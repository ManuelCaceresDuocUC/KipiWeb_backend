package com.posbarlacteo.PosBarLacteo.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.repository.VentaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;

/** Historial de ventas de la empresa del token. Un vendedor ve solo las suyas; el admin ve todas. */
@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    private final VentaRepository ventaRepository;

    public MovimientoController(VentaRepository ventaRepository) {
        this.ventaRepository = ventaRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Venta> obtenerHistorial(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        AuthPrincipal p = SecurityUtils.actual();

        if (fechaInicio == null) {
            fechaInicio = LocalDate.now();
            fechaFin = fechaInicio;
        } else if (fechaFin == null) {
            fechaFin = fechaInicio;
        }
        if (fechaFin.isBefore(fechaInicio)) {
            throw new NegocioException("La fecha final no puede ser anterior a la inicial.");
        }

        List<Venta> ventas = ventaRepository.findByEmpresaIdAndFechaHoraBetween(
                p.empresaId(), fechaInicio.atStartOfDay(), fechaFin.atTime(LocalTime.MAX));

        if (!p.esAdmin()) {
            ventas = ventas.stream()
                    .filter(v -> v.getUsuario() != null && p.usuarioId().equals(v.getUsuario().getId()))
                    .toList();
        }
        return ventas;
    }
}
