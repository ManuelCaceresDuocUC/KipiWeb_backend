package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.ColaImpresionService;

/** Impresión remota: un equipo deja el trabajo y la "estación de impresión" (PC con la impresora) lo recoge. */
@RestController
@RequestMapping("/api/impresion")
public class ImpresionController {

    public record TrabajoRequest(String tipo, List<String> documentos, Map<String, Object> evento, Boolean abrirGaveta) { }

    private final ColaImpresionService cola;

    public ImpresionController(ColaImpresionService cola) {
        this.cola = cola;
    }

    /** ¿Hay una caja de impresión conectada en mi sucursal? */
    @GetMapping("/estacion")
    public Map<String, Object> estacion() {
        return Map.of("conectada", cola.estacionConectada(SecurityUtils.actual()));
    }

    /** Lo llama la estación de impresión cada pocos segundos: sirve de latido y entrega los trabajos pendientes. */
    @GetMapping("/pendientes")
    public List<ColaImpresionService.Trabajo> pendientes(@RequestParam(defaultValue = "0") int espera) {
        // espera (segundos, máx. 15): la consulta queda abierta hasta que llegue un trabajo
        return cola.reclamar(SecurityUtils.actual(), Math.min(Math.max(espera, 0), 15) * 1000L);
    }

    @PostMapping("/trabajos")
    public Map<String, Object> encolar(@RequestBody TrabajoRequest req) {
        var t = cola.encolar(SecurityUtils.actual(), req.tipo(), req.documentos(), req.evento(), Boolean.TRUE.equals(req.abrirGaveta()));
        return Map.of("id", t.id());
    }
}
