package com.posbarlacteo.PosBarLacteo.service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Estado en vivo del servidor para el panel del dueño. Lo barato (JVM, base de datos, errores) se mide en cada consulta;
 * lo externo (Flow y S3) se revisa en segundo plano cada pocos minutos para no frenar la respuesta ni gastar llamadas.
 * Cada problema trae un código estable, que la app usa para avisar una sola vez y detectar cuándo se resuelve.
 */
@Service
public class SaludServidorService {

    // Umbrales
    private static final long DB_LENTA_MS = 1500;
    private static final int MEMORIA_ALTA_PCT = 85;
    private static final int CPU_ALTA_PCT = 90;
    private static final int ERRORES_PICO_5MIN = 5;
    private static final long REVISION_EXTERNOS_MS = 5 * 60_000L;
    private static final long HORAS_RESPALDO_ATRASADO = 36;

    @PersistenceContext
    private EntityManager em;

    private final DataSource dataSource;
    private final ErroresRecientes errores;
    private final ComprobanteService s3;
    private final MailService mail;
    private final FlowClient flow;
    private final SuscripcionService suscripcion;

    // Resultado de la última revisión de servicios externos (se actualiza en segundo plano)
    private volatile Externos externos = new Externos(null, false, null, 0, null, false, null, null, null);
    private final AtomicBoolean revisando = new AtomicBoolean(false);

    private record Externos(Instant revisadoEn, boolean flowOk, String flowError, long flowMs,
                            Instant s3RevisadoEn, boolean s3Ok, String s3Error, Instant ultimoRespaldo, String respaldoNota) { }

    public SaludServidorService(DataSource dataSource, ErroresRecientes errores, ComprobanteService s3, MailService mail,
                                FlowClient flow, SuscripcionService suscripcion) {
        this.dataSource = dataSource;
        this.errores = errores;
        this.s3 = s3;
        this.mail = mail;
        this.flow = flow;
        this.suscripcion = suscripcion;
    }

    public Map<String, Object> estado() {
        refrescarExternosSiHaceFalta();
        List<Map<String, String>> problemas = new ArrayList<>();

        // ---- Servidor (JVM)
        Runtime rt = Runtime.getRuntime();
        long usada = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = Math.max(1, rt.maxMemory() / (1024 * 1024));
        int memPct = (int) Math.round(usada * 100.0 / max);
        Integer cpuPct = cpuProceso();
        Map<String, Object> srv = new LinkedHashMap<>();
        srv.put("encendidoDesde", Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()).toString());
        srv.put("uptimeSeg", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        srv.put("memoriaUsadaMb", usada);
        srv.put("memoriaMaxMb", max);
        srv.put("memoriaPct", memPct);
        srv.put("cpuPct", cpuPct);
        srv.put("hilos", ManagementFactory.getThreadMXBean().getThreadCount());
        if (memPct >= MEMORIA_ALTA_PCT) {
            problema(problemas, "MEMORIA_ALTA", "AVISO", "Memoria del servidor al " + memPct + "% (" + usada + "/" + max + " MB).");
        }
        if (cpuPct != null && cpuPct >= CPU_ALTA_PCT) {
            problema(problemas, "CPU_ALTA", "AVISO", "CPU del servidor al " + cpuPct + "%.");
        }

        // ---- Base de datos
        Map<String, Object> bd = new LinkedHashMap<>();
        long t0 = System.nanoTime();
        boolean bdOk = true;
        try {
            em.createNativeQuery("select 1").getSingleResult();
        } catch (RuntimeException ex) {
            bdOk = false;
            bd.put("error", recortar(ex.getMessage()));
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        bd.put("ok", bdOk);
        bd.put("ms", ms);
        bd.put("pool", pool());
        if (!bdOk) {
            problema(problemas, "BD_CAIDA", "CRITICO", "La base de datos no responde.");
        } else if (ms >= DB_LENTA_MS) {
            problema(problemas, "BD_LENTA", "AVISO", "La base de datos responde lento (" + ms + " ms).");
        }
        Object pool = bd.get("pool");
        if (pool instanceof Map<?, ?> pm && pm.get("esperando") instanceof Number n && n.intValue() > 0) {
            problema(problemas, "POOL_SATURADO", "AVISO", n.intValue() + " consulta(s) esperando una conexión a la base de datos.");
        }

        // ---- Errores recientes
        long e5 = errores.ultimosMinutos("ERROR", 5);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("errores5min", e5);
        err.put("avisos5min", errores.ultimosMinutos("WARN", 5));
        err.put("errores24h", errores.ultimas24h("ERROR"));
        if (e5 >= ERRORES_PICO_5MIN) {
            problema(problemas, "ERRORES_PICO", "AVISO", e5 + " errores en los últimos 5 minutos. Revisa la pestaña Errores.");
        }

        // ---- Servicios externos (último chequeo en segundo plano)
        Externos x = externos;
        Map<String, Object> fl = new LinkedHashMap<>();
        fl.put("revisadoEn", x.revisadoEn() == null ? null : x.revisadoEn().toString());
        fl.put("ok", x.revisadoEn() == null ? null : x.flowOk());
        fl.put("ms", x.flowMs());
        fl.put("error", x.flowError());
        if (x.revisadoEn() != null && !x.flowOk()) {
            problema(problemas, "FLOW_FALLA", "CRITICO", "No se pudo consultar Flow: " + x.flowError() + " Los cobros y altas pueden fallar.");
        }

        Map<String, Object> s3m = new LinkedHashMap<>();
        s3m.put("configurado", s3.habilitado());
        s3m.put("revisadoEn", x.s3RevisadoEn() == null ? null : x.s3RevisadoEn().toString());
        s3m.put("ok", x.s3RevisadoEn() == null ? null : x.s3Ok());
        s3m.put("error", x.s3Error());
        Long horasResp = x.ultimoRespaldo() == null ? null : Duration.between(x.ultimoRespaldo(), Instant.now()).toHours();
        s3m.put("horasDesdeRespaldo", horasResp);
        if (s3.habilitado() && x.s3RevisadoEn() != null) {
            if (!x.s3Ok()) {
                problema(problemas, "S3_FALLA", "AVISO", "No se pudo acceder al almacenamiento S3: " + x.s3Error());
            } else if (horasResp == null) {
                problema(problemas, "RESPALDO_ATRASADO", "CRITICO", "No hay respaldos de la base de datos en S3.");
            } else if (horasResp > HORAS_RESPALDO_ATRASADO) {
                problema(problemas, "RESPALDO_ATRASADO", "CRITICO", "El último respaldo tiene " + horasResp + " horas. Revisa GitHub Actions.");
            }
        }

        Map<String, Object> co = new LinkedHashMap<>();
        co.put("configurado", mail.habilitado());
        if (!mail.habilitado()) {
            problema(problemas, "CORREO_SIN_CONFIGURAR", "AVISO", "El envío de correos no está configurado (cierres de caja, avisos de prueba).");
        }

        String nivel = problemas.stream().anyMatch(p -> "CRITICO".equals(p.get("nivel"))) ? "CRITICO"
                : problemas.isEmpty() ? "OK" : "AVISO";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ahora", Instant.now().toString());
        out.put("estado", nivel);
        out.put("problemas", problemas);
        out.put("servidor", srv);
        out.put("baseDatos", bd);
        out.put("errores", err);
        out.put("flow", fl);
        out.put("s3", s3m);
        out.put("correo", co);
        return out;
    }

    // ------------------------------------------------------------------------------------------

    private void refrescarExternosSiHaceFalta() {
        Externos x = externos;
        boolean vencido = x.revisadoEn() == null || Duration.between(x.revisadoEn(), Instant.now()).toMillis() > REVISION_EXTERNOS_MS;
        if (vencido && revisando.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::revisarExternos).whenComplete((v, t) -> revisando.set(false));
        }
    }

