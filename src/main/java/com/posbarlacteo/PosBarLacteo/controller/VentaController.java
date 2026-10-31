package com.posbarlacteo.PosBarLacteo.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.VentaService;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    /** Solo ADMIN (ver SecurityConfig). Body: {"motivo": "..."}. */
    @PostMapping("/{id}/anular")
    public Map<String, Object> anular(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ventaService.anular(SecurityUtils.actual(), id, body == null ? null : body.get("motivo"));
    }
}
