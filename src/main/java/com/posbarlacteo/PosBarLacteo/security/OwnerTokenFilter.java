package com.posbarlacteo.PosBarLacteo.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autenticación del panel del dueño: un token secreto largo (variable OWNER_API_TOKEN) enviado como Bearer.
 * No es un usuario de ninguna empresa. Si la variable no está definida, el panel queda desactivado (404).
 * Se compara en tiempo constante y los intentos fallidos se limitan por IP.
 * No es @Component a propósito: solo se instancia dentro de la cadena /api/owner/**.
 */
public class OwnerTokenFilter extends OncePerRequestFilter {

    private final byte[] hashEsperado;
    private final LimiteSolicitudes limite;

    public OwnerTokenFilter(String token, LimiteSolicitudes limite) {
        this.hashEsperado = (token == null || token.isBlank()) ? null : sha256(token.trim());
        this.limite = limite;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }
        if (hashEsperado == null) {
            responder(res, 404, "No disponible.");
            return;
        }
        String cab = req.getHeader("Authorization");
        boolean valido = cab != null && cab.startsWith("Bearer ")
                && MessageDigest.isEqual(hashEsperado, sha256(cab.substring(7).trim()));
        if (!valido) {
            // Solo los intentos fallidos cuentan: 10 cada 15 minutos por IP
            if (!limite.permitir("owner-fallo|" + ip(req), 10, Duration.ofMinutes(15))) {
                responder(res, 429, "Demasiados intentos. Espera unos minutos.");
                return;
            }
            responder(res, 401, "Token no válido.");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "propietario", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
        chain.doFilter(req, res);
    }

    private static String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void responder(HttpServletResponse res, int status, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"" + msg + "\"}");
    }
}
