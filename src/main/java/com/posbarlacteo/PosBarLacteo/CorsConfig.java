package com.posbarlacteo.PosBarLacteo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") 
                // Añadimos tus entornos locales junto al de producción separados por comas
                .allowedOrigins(
                    "https://sistema.kuyval.cl", 
                    "http://localhost:5173", 
                    "http://localhost:5174",
                    "http://posbarlacteo-manuel-2026.s3-website-us-east-1.amazonaws.com", // Producción AWS
                    "http://localhost:5173",                                             // PC Local
                    "http://192.168.100.85:5173"   
                ) 
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH") 
                .allowCredentials(true); 
    }
}