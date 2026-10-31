package com.posbarlacteo.PosBarLacteo.controller;

import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;

/** Políticas de venta de la empresa. Lectura: cualquier usuario (el POS decide qué medios mostrar). Escritura: ADMIN. */
@RestController
@RequestMapping("/api/empresa")
public class EmpresaConfigController {

    private final EmpresaRepository empresaRepository;

    public EmpresaConfigController(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @GetMapping("/config-ventas")
    @Transactional(readOnly = true)
    public Map<String, Object> leer() {
        Empresa e = empresa(SecurityUtils.actual());
        return Map.of("permiteFiado", Boolean.TRUE.equals(e.getPermiteFiado()));
    }

    @PutMapping("/config-ventas")
    @Transactional
    public Map<String, Object> guardar(@RequestBody Map<String, Object> body) {
        Empresa e = empresa(SecurityUtils.actual());
        Object valor = body.get("permiteFiado");
        if (!(valor instanceof Boolean permite)) {
            throw new NegocioException("Indica si se permite o no el fiado.");
        }
        e.setPermiteFiado(permite);
        empresaRepository.save(e);
        return Map.of("permiteFiado", permite);
    }

    private Empresa empresa(AuthPrincipal p) {
        return empresaRepository.findById(p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }
}
