package com.posbarlacteo.PosBarLacteo.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.dto.MovimientoInventarioRequest;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.repository.SucursalRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.InventarioService;

/** Gestión de inventario. Solo ADMIN (ver SecurityConfig: /api/inventario/**). */
@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final InventarioService inventarioService;
    private final SucursalRepository sucursalRepository;

    public InventarioController(InventarioService inventarioService, SucursalRepository sucursalRepository) {
        this.inventarioService = inventarioService;
        this.sucursalRepository = sucursalRepository;
    }

    /** Sucursales de la empresa (para el selector del panel de inventario). */
    @GetMapping("/sucursales")
    public List<Map<String, Object>> sucursales() {
        AuthPrincipal p = SecurityUtils.actual();
        return sucursalRepository.findByEmpresaIdAndActivoTrueOrderByIdAsc(p.empresaId()).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("nombre", s.getNombre());
            m.put("esCasaMatriz", s.isEsCasaMatriz());
            m.put("propia", s.getId().equals(p.sucursalId()));
            return m;
        }).toList();
    }

    @GetMapping("/configuracion")
    public Map<String, Object> configuracion() {
        AuthPrincipal p = SecurityUtils.actual();
        return Map.of("permiteStockNegativo", inventarioService.permiteStockNegativo(p.empresaId()));
    }

    @PutMapping("/configuracion")
    public Map<String, Object> guardarConfiguracion(@RequestBody Map<String, Object> body) {
        AuthPrincipal p = SecurityUtils.actual();
        Object valor = body.get("permiteStockNegativo");
        if (!(valor instanceof Boolean permite)) {
            throw new NegocioException("Indica si se permite o no el stock negativo.");
        }
        inventarioService.definirPermiteStockNegativo(p.empresaId(), permite);
        return Map.of("permiteStockNegativo", permite);
    }

    @PostMapping("/movimientos")
    public Map<String, Object> registrar(@RequestBody MovimientoInventarioRequest req) {
        AuthPrincipal p = SecurityUtils.actual();
        if (req.getProductoId() == null || req.getCantidad() == null) {
            throw new NegocioException("Indica el producto y la cantidad.");
        }
        double stock = inventarioService.registrarManual(p, req.getSucursalId(), req.getProductoId(),
                req.getTipo(), req.getCantidad(), req.getMotivo());
        return Map.of("stock", stock);
    }

    @GetMapping("/movimientos")
    public Map<String, Object> historial(@RequestParam(required = false) Long productoId,
                                         @RequestParam(required = false) Long sucursalId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return inventarioService.listarMovimientos(SecurityUtils.actual(), sucursalId, productoId, page, size);
    }
}
