package com.posbarlacteo.PosBarLacteo.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.service.FacturacionService;
import com.posbarlacteo.PosBarLacteo.service.VentaService;

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
    private final FacturacionService facturacionService; 
    private final ProductoRepository productoRepository;

    public PagoController(VentaService ventaService, 
                           FacturacionService facturacionService,
                           ProductoRepository productoRepository) {
        this.ventaService = ventaService;
        this.facturacionService = facturacionService;
        this.productoRepository = productoRepository;
    }

    @PostMapping("/efectivo")
    public ResponseEntity<?> procesarEfectivo(@RequestBody PagoRequest request) {
        try {
            ventaService.procesarVentaCompleta(
                request.getItems(), 
                (double) request.getMonto(), 
                "EFECTIVO", 
                request.getUsuarioId(),
                request.getEmpresaId(),
                request.getClienteId()
            );

            Map<String, Object> payloadBoleta = construirPayloadHaulmer(request);
            Map<String, Object> respuestaSii = facturacionService.emitirBoleta(payloadBoleta);

            String base64Pdf = respuestaSii != null ? (String) respuestaSii.get("PDF") : "";

            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "Venta en efectivo registrada y boleta emitida",
                "boleta", respuestaSii,
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
            
            ventaService.procesarVentaCompleta(
                request.getItems(), 
                (double) request.getMonto(), 
                "TARJETA", 
                request.getUsuarioId(),
                request.getEmpresaId(),
                request.getClienteId()
            );
            
            Map<String, Object> payloadBoleta = construirPayloadHaulmer(request);
            Map<String, Object> respuestaSii = facturacionService.emitirBoleta(payloadBoleta);
            
            String base64Pdf = respuestaSii != null ? (String) respuestaSii.get("PDF") : "";
            
            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "Venta con tarjeta registrada y boleta emitida",
                "boleta", respuestaSii,
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

            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "Venta a crédito registrada exitosamente. El vale se guardó e imprimió automáticamente.",
                "ventaId", venta.getId()
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

    private Map<String, Object> construirPayloadHaulmer(PagoRequest request) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> encabezado = new HashMap<>();
        
        Map<String, Object> idDoc = new HashMap<>();
        idDoc.put("TipoDTE", 39); 
        idDoc.put("FchEmis", java.time.LocalDate.now().toString());
        idDoc.put("IndServicio", "3"); 
        
        Map<String, Object> emisor = new HashMap<>();
        emisor.put("RUTEmisor", "76795561-8");
        emisor.put("RznSocEmisor", "Haulmer SpA"); 
        emisor.put("GiroEmisor", "Venta al por menor"); 
        emisor.put("DirOrigen", "Arturo Prat 527");
        emisor.put("CmnaOrigen", "Curicó");
        
        Map<String, Object> receptor = new HashMap<>();
        receptor.put("RUTRecep", "66666666-6");

        List<Map<String, Object>> detallesSii = new ArrayList<>();
        int nroLinea = 1;
        long sumaTotalVenta = 0; 
        
        for (var item : request.getItems()) {
            Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new Exception("Producto no encontrado con ID: " + item.getProductoId()));

            Map<String, Object> detalle = new HashMap<>();
            detalle.put("NroLinDet", nroLinea++);
            detalle.put("NmbItem", producto.getDescripcion());
            detalle.put("QtyItem", item.getCantidad());
            detalle.put("PrcItem", producto.getPrecio());
            
            long montoItem = Math.round(item.getCantidad() * producto.getPrecio());
            detalle.put("MontoItem", montoItem);
            
            sumaTotalVenta += montoItem; 
            detallesSii.add(detalle);
        }

        Map<String, Object> totales = new HashMap<>();
        long mntNeto = Math.round(sumaTotalVenta / 1.19);
        long iva = sumaTotalVenta - mntNeto;
        
        totales.put("MntNeto", mntNeto);
        totales.put("IVA", iva);
        totales.put("MntTotal", sumaTotalVenta); 
        
        encabezado.put("IdDoc", idDoc);
        encabezado.put("Emisor", emisor);
        encabezado.put("Receptor", receptor);
        encabezado.put("Totales", totales); 
        
        Map<String, Object> dte = new HashMap<>();
        dte.put("Encabezado", encabezado);
        dte.put("Detalle", detallesSii);
        
        payload.put("response", List.of("PDF", "TOKEN")); 
        payload.put("dte", dte);
        
        return payload;
    }
}