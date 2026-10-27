package com.posbarlacteo.PosBarLacteo.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.cors.extra-origins:}")
    private String extraOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // API stateless con token en cabecera, sin cookies
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    escribirJson(res, HttpServletResponse.SC_UNAUTHORIZED, "Sesión no válida o expirada."))
                .accessDeniedHandler((req, res, e) ->
                    escribirJson(res, HttpServletResponse.SC_FORBIDDEN, "No tienes permisos para esta acción.")))
            .authorizeHttpRequests(auth -> auth
                // 1. Preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 2. Públicos: login y registro/retorno de pago
                .requestMatchers("/api/usuarios/login", "/api/auth/**").permitAll()

                // 3. Solo administradores
                .requestMatchers("/api/admin/**", "/api/dte/**", "/api/inventario/**", "/api/reportes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/caja/historial", "/api/usuarios").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/clientes/reiniciar-creditos").hasRole("ADMIN")
                // Catálogo: lectura para todos; alta/edición/baja solo administrador
                .requestMatchers(HttpMethod.POST, "/api/productos/**", "/api/categorias/**", "/api/recetas/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/productos/**", "/api/categorias/**", "/api/recetas/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/productos/**", "/api/categorias/**", "/api/recetas/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/productos/**", "/api/categorias/**", "/api/recetas/**").hasRole("ADMIN")

                // 4. Operativa general del POS (cualquier usuario autenticado de la empresa)
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        Set<String> origenes = new LinkedHashSet<>();
        agregarOrigenConVariantes(origenes, frontendUrl);
        if (extraOrigins != null) {
            for (String o : extraOrigins.split(",")) {
                agregarOrigenConVariantes(origenes, o);
            }
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(new ArrayList<>(origenes));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false); // el token viaja en la cabecera Authorization, no en cookies
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Agrega el origen sin barra final y, para dominios https, la variante con/sin "www.". */
    private static void agregarOrigenConVariantes(Set<String> destino, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        String o = valor.trim();
        while (o.endsWith("/")) {
            o = o.substring(0, o.length() - 1);
        }
        destino.add(o);
        if (o.startsWith("https://www.")) {
            destino.add("https://" + o.substring("https://www.".length()));
        } else if (o.startsWith("https://") && o.indexOf('.', 8) > 0 && !o.contains("localhost")) {
            String host = o.substring("https://".length());
            if (host.chars().filter(ch -> ch == '.').count() == 1) { // dominio raíz (ej. kipipos.cl)
                destino.add("https://www." + host);
            }
        }
    }

    private static void escribirJson(HttpServletResponse res, int status, String mensaje) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"" + mensaje + "\"}");
    }
}
