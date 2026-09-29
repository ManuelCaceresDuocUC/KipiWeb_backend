package com.posbarlacteo.PosBarLacteo.security;

import java.io.IOException;
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
            String token = authHeader.substring(7).trim();
            
            // Validar que el token no sea "null", "undefined" ni esté vacío
            if (!token.isEmpty() && !"null".equalsIgnoreCase(token) && !"undefined".equalsIgnoreCase(token)) {
                try {
                    if (jwtService.validarToken(token)) {
                        Claims claims = jwtService.obtenerClaims(token);
                        String usuario = claims.getSubject();
                        
                        // Fallback seguro si el claim es "rol" o "role"
                        String rol = claims.get("rol", String.class);
                        if (rol == null) {
                            rol = claims.get("role", String.class);
                        }
                        if (rol == null) {
                            rol = "CAJERO"; // Rol por defecto si no viene en el token
                        }
                        
                        // Normalización del prefijo ROLE_
                        String roleFormatted = rol.toUpperCase().startsWith("ROLE_") 
                                ? rol.toUpperCase() 
                                : "ROLE_" + rol.toUpperCase();

                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleFormatted);
                        
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                usuario, null, Collections.singletonList(authority));
                                
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (Exception e) {
                    // Si el token falló o expiró, SecurityContextHolder queda limpio y retorna 401/403 controlado
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        String method = request.getMethod();
        
        return "OPTIONS".equalsIgnoreCase(method) || 
               path.startsWith("/api/auth/") || 
               path.startsWith("/auth/") || 
               path.contains("/login");
    }
}