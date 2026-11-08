package com.posbarlacteo.PosBarLacteo.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.exception.ConflictoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/**
 * Cola de impresión por sucursal: un celular (u otro equipo sin impresora) deja el trabajo y el PC que tiene la impresora
 * y KIPIPOS abierto ("estación de impresión") lo recoge cada pocos segundos.
 *
 * Es en memoria a propósito (trabajos de vida corta, sin datos que conservar) y asume una sola instancia del backend.
 * Seguridad: solo se aceptan PDF y eventos de caja ESTRUCTURADOS; jamás HTML, porque la estación (que puede ser la sesión
 * de un administrador) lo imprimiría dentro de su navegador.
 */
@Service
public class ColaImpresionService {

    public record Trabajo(String id, String tipo, List<String> documentos, Map<String, Object> evento,
                          boolean abrirGaveta, String origen, long creadoMs) { }

    private static final long TTL_MS = 5 * 60_000L;          // un ticket de hace 5 min ya no se imprime
    private static final long LATIDO_MS = 15_000L;           // la estación cuenta como conectada si consultó hace <15 s
    private static final int MAX_COLA = 30;
    private static final int MAX_BYTES = 1_800_000;
    private static final Set<String> EVENTOS = Set.of("ingreso", "retiro", "sin_venta", "prueba");

    private final Map<String, Deque<Trabajo>> colas = new ConcurrentHashMap<>();
    private final Map<String, Long> latidos = new ConcurrentHashMap<>();
    private final Map<String, Object> monitores = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> esperando = new ConcurrentHashMap<>();

    private static String clave(AuthPrincipal p) {
        return p.empresaId() + ":" + (p.sucursalId() == null ? 0 : p.sucursalId());
    }

    public boolean estacionConectada(AuthPrincipal p) {
        String k = clave(p);
        AtomicInteger e = esperando.get(k);
        if (e != null && e.get() > 0) {
            return true; // la estación tiene una consulta abierta esperando trabajos
        }
        Long t = latidos.get(k);
        return t != null && System.currentTimeMillis() - t < LATIDO_MS;
    }

    public Trabajo encolar(AuthPrincipal p, String tipo, List<String> documentos, Map<String, Object> evento, boolean abrirGaveta) {
        if (!estacionConectada(p)) {
            throw new ConflictoException("No hay una caja de impresión conectada en esta sucursal.");
        }
        List<String> docs = List.of();
        Map<String, Object> ev = null;
        if ("PDF".equals(tipo)) {
            if (documentos == null || documentos.isEmpty() || documentos.size() > 4) {
                throw new NegocioException("Documentos de impresión no válidos.");
            }
            int total = 0;
            for (String d : documentos) {
                // Un PDF en base64 siempre empieza con "JVBER" ("%PDF-"); solo caracteres base64
                if (d == null || !d.startsWith("JVBER") || !d.matches("[A-Za-z0-9+/=\\r\\n]+")) {
                    throw new NegocioException("Solo se pueden enviar documentos PDF.");
                }
                total += d.length();
            }
            if (total > MAX_BYTES) {
                throw new NegocioException("El documento es demasiado grande para imprimirlo en la caja.");
            }
            docs = List.copyOf(documentos);
        } else if ("CAJA".equals(tipo)) {
            ev = validarEvento(evento);
        } else {
            throw new NegocioException("Tipo de trabajo de impresión no válido.");
        }

        Trabajo t = new Trabajo(UUID.randomUUID().toString(), tipo, docs, ev, abrirGaveta, p.usuario(), System.currentTimeMillis());
        Deque<Trabajo> cola = colas.computeIfAbsent(clave(p), k -> new ArrayDeque<>());
        synchronized (cola) {
            purgar(cola);
            if (cola.size() >= MAX_COLA) {
                throw new NegocioException("Hay demasiados trabajos esperando en la caja de impresión.");
            }
            cola.addLast(t);
        }
        Object mon = monitores.computeIfAbsent(clave(p), k -> new Object());
        synchronized (mon) {
            mon.notifyAll(); // despierta a la estación que está esperando
        }
        return t;
    }

    /**
     * La estación consulta: se registra el latido y se le entregan (y retiran) los trabajos vigentes.
     * Con {@code esperaMs} > 0 la consulta se queda abierta hasta que llegue un trabajo (o pase el tiempo), así el ticket
     * sale casi al instante sin consultar a cada rato.
     */
    public List<Trabajo> reclamar(AuthPrincipal p, long esperaMs) {
        String k = clave(p);
        Object mon = monitores.computeIfAbsent(k, x -> new Object());
        AtomicInteger espera = esperando.computeIfAbsent(k, x -> new AtomicInteger());
        latidos.put(k, System.currentTimeMillis());
        espera.incrementAndGet();
        try {
            long fin = System.currentTimeMillis() + Math.max(0, esperaMs);
            synchronized (mon) {
                while (true) {
                    List<Trabajo> listos = sacar(k);
                    if (!listos.isEmpty() || esperaMs <= 0) {
                        return listos;
                    }
                    long resto = fin - System.currentTimeMillis();
                    if (resto <= 0) {
                        return List.of();
                    }
                    try {
                        mon.wait(resto);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return List.of();
                    }
                }
            }
        } finally {
            espera.decrementAndGet();
            latidos.put(k, System.currentTimeMillis());
        }
    }

    private List<Trabajo> sacar(String k) {
        Deque<Trabajo> cola = colas.get(k);
        if (cola == null) {
            return List.of();
        }
        synchronized (cola) {
            purgar(cola);
            List<Trabajo> out = new ArrayList<>(cola);
            cola.clear();
            return out;
        }
    }

    private static void purgar(Deque<Trabajo> cola) {
        long limite = System.currentTimeMillis() - TTL_MS;
        cola.removeIf(t -> t.creadoMs() < limite);
    }

    private static Map<String, Object> validarEvento(Map<String, Object> e) {
        if (e == null || !(e.get("tipo") instanceof String tipo) || !EVENTOS.contains(tipo)) {
            throw new NegocioException("Evento de caja no válido.");
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("tipo", tipo);
        if (e.get("monto") instanceof Number n) {
            out.put("monto", n.doubleValue());
        }
        if (e.get("motivo") instanceof String m) {
            out.put("motivo", m.length() > 200 ? m.substring(0, 200) : m);
        }
        return out;
    }
}
