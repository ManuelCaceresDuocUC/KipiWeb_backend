package com.posbarlacteo.PosBarLacteo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;

/**
 * Estado de la suscripción de cada empresa y cuánto puede hacer en KIPIPOS.
 *
 * Fuente de verdad: Flow. Se concilia periódicamente (ver ConciliacionFlowJob) y a pedido del cliente.
 * Regla de oro: si Flow no responde, NO se cambia ningún estado (una caída de Flow nunca suspende a un cliente).
 *
 * Políticas acordadas:
 *  - Factura vencida (morose=1): EN_MORA con N días de gracia (uso normal + aviso); pasada la gracia, SUSPENDIDA.
 *  - Suspendida: solo lectura (puede consultar y pagar, no puede vender ni modificar).
 *  - Cancelada en Flow: acceso hasta el fin del período ya pagado.
 */
@Service
public class SuscripcionService {

    private static final Logger log = LoggerFactory.getLogger(SuscripcionService.class);

    /** Plan único de Flow. */
    public static final String PLAN_ID = "KIPI";

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ACTIVA = "ACTIVA";
    public static final String EN_MORA = "EN_MORA";
    public static final String SUSPENDIDA = "SUSPENDIDA";

    public enum Modo {
        /** Uso normal. */
        PLENO,
        /** Puede consultar pero no escribir. */
        SOLO_LECTURA,
        /** Sin acceso (registro sin pagar). */
        DENEGADO
    }

    /** Vista de una empresa para el frontend y el filtro. */
    public record Vista(String estado, Modo modo, LocalDateTime moraDesde, LocalDateTime limiteGracia,
                        LocalDateTime accesoHasta, boolean cancelada, String mensaje) {
    }

    private record Entrada(Vista vista, long venceMs) { }

    private static final long TTL_MS = 60_000;
    private final ConcurrentHashMap<Long, Entrada> cache = new ConcurrentHashMap<>();

    private final EmpresaRepository empresaRepository;
    private final FlowClient flow;
    private final long graciaDias;

    public SuscripcionService(EmpresaRepository empresaRepository, FlowClient flow,
                              @Value("${suscripcion.gracia-dias:7}") long graciaDias) {
        this.empresaRepository = empresaRepository;
        this.flow = flow;
        this.graciaDias = graciaDias;
    }

    // ------------------------------------------------------------------------------------------
    // Lectura (usada por el filtro en CADA petición: con caché de 60 s)
    // ------------------------------------------------------------------------------------------

    public Vista vista(Long empresaId) {
        long ahora = System.currentTimeMillis();
        Entrada e = cache.get(empresaId);
        if (e != null && e.venceMs() > ahora) {
            return e.vista();
        }
        Empresa empresa = empresaRepository.findById(empresaId).orElse(null);
        Vista v = empresa == null ? new Vista("DESCONOCIDA", Modo.DENEGADO, null, null, null, false, "Empresa no encontrada.") : calcular(empresa);
        cache.put(empresaId, new Entrada(v, ahora + TTL_MS));
        return v;
    }

    public void invalidar(Long empresaId) {
        cache.remove(empresaId);
    }

    /** Calcula el modo en este instante a partir de lo guardado (no depende de que el job haya corrido). */
    Vista calcular(Empresa e) {
        String estado = e.getEstado();
        LocalDateTime ahora = LocalDateTime.now();
        boolean cancelada = Boolean.TRUE.equals(e.getSuscripcionCancelada());
        LocalDateTime limiteGracia = e.getMoraDesde() == null ? null : e.getMoraDesde().plusDays(graciaDias);

        if (PENDIENTE.equals(estado)) {
            return new Vista(PENDIENTE, Modo.DENEGADO, null, null, null, false, "Falta completar el pago de la suscripción.");
        }
        if (SUSPENDIDA.equals(estado)) {
            return new Vista(SUSPENDIDA, Modo.SOLO_LECTURA, e.getMoraDesde(), limiteGracia, e.getAccesoHasta(), cancelada,
                    "Tu cuenta está suspendida por falta de pago: puedes consultar tu información, pero no registrar ventas ni cambios. Regulariza el pago para reactivarla.");
        }
        // Cancelada y ya terminó el período pagado
        if (e.getAccesoHasta() != null && ahora.isAfter(e.getAccesoHasta())) {
            return new Vista(SUSPENDIDA, Modo.SOLO_LECTURA, e.getMoraDesde(), limiteGracia, e.getAccesoHasta(), cancelada,
                    "Tu suscripción terminó el " + fecha(e.getAccesoHasta()) + ". Puedes consultar tu información, pero no registrar ventas. Contáctanos para reactivarla.");
        }
        // Factura vencida
        if (e.getMoraDesde() != null) {
            if (ahora.isAfter(limiteGracia)) {
                return new Vista(SUSPENDIDA, Modo.SOLO_LECTURA, e.getMoraDesde(), limiteGracia, e.getAccesoHasta(), cancelada,
                        "Tu cuenta está suspendida por falta de pago. Regulariza el pago para volver a registrar ventas.");
            }
            return new Vista(EN_MORA, Modo.PLENO, e.getMoraDesde(), limiteGracia, e.getAccesoHasta(), cancelada,
                    "No pudimos cobrar tu suscripción. Regulariza el pago antes del " + fecha(limiteGracia) + " para no perder el acceso.");
        }
        if (cancelada && e.getAccesoHasta() != null) {
            return new Vista(ACTIVA, Modo.PLENO, null, null, e.getAccesoHasta(), true,
                    "Tu suscripción fue cancelada. Podrás usar KIPIPOS hasta el " + fecha(e.getAccesoHasta()) + ".");
        }
        // ACTIVA, o empresas antiguas sin estado (nunca pasaron por Flow): uso normal
        return new Vista(estado == null ? ACTIVA : estado, Modo.PLENO, null, null, null, false, null);
    }

