package com.posbarlacteo.PosBarLacteo.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.dto.AbonoRequest;
import com.posbarlacteo.PosBarLacteo.dto.PagoRequest;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.service.VentaService; // Servicio para crear el PDF del voucher
import com.posbarlacteo.PosBarLacteo.service.VoucherService;

@RestController
@CrossOrigin(origins = {
    "http://posbarlacteo-manuel-2026.s3-website-us-east-1.amazonaws.com",
    "http://localhost:5173",
    "http://34.203.91.138",
    "https://ordpos.duckdns.org",
    "http://192.168.100.85:5173"
})
@RequestMapping("/api/pagos")
public class PagoController {

    private final VentaService ventaService;
    private final VoucherService voucherService; // Nuevo servicio local para vouchers

    public PagoController(VentaService ventaService, VoucherService voucherService) {
        this.ventaService = ventaService;
        this.voucherService = voucherService;
    }

    @PostMapping("/efectivo")
    public ResponseEntity<?> procesarEfectivo(@RequestBody PagoRequest request) {
        try {
            Venta venta = ventaService.procesarVentaCompleta(
                request.getItems(), 
                (double) request.getMonto(), 
                "EFECTIVO", 
                request.getUsuarioId(),
                request.getEmpresaId(),
                request.getClienteId()
            );

            // Genera PDF local en Base64 con 2 copias (Cliente + Local)
            String base64Pdf = voucherService.generarVoucherBase64(venta, request.getItems());

            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "Venta en efectivo registrada y voucher generado",
                "ventaId", venta.getId(),
                "boletaPdf", base64Pdf
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/cobrar")
    public ResponseEntity<?> procesarPagoTarjeta(@RequestBody PagoRequest request) {
        try {
            if (request.getItems() == null || request.getItems().isEmpty()) {
                throw new Exception("El carrito está vacío");
            }
            
            Venta venta = ventaService.procesarVentaCompleta(
                request.getItems(), 
                (double) request.getMonto(), 
                "TARJETA", 
                request.getUsuarioId(),
                request.getEmpresaId(),
                request.getClienteId()
            );
            
            // Genera PDF local en Base64 con 2 copias (Cliente + Local)
            String base64Pdf = voucherService.generarVoucherBase64(venta, request.getItems());
            
            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "Venta con tarjeta registrada y voucher generado",
                "ventaId", venta.getId(),
                "boletaPdf", base64Pdf
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/credito")
    public ResponseEntity<?> procesarCredito(@RequestBody PagoRequest request) {
        try {
            if (request.getClienteId() == null) {
                throw new Exception("El ID del cliente es obligatorio para ventas a crédito");
            }
            
            Venta venta = ventaService.procesarVentaCompleta(
                request.getItems(), 
                (double) request.getMonto(), 
                "CREDITO", 
                request.getUsuarioId(),
                request.getEmpresaId(),
                request.getClienteId() 
            );

            String base64Pdf = voucherService.generarVoucherBase64(venta, request.getItems());

            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "Venta a crédito registrada exitosamente.",
                "ventaId", venta.getId(),
                "boletaPdf", base64Pdf
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/abono")
    public ResponseEntity<?> procesarAbono(@RequestBody AbonoRequest request) {
        try {
            if (request.getClienteId() == null || request.getMonto() == null || request.getMonto() <= 0) {
                throw new Exception("El cliente y un monto válido son obligatorios para registrar un abono.");
            }

            Map<String, Object> datosAbono = ventaService.procesarAbonoCliente(
                request.getClienteId(),
                request.getMonto(),
                request.getMetodoPago(),
                request.getUsuarioId(),
                request.getEmpresaId()
            );

            String rutaPdf = (String) datosAbono.getOrDefault("comprobantePdf", "Guardado en disco local");

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Abono registrado e impreso exitosamente.",
                "datos", datosAbono,
                "rutaArchivo", rutaPdf
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }
}