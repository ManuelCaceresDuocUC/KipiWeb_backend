package com.posbarlacteo.PosBarLacteo.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Límite de solicitudes por clave (p. ej. "contacto|IP") con ventana deslizante, en memoria.
 * Suficiente para una sola instancia; con varias habría que moverlo a Redis/BD.
 */
@Component
public class LimiteSolicitudes {

    private static final int MAX_CLAVES = 20_000;
    private final ConcurrentHashMap<String, Deque<Long>> registros = new ConcurrentHashMap<>();

    /** true si la solicitud entra dentro del límite (y la contabiliza); false si ya se excedió. */
    public boolean permitir(String clave, int max, Duration ventana) {
        long ahora = System.currentTimeMillis();
        long desde = ahora - ventana.toMillis();
        if (registros.size() > MAX_CLAVES) {
            registros.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || e.getValue().peekLast() < desde;
                }
            });
        }
        Deque<Long> q = registros.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < desde) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                return false;
            }
            q.addLast(ahora);
            return true;
        }
    }

    /** IP del cliente detrás del proxy de Render (primer valor de X-Forwarded-For). */
    public static String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
