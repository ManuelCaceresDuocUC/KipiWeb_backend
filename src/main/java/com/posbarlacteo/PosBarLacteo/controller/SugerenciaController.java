package com.posbarlacteo.PosBarLacteo.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.SugerenciaPreciosService;

/** Ofertas y sugerencias de precio (solo administradores). */
@RestController
@RequestMapping("/api/sugerencias")
public class SugerenciaController {

    private final SugerenciaPreciosService servicio;

    public SugerenciaController(SugerenciaPreciosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public Map<String, Object> analizar(@RequestParam(defaultValue = "30") int dias,
                                        @RequestParam(defaultValue = "20") double margen) {
        return servicio.analizar(SecurityUtils.actual(), dias, margen);
    }

    /** Cambia el precio de un producto con el valor sugerido (body: {productoId, precio}). */
    @PostMapping("/aplicar-precio")
    public Map<String, Object> aplicarPrecio(@RequestBody Map<String, Number> cuerpo) {
        Number id = cuerpo.get("productoId");
        Number precio = cuerpo.get("precio");
        return servicio.aplicarPrecio(SecurityUtils.actual(), id == null ? null : id.longValue(),
                precio == null ? null : precio.longValue());
    }
}
