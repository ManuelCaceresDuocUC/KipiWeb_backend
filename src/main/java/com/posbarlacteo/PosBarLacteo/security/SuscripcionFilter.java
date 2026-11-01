package com.posbarlacteo.PosBarLacteo.security;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.posbarlacteo.PosBarLacteo.service.SuscripcionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Aplica el estado de la suscripción a cada petición autenticada.
 * Se instancia en SecurityConfig (no es @Component) para ejecutarse justo después de JwtAuthFilter.
 *
 *  - DENEGADO: 402 en todo.
 *  - SOLO_LECTURA: se permiten GET/HEAD/OPTIONS; cualquier escritura responde 402.
 *  - Siempre permitido: /api/empresa/suscripcion** (para ver el estado y pagar) y cerrar un turno de caja abierto.
 */
public class SuscripcionFilter extends OncePerRequestFilter {

    private final SuscripcionService suscripcionService;

    public SuscripcionFilter(SuscripcionService suscripcionService) {
        this.suscripcionService = suscripcionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p) || p.empresaId() == null) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getRequestURI();
        if (path.startsWith("/api/empresa/suscripcion") || (HttpMethod.POST.matches(req.getMethod()) && path.equals("/api/caja/cerrar"))) {
            chain.doFilter(req, res);
            return;
        }

        SuscripcionService.Vista v;
        try {
            v = suscripcionService.vista(p.empresaId());
        } catch (RuntimeException e) {
            chain.doFilter(req, res); // ante un fallo interno NO se bloquea al cliente
            return;
        }

        boolean lectura = HttpMethod.GET.matches(req.getMethod()) || HttpMethod.HEAD.matches(req.getMethod())
                || HttpMethod.OPTIONS.matches(req.getMethod());

        if (v.modo() == SuscripcionService.Modo.DENEGADO || (v.modo() == SuscripcionService.Modo.SOLO_LECTURA && !lectura)) {
            res.setStatus(402);
            res.setContentType("application/json;charset=UTF-8");
            String msg = v.mensaje() == null ? "Suscripción inactiva." : v.mensaje().replace("\"", "'");
            res.getWriter().write("{\"message\":\"" + msg + "\",\"codigo\":\"SUSCRIPCION_SUSPENDIDA\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
