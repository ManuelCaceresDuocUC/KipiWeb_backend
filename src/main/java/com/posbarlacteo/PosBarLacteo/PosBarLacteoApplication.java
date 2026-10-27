package com.posbarlacteo.PosBarLacteo;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
public class PosBarLacteoApplication {

    public static void main(String[] args) {
        // Render corre en UTC. Fijamos la zona del negocio ANTES de arrancar para que
        // LocalDateTime.now()/LocalDate.now() (ventas, turnos, fecha de emisión DTE)
        // usen la hora de Chile y no la del servidor.
        String zona = System.getenv().getOrDefault("APP_TIMEZONE", "America/Santiago");
        TimeZone.setDefault(TimeZone.getTimeZone(zona));

        SpringApplication.run(PosBarLacteoApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
