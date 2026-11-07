package com.posbarlacteo.PosBarLacteo.service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.AuditoriaEvento;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.AuditoriaEventoRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Datos y acciones del panel del dueño de KIPIPOS (app de escritorio). Solo se usa desde /api/owner/**.
 * Nunca devuelve contraseñas, API Keys ni identificadores internos de Flow.
 */
@Service
public class OwnerPanelService {

    private static final String CARPETA_RESPALDOS = "respaldos-bd/";
    private static final long HORAS_ALERTA_RESPALDO = 36;

    @PersistenceContext
    private EntityManager em;

    private final EmpresaRepository empresaRepository;
    private final SuscripcionService suscripcionService;
    private final AuditoriaEventoRepository auditoriaRepo;
    private final ComprobanteService s3;
    private final MailService mail;
    private final ErroresRecientes errores;

    public OwnerPanelService(EmpresaRepository empresaRepository, SuscripcionService suscripcionService,
                             AuditoriaEventoRepository auditoriaRepo, ComprobanteService s3, MailService mail,
                             ErroresRecientes errores) {
        this.empresaRepository = empresaRepository;
        this.suscripcionService = suscripcionService;
        this.auditoriaRepo = auditoriaRepo;
        this.s3 = s3;
        this.mail = mail;
        this.errores = errores;
    }

