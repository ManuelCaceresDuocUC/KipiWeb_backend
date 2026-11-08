package com.posbarlacteo.PosBarLacteo.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.AuditoriaService;

/**
 * Deja constancia de las aperturas manuales de la gaveta (sin venta). La apertura física la hace el programa local de la caja;
 * aquí solo se registra quién la pidió y por qué. Solo administradores (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/caja/gaveta")
public class GavetaController {

    private final AuditoriaService auditoria;

    public GavetaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    @PostMapping("/abrir")
    public ResponseEntity<Void> abrirManual(@RequestBody Map<String, String> body) {
        String motivo = body == null ? "" : String.valueOf(body.getOrDefault("motivo", "")).trim();
        if (motivo.length() < 3) {
            throw new NegocioException("Indica el motivo de la apertura.");
        }
        AuthPrincipal p = SecurityUtils.actual();
        auditoria.registrar(p, "GAVETA_MANUAL", "Apertura de gaveta sin venta. Motivo: " + motivo);
        return ResponseEntity.ok().build();
    }
}
