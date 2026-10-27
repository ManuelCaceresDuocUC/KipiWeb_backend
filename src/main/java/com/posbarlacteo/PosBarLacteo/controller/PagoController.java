package com.posbarlacteo.PosBarLacteo.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.dto.PagoRequest;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.DteService;
import com.posbarlacteo.PosBarLacteo.service.VentaService;

/** Los errores de negocio los traduce GlobalExceptionHandler a {"message": "..."} con el código HTTP correcto. */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final VentaService ventaService;
    private final DteService dteService;

    public PagoController(VentaService ventaService, DteService dteService) {
        this.ventaService = ventaService;
        this.dteService = dteService;
    }

    @PostMapping("/efectivo")
    public ResponseEntity<?> procesarEfectivo(@RequestBody PagoRequest request) {
        return cobrar(request, "EFECTIVO", "Venta en efectivo registrada correctamente");
    }

    @PostMapping("/cobrar")
    public ResponseEntity<?> procesarPagoTarjeta(@RequestBody PagoRequest request) {
        return cobrar(request, "TARJETA", "Venta con tarjeta registrada correctamente");
    }

    @PostMapping("/credito")
    public ResponseEntity<?> procesarCredito(@RequestBody PagoRequest request) {
        if (request.getClienteId() == null) {
            throw new NegocioException("El cliente es obligatorio para ventas a crédito.");
        }
        return cobrar(request, "CREDITO", "Venta a crédito registrada exitosamente.");
    }

    private ResponseEntity<?> cobrar(PagoRequest request, String metodo, String mensaje) {
        AuthPrincipal p = SecurityUtils.actual();
        // Identidad siempre desde el token: lo que venga en el cuerpo se pisa
        request.setEmpresaId(p.empresaId());
        request.setUsuarioId(p.usuarioId());

        Venta venta = ventaService.procesarVentaCompleta(
                request.getItems(), request.getMonto(), metodo, p, request.getClienteId());

        String base64Pdf = dteService.obtenerDocumentoVenta(venta, request, metodo);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", mensaje,
                "ventaId", venta.getId(),
                "boletaPdf", base64Pdf == null ? "" : base64Pdf));
    }
}