    private static String fecha(LocalDateTime d) {
        return d.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    // ------------------------------------------------------------------------------------------
    // Conciliación con Flow
    // ------------------------------------------------------------------------------------------

    /** Resultado de consultar una empresa: lo que Flow dice y el estado al que llevaría. */
    public record Decision(Empresa empresa, Map<String, Object> flow, boolean pasaASuspendida) { }

    /** Consulta Flow y devuelve la decisión SIN guardarla. Lanza ServicioExternoException si Flow no responde. */
    public Decision consultar(Empresa e) {
        Map<String, Object> sub = flow.get("/subscription/get", mapa("subscriptionId", e.getFlowSubscriptionId()));
        if (sub == null || sub.get("status") == null) {
            throw new IllegalStateException("Respuesta de Flow sin status para la suscripción " + e.getFlowSubscriptionId());
        }
        String antes = e.getEstado();
        Empresa copia = e; // se aplica sobre la entidad; el llamador decide si persistir
        aplicar(copia, sub);
        return new Decision(copia, sub, !SUSPENDIDA.equals(antes) && SUSPENDIDA.equals(copia.getEstado()));
    }

    /** Traduce la respuesta de Flow a los campos de la empresa. */
    void aplicar(Empresa e, Map<String, Object> sub) {
        int status = entero(sub.get("status"), -1);
        int morose = entero(sub.get("morose"), 0);
        boolean cancelaAlFinal = entero(sub.get("cancel_at_period_end"), 0) == 1;
        LocalDateTime finPeriodo = fechaFlow(sub.get("period_end"));
        LocalDateTime ahora = LocalDateTime.now();

        e.setFlowStatus(status);
        e.setFlowMorosidad(morose);
        e.setFlowUltimaSync(ahora);

        if (status == 4) { // cancelada: queda el período ya pagado
            e.setSuscripcionCancelada(true);
            e.setAccesoHasta(finPeriodo != null ? finPeriodo : ahora);
            e.setMoraDesde(null);
        } else if (status == 1 || status == 2) {
            if (cancelaAlFinal) {
                e.setSuscripcionCancelada(true);
                e.setAccesoHasta(finPeriodo);
            } else {
                e.setSuscripcionCancelada(false);
                e.setAccesoHasta(null);
            }
            if (morose == 1) {
                if (e.getMoraDesde() == null) {
                    e.setMoraDesde(ahora);
                }
            } else {
                e.setMoraDesde(null);
            }
        } else {
            return; // status 0 u otro desconocido: no se toca el estado (solo queda registrado lo recibido)
        }

        e.setEstado(calcular(e).estado());
    }

    /** Concilia UNA empresa y guarda. Usado por el botón "Ya pagué". */
    public Vista sincronizar(Long empresaId) {
        Empresa e = empresaRepository.findById(empresaId).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        if (e.getFlowSubscriptionId() == null || e.getFlowSubscriptionId().isBlank()) {
            return calcular(e); // sin suscripción en Flow (cuenta manual): nada que conciliar
        }
        consultar(e);
        empresaRepository.save(e);
        invalidar(empresaId);
        return calcular(e);
    }

    /**
     * Enlace de pago de la primera factura impaga de la suscripción, o null si no se puede determinar.
     * (Basado en subscription/get → invoices[] e invoice/get → paymentLink.)
     */
    public String enlaceDePago(Long empresaId) {
        Empresa e = empresaRepository.findById(empresaId).orElse(null);
        if (e == null || e.getFlowSubscriptionId() == null) {
            return null;
        }
        try {
            Map<String, Object> sub = flow.get("/subscription/get", mapa("subscriptionId", e.getFlowSubscriptionId()));
            Object inv = sub == null ? null : sub.get("invoices");
            if (inv instanceof List<?> facturas) {
                for (Object f : facturas) {
                    if (f instanceof Map<?, ?> m && entero(m.get("status"), -1) == 0 && m.get("id") != null) {
                        Map<String, Object> factura = flow.get("/invoice/get", mapa("invoiceId", String.valueOf(m.get("id"))));
                        Object link = factura == null ? null : factura.get("paymentLink");
                        if (link != null && !String.valueOf(link).isBlank()) {
                            return String.valueOf(link);
                        }
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo obtener el enlace de pago de la empresa {}: {}", empresaId, ex.getMessage());
        }
        return null;
    }

    /**
     * Registros PENDIENTES cuyo cliente existe en Flow pero cuya suscripción no quedó guardada. Hay dos casos:
     *  1) Flow ya tiene una suscripción vigente del cliente (falló la base justo después de crearla): se adopta.
     *  2) El cliente inscribió su tarjeta pero nunca se creó la suscripción (se cortó el retorno desde Flow, p. ej.
     *     por cerrar la pestaña o un error de red): se crea ahora, porque al inscribir la tarjeta el cliente autorizó el
     *     cobro (ver Términos). Sin tarjeta inscrita no se hace nada: es un registro abandonado.
     * Si Flow no responde, no se toca nada y se reintenta en la siguiente conciliación.
     */
    public List<Empresa> rescatarRegistrosIncompletos(LocalDateTime antesDe) {
        List<Empresa> rescatadas = new ArrayList<>();
        for (Empresa e : empresaRepository.findByEstadoAndFlowCustomerIdIsNotNullAndFlowSubscriptionIdIsNull(PENDIENTE)) {
            if (e.getFechaRegistro() == null || e.getFechaRegistro().isAfter(antesDe)) {
                continue; // muy reciente: puede estar pagando en este momento
            }
            try {
                String subscriptionId = suscripcionVigenteEnFlow(e.getFlowCustomerId());
                String origen = "tenía suscripción vigente en Flow sin registrar";
                if (subscriptionId == null && tarjetaInscrita(e.getFlowCustomerId())) {
                    subscriptionId = crearSuscripcion(e.getFlowCustomerId());
                    origen = "tenía la tarjeta inscrita pero sin suscripción: se creó";
                }
                if (subscriptionId != null) {
                    e.setFlowSubscriptionId(subscriptionId);
                    e.setEstado(ACTIVA);
                    e.setActivo(true);
                    empresaRepository.save(e);
                    invalidar(e.getId());
                    rescatadas.add(e);
                    log.warn("Empresa {} rescatada: {}.", e.getId(), origen);
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo revisar el registro incompleto de la empresa {}: {}", e.getId(), ex.getMessage());
            }
        }
        return rescatadas;
    }

    /** Id de una suscripción activa (1) o en prueba (2) del cliente en Flow, o null. */
    private String suscripcionVigenteEnFlow(String customerId) {
        Map<String, Object> r = flow.get("/customer/getSubscriptions", mapa("customerId", customerId, "start", "0", "limit", "10"));
        Object data = r == null ? null : r.get("data");
        if (data instanceof List<?> lista) {
            for (Object o : lista) {
                if (o instanceof Map<?, ?> m) {
                    int st = entero(m.get("status"), -1);
                    if ((st == 1 || st == 2) && m.get("subscriptionId") != null) {
                        return String.valueOf(m.get("subscriptionId"));
                    }
                }
            }
        }
        return null;
    }

    /** true si el cliente tiene una tarjeta inscrita en Flow. */
    private boolean tarjetaInscrita(String customerId) {
        Map<String, Object> c = flow.get("/customer/get", mapa("customerId", customerId));
        if (c == null) return false;
        return noVacio(c.get("creditCardType")) || noVacio(c.get("last4CardDigits"));
    }

    /** Crea la suscripción al plan; devuelve su id solo si quedó activa o en prueba. Lanza excepción si Flow falla. */
    private String crearSuscripcion(String customerId) {
        Map<String, Object> s = flow.post("/subscription/create", mapa("customerId", customerId, "planId", PLAN_ID));
        int st = s == null ? -1 : entero(s.get("status"), -1);
        if ((st == 1 || st == 2) && s.get("subscriptionId") != null) {
            return String.valueOf(s.get("subscriptionId"));
        }
        log.warn("Flow no dejó activa la suscripción del cliente {} (status {}).", customerId, st);
        return null;
    }

    private static boolean noVacio(Object o) {
        return o != null && !String.valueOf(o).isBlank() && !"null".equalsIgnoreCase(String.valueOf(o));
    }

    // ------------------------------------------------------------------------------------------

    static Map<String, String> mapa(String... kv) {
        Map<String, String> m = new TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    static int entero(Object o, int porDefecto) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignorado) {
                return porDefecto;
            }
        }
        return porDefecto;
    }

    /** Flow entrega fechas como "2026-11-01" o "2026-11-01 10:30:00". */
    static LocalDateTime fechaFlow(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        try {
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19).replace(' ', 'T'));
            }
            if (s.length() >= 10) {
                return LocalDate.parse(s.substring(0, 10)).plusDays(1).atStartOfDay().minusSeconds(1); // hasta el final de ese día
            }
        } catch (RuntimeException ignorado) {
            return null;
        }
        return null;
    }

    /** Estados que se concilian. */
    public static final Set<String> ESTADOS_CONCILIABLES = Set.of(ACTIVA, EN_MORA, SUSPENDIDA);
}
