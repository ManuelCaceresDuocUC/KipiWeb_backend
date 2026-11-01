package com.posbarlacteo.PosBarLacteo.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.posbarlacteo.PosBarLacteo.exception.ServicioExternoException;

/** Cliente HTTP de la API de Flow con firma HMAC-SHA256. Único lugar que conoce el protocolo de firma. */
@Component
public class FlowClient {

    private static final Logger log = LoggerFactory.getLogger(FlowClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() { };

    private final String apiKey;
    private final String secretKey;
    private final String baseUrl;
    private final RestTemplate http;

    public FlowClient(@Value("${flow.api.key}") String apiKey,
                      @Value("${flow.secret.key}") String secretKey,
                      @Value("${flow.base-url:https://sandbox.flow.cl/api}") String baseUrl) {
        this.apiKey = apiKey.trim();
        this.secretKey = secretKey.trim();
        this.baseUrl = baseUrl.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = new RestTemplate(factory);
    }

    public Map<String, Object> post(String endpoint, Map<String, String> params) {
        Map<String, String> p = firmados(params);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        p.forEach(body::add);
        try {
            ResponseEntity<Map<String, Object>> r = http.exchange(baseUrl + endpoint, HttpMethod.POST,
                    new HttpEntity<>(body, headers), MAP_TYPE);
            return r.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Flow POST {} -> {} | body: [{}]", endpoint, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServicioExternoException("El sistema de pagos rechazó la solicitud. Revisa los datos e inténtalo nuevamente.");
        } catch (RestClientException e) {
            log.error("Flow {} no disponible: {}", endpoint, e.getMessage());
            throw new ServicioExternoException("No se pudo conectar con el sistema de pagos. Inténtalo más tarde.");
        }
    }

    public Map<String, Object> get(String endpoint, Map<String, String> params) {
        Map<String, String> p = firmados(params);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + endpoint);
        p.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();
        try {
            ResponseEntity<Map<String, Object>> r = http.exchange(uri, HttpMethod.GET, null, MAP_TYPE);
            return r.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Flow GET {} -> {} | body: [{}]", endpoint, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServicioExternoException("No se pudo consultar el sistema de pagos. Inténtalo más tarde.");
        } catch (RestClientException e) {
            log.error("Flow {} no disponible: {}", endpoint, e.getMessage());
            throw new ServicioExternoException("No se pudo conectar con el sistema de pagos. Inténtalo más tarde.");
        }
    }

    private Map<String, String> firmados(Map<String, String> params) {
        Map<String, String> p = new TreeMap<>(params); // la firma exige orden alfabético
        p.put("apiKey", apiKey);
        p.put("s", firmar(p));
        return p;
    }

    private String firmar(Map<String, String> params) {
        try {
            StringBuilder toSign = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if ("s".equals(e.getKey())) continue;
                toSign.append(e.getKey()).append(e.getValue());
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(toSign.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error firmando petición a Flow", e);
        }
    }
}
