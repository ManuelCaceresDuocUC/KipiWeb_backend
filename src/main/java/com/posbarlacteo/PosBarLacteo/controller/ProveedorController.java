package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.CompraService;
import com.posbarlacteo.PosBarLacteo.service.CompraService.DatosProveedor;

/** Proveedores de la empresa (solo administradores). */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final CompraService servicio;

    public ProveedorController(CompraService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return servicio.listarProveedores(SecurityUtils.actual());
    }

    @PostMapping
    public Map<String, Object> crear(@RequestBody DatosProveedor d) {
        return servicio.crearProveedor(SecurityUtils.actual(), d);
    }

    @PutMapping("/{id}")
    public Map<String, Object> editar(@PathVariable Long id, @RequestBody DatosProveedor d) {
        return servicio.editarProveedor(SecurityUtils.actual(), id, d);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> desactivar(@PathVariable Long id) {
        servicio.desactivarProveedor(SecurityUtils.actual(), id);
        return Map.of("status", "success");
    }
}
