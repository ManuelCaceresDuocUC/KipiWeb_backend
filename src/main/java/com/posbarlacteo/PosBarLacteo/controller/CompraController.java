package com.posbarlacteo.PosBarLacteo.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.CompraService;
import com.posbarlacteo.PosBarLacteo.service.CompraService.NuevaCompra;
import com.posbarlacteo.PosBarLacteo.service.CompraService.NuevoPago;

/** Compras a proveedores y cuentas por pagar (solo administradores). */
@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService servicio;

    public CompraController(CompraService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<Map<String, Object>> listar(
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.listar(SecurityUtils.actual(), proveedorId, estado, desde, hasta);
    }

    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        return servicio.resumenGeneral(SecurityUtils.actual());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable Long id) {
        return servicio.detalle(SecurityUtils.actual(), id);
    }

    @PostMapping
    public Map<String, Object> registrar(@RequestBody NuevaCompra r) {
        return servicio.registrar(SecurityUtils.actual(), r);
    }

    @PostMapping("/{id}/pagos")
    public Map<String, Object> pagar(@PathVariable Long id, @RequestBody NuevoPago pago) {
        return servicio.pagar(SecurityUtils.actual(), id, pago);
    }

    @PostMapping("/{id}/anular")
    public Map<String, Object> anular(@PathVariable Long id, @RequestBody Map<String, String> cuerpo) {
        return servicio.anular(SecurityUtils.actual(), id, cuerpo == null ? null : cuerpo.get("motivo"));
    }

    @PostMapping(value = "/{id}/adjunto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> adjuntar(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return servicio.adjuntar(SecurityUtils.actual(), id, file);
    }

    /** URL temporal (10 min) para ver el documento adjunto. */
    @GetMapping("/{id}/adjunto")
    public Map<String, Object> adjunto(@PathVariable Long id) {
        return Map.of("url", servicio.urlAdjunto(SecurityUtils.actual(), id));
    }
}
