package com.posbarlacteo.PosBarLacteo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class FacturacionConfig {

    private static final Logger log = LoggerFactory.getLogger(FacturacionConfig.class);

    @Value("${facturacion.api.url:https://dev-api.haulmer.com}")
    private String apiUrl;

    @Value("${facturacion.api.key:}")
    private String apiKey;

    @Bean
    public RestClient haulmerRestClient() {
        // Limpia comillas, saltos de línea, espacios, tabulaciones y corchetes
        String cleanUrl = apiUrl.replaceAll("[\"'\r\n\t\\[\\]() ]", "").trim();
        String cleanKey = apiKey.trim();

        log.info(">>> Conectando Haulmer RestClient a la URL: '{}'", cleanUrl);

        return RestClient.builder()
                .baseUrl(cleanUrl)
                .defaultHeader("apikey", cleanKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}