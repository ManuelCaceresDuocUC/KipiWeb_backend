package com.posbarlacteo.PosBarLacteo.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.repository.VentaRepository;

@RestController
@RequestMapping("/api/movimientos")
@CrossOrigin(origins = {
    "http://posbarlacteo-manuel-2026.s3-website-us-east-1.amazonaws.com", // Producción AWS
    "http://localhost:5173",
    "http://34.203.91.138",
    "https://ordpos.duckdns.org",                                             // PC Local
    "http://192.168.100.85:5173"                                         // Tu Celular
})
public class MovimientoController {

    @Autowired
    private VentaRepository ventaRepository;

    @GetMapping
    public List<Venta> obtenerHistorial(
            @RequestParam(defaultValue = "1") Long empresaId, // ✨ 1. Recibimos el ID de la empresa
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin
    ) {
        // Si no mandan fechaInicio, asumimos que quieren ver las ventas de HOY
        if (fechaInicio == null) {
            fechaInicio = LocalDate.now();
            fechaFin = LocalDate.now();
        } 
        // Si mandan inicio pero no fin, asumimos que quieren ver un solo día específico
        else if (fechaFin == null) {
            fechaFin = fechaInicio;
        }

        // Convertimos a LocalDateTime: Inicio del día (00:00:00) y Fin del día (23:59:59)
        LocalDateTime inicioDelDia = fechaInicio.atStartOfDay();
        LocalDateTime finDelDia = fechaFin.atTime(23, 59, 59);

        // ✨ 2. Pasamos el empresaId al repositorio
        return ventaRepository.findByEmpresaIdAndFechaHoraBetween(empresaId, inicioDelDia, finDelDia);
    }
}