package com.posbarlacteo.PosBarLacteo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.dto.AperturaCajaDTO;
import com.posbarlacteo.PosBarLacteo.dto.CierreCajaDTO;
import com.posbarlacteo.PosBarLacteo.dto.EstadoCajaDTO;
import com.posbarlacteo.PosBarLacteo.dto.MovimientoCajaDTO;
import com.posbarlacteo.PosBarLacteo.dto.ResumenCajaDTO;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.CajaService;

/**
 * La caja siempre es la del usuario del token. El parámetro ?usuarioId que aún envíe el frontend
 * antiguo se ignora. Los errores de negocio se devuelven como texto plano (el frontend usa response.text()).
 */
@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @GetMapping("/estado")
    public ResponseEntity<EstadoCajaDTO> obtenerEstadoCaja() {
        return ResponseEntity.ok(new EstadoCajaDTO(cajaService.tieneCajaAbierta(SecurityUtils.actual())));
    }

    @PostMapping("/abrir")
    public ResponseEntity<?> abrirCaja(@RequestBody AperturaCajaDTO dto) {
        try {
            cajaService.abrirCaja(SecurityUtils.actual(), dto.getMontoInicial());
            return ResponseEntity.ok().build();
        } catch (NegocioException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/cerrar")
    public ResponseEntity<?> cerrarCaja(@RequestBody CierreCajaDTO dto) {
        try {
            cajaService.cerrarCaja(SecurityUtils.actual(), dto);
            return ResponseEntity.ok().build();
        } catch (NegocioException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/movimiento")
    public ResponseEntity<?> registrarMovimiento(@RequestBody MovimientoCajaDTO dto) {
        try {
            cajaService.registrarMovimiento(SecurityUtils.actual(), dto);
            return ResponseEntity.ok().build();
        } catch (NegocioException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/resumen")
    public ResponseEntity<?> obtenerResumen() {
        try {
            ResumenCajaDTO resumen = cajaService.obtenerResumen(SecurityUtils.actual());
            return ResponseEntity.ok(resumen);
        } catch (NegocioException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Solo ADMIN (ver SecurityConfig). */
    @GetMapping("/historial")
    public ResponseEntity<?> obtenerHistorial() {
        return ResponseEntity.ok(cajaService.obtenerHistorial(SecurityUtils.actual()));
    }
}
