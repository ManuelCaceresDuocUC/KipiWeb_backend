package com.posbarlacteo.PosBarLacteo.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.AuditoriaService;
import com.posbarlacteo.PosBarLacteo.service.ExportacionService;

/** Descarga de datos en CSV. Solo ADMIN (SecurityConfig: /api/exportar/**). */
@RestController
@RequestMapping("/api/exportar")
public class ExportacionController {

    private final ExportacionService servicio;
    private final AuditoriaService auditoria;

    public ExportacionController(ExportacionService servicio, AuditoriaService auditoria) {
        this.servicio = servicio;
        this.auditoria = auditoria;
    }

    @GetMapping("/{tipo}")
    public ResponseEntity<byte[]> exportar(@PathVariable String tipo,
                                           @RequestParam(required = false) String desde,
                                           @RequestParam(required = false) String hasta) {
        AuthPrincipal p = SecurityUtils.actual();
        String csv = switch (tipo) {
            case "productos" -> servicio.productos(p);
            case "clientes" -> servicio.clientes(p);
            case "ventas" -> servicio.ventas(p, fecha(desde), fecha(hasta));
            case "devoluciones" -> servicio.devoluciones(p, fecha(desde), fecha(hasta));
            case "movimientos-inventario" -> servicio.movimientosInventario(p);
            default -> throw new NegocioException("Ese tipo de exportación no existe.");
        };
        auditoria.registrar(p, "EXPORTAR", "Exportó " + tipo + (desde != null ? " (" + desde + " a " + hasta + ")" : "") + ".");
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDate fecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new NegocioException("La fecha \"" + texto + "\" no es válida (usa AAAA-MM-DD).");
        }
    }
}
