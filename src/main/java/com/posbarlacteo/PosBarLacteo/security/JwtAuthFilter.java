package com.posbarlacteo.PosBarLacteo.security;
import java.io.IOException; // ✨ AGREGAR ESTA LÍNEA
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.posbarlacteo.PosBarLacteo.service.JwtService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            if (jwtService.validarToken(token)) {
                Claims claims = jwtService.obtenerClaims(token);
                String usuario = claims.getSubject();
                String rol = claims.get("rol", String.class); // Leemos el rol inmutable
                
                // Agregamos el prefijo "ROLE_" como estándar en Spring
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase());
                
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        usuario, null, Collections.singletonList(authority));
                        
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        String method = request.getMethod();
        
        // Si es una petición OPTIONS o va a rutas públicas, el filtro JWT no debe ejecutarse
        return "OPTIONS".equalsIgnoreCase(method) || 
            path.startsWith("/api/auth/") || 
            path.startsWith("/auth/") || 
            path.equals("/api/usuarios/login");
    }
}