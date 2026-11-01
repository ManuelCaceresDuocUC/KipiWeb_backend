package com.posbarlacteo.PosBarLacteo.service;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;

/**
 * Verifica el captcha de Cloudflare Turnstile. Si TURNSTILE_SECRET no está configurado, la verificación se omite
 * (útil en desarrollo): en producción debe definirse.
 */
@Service
public class TurnstileService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);
    private static final String URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secret;
    private final RestTemplate http;

    public TurnstileService(@Value("${turnstile.secret:}") String secret) {
        this.secret = secret == null ? "" : secret.trim();
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(4));
        f.setReadTimeout(Duration.ofSeconds(6));
        this.http = new RestTemplate(f);
        if (this.secret.isEmpty()) {
            log.warn("TURNSTILE_SECRET no configurado: el captcha NO se está verificando.");
        }
    }

    public void exigir(String token, String ip) {
        if (secret.isEmpty()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new NegocioException("Confirma que no eres un robot e inténtalo de nuevo.");
        }
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secret);
            body.add("response", token);
            if (ip != null) body.add("remoteip", ip);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            Map<String, Object> r = http.exchange(URL, HttpMethod.POST, new HttpEntity<>(body, h),
                    new ParameterizedTypeReference<Map<String, Object>>() { }).getBody();
            if (r != null && Boolean.TRUE.equals(r.get("success"))) {
                return;
            }
        } catch (RuntimeException e) {
            log.error("No se pudo verificar Turnstile: {}", e.getMessage());
            throw new NegocioException("No pudimos verificar el captcha. Inténtalo nuevamente en unos segundos.");
        }
        throw new NegocioException("No pudimos verificar que no eres un robot. Recarga la página e inténtalo de nuevo.");
    }
}
