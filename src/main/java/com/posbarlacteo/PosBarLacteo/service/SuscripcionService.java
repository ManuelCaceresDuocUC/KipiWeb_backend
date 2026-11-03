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

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.exception.ServicioExternoException;
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
 *  - Prueba gratuita sin tarjeta (PRUEBA): uso normal hasta prueba_hasta. Vencida, la cuenta queda en solo lectura
 *    (PRUEBA_VENCIDA, que no se guarda: se calcula por fecha) hasta que el cliente inscriba su tarjeta desde el portal.
 *    La tarjeta se inscribe en Flow y recién ahí se crea la suscripción (el plan de Flow NO debe tener días de prueba).
 *  - Baja (BAJA): el cliente cerró su cuenta; sin acceso. Los datos se borran pasado el plazo (ver BajaCuentaService).
 */
@Service
public class SuscripcionService {

    private static final Logger log = LoggerFactory.getLogger(SuscripcionService.class);

    public static final String PRUEBA = "PRUEBA";
    /** Estado calculado (no se guarda): prueba terminada y sin tarjeta inscrita. */
    public static final String PRUEBA_VENCIDA = "PRUEBA_VENCIDA";
    public static final String BAJA = "BAJA";

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
                        LocalDateTime accesoHasta, boolean cancelada, String mensaje, LocalDateTime pruebaHasta) {
        public Vista(String estado, Modo modo, LocalDateTime moraDesde, LocalDateTime limiteGracia,
                     LocalDateTime accesoHasta, boolean cancelada, String mensaje) {
            this(estado, modo, moraDesde, limiteGracia, accesoHasta, cancelada, mensaje, null);
        }
    }

    private record Entrada(Vista vista, long venceMs) { }

    private static final long TTL_MS = 60_000;
    private final ConcurrentHashMap<Long, Entrada> cache = new ConcurrentHashMap<>();

    private final EmpresaRepository empresaRepository;
    private final FlowClient flow;
    private final long graciaDias;
    private final long pruebaDias;
    private final String planId;

    public SuscripcionService(EmpresaRepository empresaRepository, FlowClient flow,
                              @Value("${suscripcion.gracia-dias:7}") long graciaDias,
                              @Value("${suscripcion.prueba-dias:30}") long pruebaDias,
                              @Value("${flow.plan-id:KIPI}") String planId) {
        this.empresaRepository = empresaRepository;
        this.flow = flow;
        this.graciaDias = graciaDias;
        this.pruebaDias = pruebaDias;
        this.planId = planId;
    }

    /** Plan de Flow al que se suscribe (variable FLOW_PLAN_ID; debe ser un plan SIN días de prueba). */
    public String planId() {
        return planId;
    }

    /** Fin de la prueba gratuita para una empresa que se registra ahora. */
    public LocalDateTime finDePruebaDesdeAhora() {
        return LocalDateTime.now().plusDays(pruebaDias);
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
        if (BAJA.equals(estado)) {
            return new Vista(BAJA, Modo.DENEGADO, null, null, null, false,
                    "Tu cuenta fue dada de baja. Si fue un error, escríbenos antes de que se eliminen tus datos.");
        }
        if (PRUEBA.equals(estado)) {
            LocalDateTime hasta = e.getPruebaHasta();
            if (hasta == null || ahora.isAfter(hasta)) {
                return new Vista(PRUEBA_VENCIDA, Modo.SOLO_LECTURA, null, null, null, false,
                        "Tu prueba gratuita terminó. Puedes consultar tu información, pero para seguir vendiendo debes activar tu suscripción inscribiendo tu tarjeta. "
                                + "Si prefieres no continuar, puedes dar de baja tu cuenta desde Administración.", hasta);
            }
            long dias = diasRestantes(ahora, hasta);
            // Con más de 10 días no se muestra aviso (no estorbar); el portal siempre permite activar.
            String msg = dias > 10 ? null
                    : "Te " + (dias == 1 ? "queda 1 día" : "quedan " + dias + " días") + " de prueba gratuita (hasta el " + fecha(hasta)
                    + "). Activa tu suscripción para no perder el acceso.";
            return new Vista(PRUEBA, Modo.PLENO, null, null, null, false, msg, hasta);
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

    /** Días enteros que faltan (redondeando hacia arriba): a las 23 h de plazo aún "queda 1 día". Nunca negativo. */
    public static long diasRestantes(LocalDateTime desde, LocalDateTime hasta) {
        long horas = java.time.Duration.between(desde, hasta).toHours();
        return horas <= 0 ? 0 : (horas + 23) / 24;
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
            // Prueba/registro con la tarjeta ya inscrita pero sin suscripción guardada: se completa ahora.
            if (e.getFlowCustomerId() != null && !e.getFlowCustomerId().isBlank()
                    && (PRUEBA.equals(e.getEstado()) || PENDIENTE.equals(e.getEstado()))) {
                try {
                    rescatarEmpresa(e);
                } catch (RuntimeException ex) {
                    log.warn("No se pudo completar la suscripción de la empresa {}: {}", empresaId, ex.getMessage());
                }
            }
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
        // PENDIENTE: registros antiguos que pagaban en el registro. PRUEBA: clientes que inscribieron su tarjeta desde el portal.
        for (String estado : List.of(PENDIENTE, PRUEBA)) {
            for (Empresa e : empresaRepository.findByEstadoAndFlowCustomerIdIsNotNullAndFlowSubscriptionIdIsNull(estado)) {
                LocalDateTime iniciado = PRUEBA.equals(estado) ? e.getFlowInscripcionEn() : e.getFechaRegistro();
                if (iniciado == null || iniciado.isAfter(antesDe)) {
                    continue; // muy reciente (puede estar pagando en este momento) o nunca inició la inscripción
                }
                try {
                    if (rescatarEmpresa(e)) {
                        rescatadas.add(e);
                    }
                } catch (RuntimeException ex) {
                    log.warn("No se pudo revisar el registro incompleto de la empresa {}: {}", e.getId(), ex.getMessage());
                }
            }
        }
        return rescatadas;
    }

    /** Adopta la suscripción vigente de Flow o la crea si hay tarjeta inscrita; activa la empresa. true si quedó activa. */
    private boolean rescatarEmpresa(Empresa e) {
        String subscriptionId = suscripcionVigenteEnFlow(e.getFlowCustomerId());
        String origen = "tenía suscripción vigente en Flow sin registrar";
        if (subscriptionId == null && tarjetaInscrita(e.getFlowCustomerId())) {
            subscriptionId = crearSuscripcion(e.getFlowCustomerId());
            origen = "tenía la tarjeta inscrita pero sin suscripción: se creó";
        }
        if (subscriptionId == null) {
            return false;
        }
        e.setFlowSubscriptionId(subscriptionId);
        e.setEstado(ACTIVA);
        e.setActivo(true);
        empresaRepository.save(e);
        invalidar(e.getId());
        log.warn("Empresa {} rescatada: {}.", e.getId(), origen);
        return true;
    }

    /**
     * Inicia la inscripción de la tarjeta desde el portal (cliente en PRUEBA o con la prueba vencida). Crea el cliente
     * en Flow si aún no existe y devuelve la URL de Flow donde se inscribe la tarjeta. Al volver, el endpoint
     * /api/auth/registro-exitoso crea la suscripción y deja la cuenta ACTIVA.
     */
    public String iniciarInscripcionTarjeta(Long empresaId, String correo, String urlRetorno) {
        Empresa e = empresaRepository.findById(empresaId).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        if (!PRUEBA.equals(e.getEstado())) {
            throw new NegocioException("Tu cuenta no está en período de prueba, por lo que no hay nada que activar.");
        }
        if (e.getFlowSubscriptionId() != null && !e.getFlowSubscriptionId().isBlank()) {
            throw new NegocioException("Tu suscripción ya está creada. Si no ves el cambio, pulsa \"Ya pagué\".");
        }
        String customerId = e.getFlowCustomerId();
        if (customerId == null || customerId.isBlank()) {
            Map<String, Object> creado = flow.post("/customer/create",
                    mapa("name", e.getRazonSocial(), "email", MailService.validarDestino(correo), "externalId", e.getRutEmpresa()));
            if (creado == null || creado.get("customerId") == null) {
                throw new ServicioExternoException("No se pudo crear tu cuenta de pagos. Inténtalo más tarde.");
            }
            customerId = String.valueOf(creado.get("customerId"));
            e.setFlowCustomerId(customerId);
            empresaRepository.save(e);
        }
        Map<String, Object> reg = flow.post("/customer/register", mapa("customerId", customerId, "url_return", urlRetorno));
        if (reg == null || reg.get("url") == null || reg.get("token") == null) {
            throw new ServicioExternoException("No se pudo generar el enlace de pago. Inténtalo más tarde.");
        }
        e.setFlowInscripcionEn(LocalDateTime.now());
        empresaRepository.save(e);
        invalidar(empresaId);
        return reg.get("url") + "?token=" + reg.get("token");
    }

    /** Cancela de inmediato la suscripción de Flow (al dar de baja la cuenta). Lanza excepción si Flow no lo confirma. */
    public void cancelarSuscripcionFlow(Empresa e) {
        if (e.getFlowSubscriptionId() == null || e.getFlowSubscriptionId().isBlank()) {
            return;
        }
        Map<String, Object> r = flow.post("/subscription/cancel", mapa("subscriptionId", e.getFlowSubscriptionId(), "at_period_end", "0"));
        if (r == null) {
            throw new ServicioExternoException("Flow no confirmó la cancelación de tu suscripción.");
        }
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
        Map<String, Object> s = flow.post("/subscription/create", mapa("customerId", customerId, "planId", planId));
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
