package com.posbarlacteo.PosBarLacteo.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.AuditoriaService;

/** Registro de actividad de la empresa. Solo ADMIN (/api/admin/** ya lo exige en SecurityConfig). */
@RestController
@RequestMapping("/api/admin/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoria;

    public AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<Map<String, Object>> recientes() {
        return auditoria.recientes(SecurityUtils.actual().empresaId()).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("fecha", e.getFecha());
            m.put("usuario", e.getUsuario());
            m.put("accion", e.getAccion());
            m.put("detalle", e.getDetalle());
            m.put("ip", e.getIp());
            return m;
        }).toList();
    }
}