    private void revisarExternos() {
        Instant ahora = Instant.now();
        boolean flowOk = true;
        String flowErr = null;
        long flowMs = 0;
        long t0 = System.nanoTime();
        try {
            flow.get("/plans/get", Map.of("planId", suscripcion.planId()));
            flowMs = (System.nanoTime() - t0) / 1_000_000;
        } catch (RuntimeException ex) {
            flowOk = false;
            flowErr = recortar(ex.getMessage());
            flowMs = (System.nanoTime() - t0) / 1_000_000;
        }

        boolean s3Ok = true;
        String s3Err = null;
        Instant ultimo = null;
        Instant s3Cuando = null;
        if (s3.habilitado()) {
            s3Cuando = Instant.now();
            try {
                List<ComprobanteService.Objeto> o = s3.listar("respaldos-bd/", 1);
                ultimo = o.isEmpty() ? null : o.get(0).fecha();
            } catch (RuntimeException ex) {
                s3Ok = false;
                s3Err = recortar(ex.getMessage());
            }
        }
        externos = new Externos(ahora, flowOk, flowErr, flowMs, s3Cuando, s3Ok, s3Err, ultimo, null);
    }

    private Map<String, Object> pool() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            HikariDataSource h = dataSource.unwrap(HikariDataSource.class);
            HikariPoolMXBean mx = h.getHikariPoolMXBean();
            if (mx != null) {
                m.put("activas", mx.getActiveConnections());
                m.put("inactivas", mx.getIdleConnections());
                m.put("total", mx.getTotalConnections());
                m.put("esperando", mx.getThreadsAwaitingConnection());
            }
        } catch (Exception ignorado) {
            // sin datos del pool: no es un problema por sí mismo
        }
        return m;
    }

    private static Integer cpuProceso() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                double c = os.getProcessCpuLoad();
                return c < 0 || Double.isNaN(c) ? null : (int) Math.round(c * 100);
            }
        } catch (RuntimeException | LinkageError ignorado) {
            // no disponible en esta JVM
        }
        return null;
    }

    private static void problema(List<Map<String, String>> lista, String codigo, String nivel, String mensaje) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("codigo", codigo);
        p.put("nivel", nivel);
        p.put("mensaje", mensaje);
        lista.add(p);
    }

    private static String recortar(String s) {
        if (s == null) {
            return "sin detalle";
        }
        String limpio = s.replaceAll("\\s+", " ").trim();
        return limpio.length() > 160 ? limpio.substring(0, 160) + "…" : limpio;
    }
}
