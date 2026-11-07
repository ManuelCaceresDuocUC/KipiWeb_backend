package com.posbarlacteo.PosBarLacteo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cadena de seguridad propia para /api/owner/** (panel del dueño de KIPIPOS), independiente del login de las empresas.
 * Se evalúa antes que la cadena general.
 */
@Configuration
public class OwnerSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain ownerFilterChain(HttpSecurity http, LimiteSolicitudes limite,
                                                @Value("${owner.api-token:}") String token) throws Exception {
        http
            .securityMatcher("/api/owner/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().hasRole("OWNER"))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> res.sendError(401))
                .accessDeniedHandler((req, res, e) -> res.sendError(403)))
            .addFilterBefore(new OwnerTokenFilter(token, limite), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
