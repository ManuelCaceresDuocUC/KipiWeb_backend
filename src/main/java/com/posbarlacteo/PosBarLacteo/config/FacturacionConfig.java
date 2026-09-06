package com.posbarlacteo.PosBarLacteo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class FacturacionConfig {

    @Value("${facturacion.api.url}")
    private String apiUrl;

    @Value("${facturacion.api.key}")
    private String apiKey;

    @Bean
    public RestClient haulmerRestClient() {
        // ✨ SOLUCIÓN: Usamos RestClient.builder() directamente sin inyectarlo en los parámetros
        return RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("apikey", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}