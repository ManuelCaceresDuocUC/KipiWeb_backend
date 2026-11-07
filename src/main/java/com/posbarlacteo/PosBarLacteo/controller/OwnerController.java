package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.service.ErroresRecientes;
import com.posbarlacteo.PosBarLacteo.service.OwnerPanelService;

/** API del panel del dueño de KIPIPOS. Protegida por OWNER_API_TOKEN (ver OwnerSecurityConfig). */
@RestController
@RequestMapping("/api/owner")
public class OwnerController {

    private final OwnerPanelService panel;

    public OwnerController(OwnerPanelService panel) {
        this.panel = panel;
    }

    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        return panel.resumen();
    }

    @GetMapping("/empresas")
    public List<Map<String, Object>> empresas() {
        return panel.empresas();
    }

    @GetMapping("/empresas/{id}")
    public Map<String, Object> detalle(@PathVariable Long id) {
        return panel.detalle(id);
    }

    @PostMapping("/empresas/{id}/suspender")
    public Map<String, Object> suspender(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return panel.suspender(id, body == null ? null : body.get("motivo"));
    }

    @PostMapping("/empresas/{id}/reactivar")
    public Map<String, Object> reactivar(@PathVariable Long id) {
        return panel.reactivar(id);
    }

    @PostMapping("/empresas/{id}/sincronizar")
    public Map<String, Object> sincronizar(@PathVariable Long id) {
        return panel.sincronizar(id);
    }

    @GetMapping("/respaldos")
    public Map<String, Object> respaldos(@RequestParam(defaultValue = "30") int max) {
        return panel.respaldos(Math.min(Math.max(max, 1), 100));
    }

    @GetMapping("/errores")
    public List<ErroresRecientes.Entrada> errores(@RequestParam(required = false) String nivel,
                                                  @RequestParam(defaultValue = "100") int max) {
        return panel.errores(nivel, max);
    }
}