    // ------------------------------------------------------------------------------------------
    // Empresas
    // ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> empresas() {
        LocalDateTime hace30 = LocalDate.now().minusDays(30).atStartOfDay();
        Map<Long, Object[]> ventas30 = porEmpresa(
                "select v.empresa.id, count(v), coalesce(sum(v.total), 0.0) from Venta v "
                        + "where v.fechaHora >= :d and (v.estado is null or v.estado <> 'ANULADA') group by v.empresa.id", hace30);
        Map<Long, Object[]> ultimaVenta = porEmpresa(
                "select v.empresa.id, max(v.fechaHora) from Venta v group by v.empresa.id", null);
        Map<Long, Object[]> usuarios = porEmpresa(
                "select u.empresa.id, count(u) from Usuario u where u.activo = true group by u.empresa.id", null);
        Map<Long, Object[]> productos = porEmpresa(
                "select p.empresa.id, count(p) from Producto p where (p.activo is null or p.activo = true) group by p.empresa.id", null);
        Map<Long, Object[]> correos = porEmpresa(
                "select u.empresa.id, min(u.correo) from Usuario u where lower(u.rol) in ('admin','administrador') "
                        + "and u.correo is not null and u.correo not like '%.local' group by u.empresa.id", null);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Empresa e : empresaRepository.findAll()) {
            Map<String, Object> m = baseEmpresa(e);
            Object[] v = ventas30.get(e.getId());
            m.put("ventas30dCantidad", v == null ? 0L : v[1]);
            m.put("ventas30dMonto", v == null ? 0.0 : v[2]);
            Object[] uv = ultimaVenta.get(e.getId());
            LocalDateTime ult = uv == null ? null : (LocalDateTime) uv[1];
            m.put("ultimaVenta", ult == null ? null : ult.toString());
            m.put("diasSinVender", ult == null ? null : Duration.between(ult, LocalDateTime.now()).toDays());
            Object[] us = usuarios.get(e.getId());
            m.put("usuariosActivos", us == null ? 0L : us[1]);
            Object[] pr = productos.get(e.getId());
            m.put("productos", pr == null ? 0L : pr[1]);
            Object[] co = correos.get(e.getId());
            m.put("correoAdmin", co == null ? null : co[1]);
            out.add(m);
        }
        out.sort((a, b) -> Long.compare((Long) b.get("id"), (Long) a.get("id")));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(Long id) {
        Empresa e = empresaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Map<String, Object> m = baseEmpresa(e);
        // Indicadores de uso (ventas 30 días, usuarios, productos, correo del admin)
        empresas().stream().filter(x -> id.equals(x.get("id"))).findFirst().ifPresent(x -> x.forEach(m::putIfAbsent));
        m.put("giro", e.getGiro());
        m.put("direccion", e.getDireccion());
        m.put("comuna", e.getComuna());
        m.put("terminosVersion", e.getTerminosVersion());
        m.put("terminosAceptadosEn", str(e.getTerminosAceptadosEn()));
        m.put("bajaSolicitadaEn", str(e.getBajaSolicitadaEn()));

        List<Map<String, Object>> us = new ArrayList<>();
        for (Object[] r : consulta("select u.usuario, u.rol, u.activo, u.correo from Usuario u where u.empresa.id = :id order by u.id", id)) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("usuario", r[0]);
            u.put("rol", r[1]);
            u.put("activo", r[2]);
            u.put("correo", r[3]);
            us.add(u);
        }
        m.put("usuarios", us);

        Object[] c = consulta("select count(v), coalesce(sum(v.total), 0.0) from Venta v where v.empresa.id = :id "
                + "and (v.estado is null or v.estado <> 'ANULADA')", id).get(0);
        m.put("ventasTotalesCantidad", c[0]);
        m.put("ventasTotalesMonto", c[1]);

        List<Map<String, Object>> aud = new ArrayList<>();
        auditoriaRepo.findTop150ByEmpresaIdOrderByIdDesc(id).stream().limit(30).forEach(a -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("fecha", str(a.getFecha()));
            x.put("usuario", a.getUsuario());
            x.put("accion", a.getAccion());
            x.put("detalle", a.getDetalle());
            aud.add(x);
        });
        m.put("auditoria", aud);
        return m;
    }

    private Map<String, Object> baseEmpresa(Empresa e) {
        SuscripcionService.Vista v = suscripcionService.calcular(e);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("rut", e.getRutEmpresa());
        m.put("razonSocial", e.getRazonSocial());
        m.put("fechaRegistro", str(e.getFechaRegistro()));
        m.put("estado", e.getEstado());
        m.put("estadoEfectivo", v.estado());
        m.put("modo", v.modo().name());
        m.put("pruebaHasta", str(e.getPruebaHasta()));
        m.put("moraDesde", str(e.getMoraDesde()));
        m.put("accesoHasta", str(e.getAccesoHasta()));
        m.put("cancelada", Boolean.TRUE.equals(e.getSuscripcionCancelada()));
        m.put("flowStatus", e.getFlowStatus());
        m.put("flowMorosidad", e.getFlowMorosidad());
        m.put("flowUltimaSync", str(e.getFlowUltimaSync()));
        m.put("conSuscripcionFlow", e.getFlowSubscriptionId() != null && !e.getFlowSubscriptionId().isBlank());
        m.put("bloqueoManual", e.isBloqueoManual());
        m.put("bloqueoMotivo", e.getBloqueoMotivo());
        m.put("bloqueoDesde", str(e.getBloqueoDesde()));
        return m;
    }

    // ------------------------------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> suspender(Long id, String motivo) {
        String mot = motivo == null ? "" : motivo.trim();
        if (mot.length() < 3) {
            throw new NegocioException("Indica el motivo de la suspensión.");
        }
        Empresa e = empresaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        e.setBloqueoManual(true, mot.length() > 300 ? mot.substring(0, 300) : mot);
        empresaRepository.save(e);
        suscripcionService.invalidar(id);
        auditar(id, "OWNER_SUSPENDER", mot);
        return baseEmpresa(e);
    }

    @Transactional
    public Map<String, Object> reactivar(Long id) {
        Empresa e = empresaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        if (!e.isBloqueoManual()) {
            throw new NegocioException("La empresa no está suspendida manualmente.");
        }
        e.setBloqueoManual(false, null);
        empresaRepository.save(e);
        suscripcionService.invalidar(id);
        auditar(id, "OWNER_REACTIVAR", "Bloqueo manual retirado");
        return baseEmpresa(e);
    }

    /** Consulta Flow y actualiza el estado de la empresa ahora mismo (sin esperar la conciliación de cada 6 h). */
    public Map<String, Object> sincronizar(Long id) {
        suscripcionService.sincronizar(id);
        suscripcionService.invalidar(id);
        auditar(id, "OWNER_SYNC_FLOW", "Sincronización manual con Flow");
        return empresaRepository.findById(id).map(this::baseEmpresa)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    private void auditar(Long empresaId, String accion, String detalle) {
        try {
            AuditoriaEvento a = new AuditoriaEvento();
            a.setEmpresaId(empresaId);
            a.setUsuario("KIPIPOS (dueño)");
            a.setAccion(accion);
            a.setDetalle(detalle != null && detalle.length() > 500 ? detalle.substring(0, 500) : detalle);
            auditoriaRepo.save(a);
        } catch (RuntimeException ignorado) {
            // la auditoría nunca debe impedir la acción
        }
    }

    // ------------------------------------------------------------------------------------------
    // Resumen, respaldos, errores
    // ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> resumen() {
        Map<String, Long> porEstado = new LinkedHashMap<>();
        long bloqueadas = 0;
        long sinActividad = 0;
        List<Map<String, Object>> lista = empresas();
        for (Map<String, Object> e : lista) {
            porEstado.merge((String) e.get("estadoEfectivo"), 1L, Long::sum);
            if (Boolean.TRUE.equals(e.get("bloqueoManual"))) {
                bloqueadas++;
            }
            Object dias = e.get("diasSinVender");
            String est = (String) e.get("estadoEfectivo");
            boolean vigente = "ACTIVA".equals(est) || "EN_MORA".equals(est) || "PRUEBA".equals(est);
            if (vigente && (dias == null || ((Long) dias) >= 14)) {
                sinActividad++;
            }
        }
        LocalDateTime hoy = LocalDate.now().atStartOfDay();
        Object[] h = consultaUno("select count(v), coalesce(sum(v.total), 0.0) from Venta v where v.fechaHora >= :d "
                + "and (v.estado is null or v.estado <> 'ANULADA')", hoy);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("empresasTotal", lista.size());
        m.put("porEstado", porEstado);
        m.put("bloqueadasManual", bloqueadas);
        m.put("vigentesSinVentasEn14Dias", sinActividad);
        m.put("ventasHoyCantidad", h[0]);
        m.put("ventasHoyMonto", h[1]);
        m.put("errores24h", errores.ultimas24h("ERROR"));
        m.put("avisos24h", errores.ultimas24h("WARN"));
        m.put("respaldo", respaldos(1));
        m.put("servidor", servidor());
        return m;
    }

    public Map<String, Object> respaldos(int max) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!s3.habilitado()) {
            m.put("habilitado", false);
            m.put("alerta", true);
            m.put("mensaje", "El almacenamiento S3 no está configurado en el servidor.");
            m.put("archivos", List.of());
            return m;
        }
        m.put("habilitado", true);
        try {
            List<ComprobanteService.Objeto> objs = s3.listar(CARPETA_RESPALDOS, Math.max(1, max));
            List<Map<String, Object>> archivos = new ArrayList<>();
            for (ComprobanteService.Objeto o : objs) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("nombre", o.key().substring(o.key().lastIndexOf('/') + 1));
                a.put("bytes", o.bytes());
                a.put("fecha", o.fecha().toString());
                archivos.add(a);
            }
            m.put("archivos", archivos);
            if (objs.isEmpty()) {
                m.put("alerta", true);
                m.put("mensaje", "No hay respaldos en " + CARPETA_RESPALDOS);
            } else {
                long horas = Duration.between(objs.get(0).fecha(), Instant.now()).toHours();
                m.put("horasDesdeUltimo", horas);
                boolean alerta = horas > HORAS_ALERTA_RESPALDO;
                m.put("alerta", alerta);
                m.put("mensaje", alerta ? "El último respaldo tiene más de " + HORAS_ALERTA_RESPALDO + " horas. Revisa GitHub Actions." : "Respaldos al día.");
            }
        } catch (RuntimeException ex) {
            m.put("alerta", true);
            m.put("archivos", List.of());
            m.put("mensaje", "No se pudo listar los respaldos (¿falta el permiso s3:ListBucket?): " + ex.getMessage());
        }
        return m;
    }

    public List<ErroresRecientes.Entrada> errores(String nivel, int max) {
        return errores.recientes(nivel, Math.min(Math.max(max, 1), 300));
    }

    private Map<String, Object> servidor() {
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("encendidoDesde", Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()).toString());
        m.put("memoriaUsadaMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        m.put("memoriaMaxMb", rt.maxMemory() / (1024 * 1024));
        m.put("s3Configurado", s3.habilitado());
        m.put("correoConfigurado", mail.habilitado());
        return m;
    }

    // ------------------------------------------------------------------------------------------
    // Utilidades de consulta
    // ------------------------------------------------------------------------------------------

    private Map<Long, Object[]> porEmpresa(String jpql, LocalDateTime desde) {
        var q = em.createQuery(jpql, Object[].class);
        if (desde != null) {
            q.setParameter("d", desde);
        }
        Map<Long, Object[]> m = new HashMap<>();
        for (Object[] r : q.getResultList()) {
            m.put(((Number) r[0]).longValue(), r);
        }
        return m;
    }

    private List<Object[]> consulta(String jpql, Long id) {
        return em.createQuery(jpql, Object[].class).setParameter("id", id).getResultList();
    }

    private Object[] consultaUno(String jpql, LocalDateTime desde) {
        return em.createQuery(jpql, Object[].class).setParameter("d", desde).getSingleResult();
    }

    private static String str(LocalDateTime d) {
        return d == null ? null : d.toString();
    }
}
