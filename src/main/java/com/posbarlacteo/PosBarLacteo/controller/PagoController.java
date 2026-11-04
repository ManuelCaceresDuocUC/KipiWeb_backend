package com.posbarlacteo.PosBarLacteo.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.dto.LineaPagoDTO;
import com.posbarlacteo.PosBarLacteo.dto.PagoRequest;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.PagoVenta;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.DteService;
import com.posbarlacteo.PosBarLacteo.service.FiadoCorreoService;
import com.posbarlacteo.PosBarLacteo.util.PdfUnionUtil;
import com.posbarlacteo.PosBarLacteo.service.ValeCreditoPdfService;
import com.posbarlacteo.PosBarLacteo.service.VentaService;

/** Los errores de negocio los traduce GlobalExceptionHandler a {"message": "..."} con el código HTTP correcto. */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final VentaService ventaService;
    private final DteService dteService;
    private final ValeCreditoPdfService valeService;
    private final FiadoCorreoService fiadoCorreo;

    public PagoController(VentaService ventaService, DteService dteService, ValeCreditoPdfService valeService,
                          FiadoCorreoService fiadoCorreo) {
        this.ventaService = ventaService;
        this.dteService = dteService;
        this.valeService = valeService;
        this.fiadoCorreo = fiadoCorreo;
    }

    /** Endpoint principal: una o varias formas de pago (request.pagos) que deben sumar el total. */
    @PostMapping("/venta")
    public ResponseEntity<?> venta(@RequestBody PagoRequest request) {
        return cobrar(request, "Venta registrada correctamente");
    }

    // ---- Endpoints anteriores (un solo medio por el total); se mantienen para versiones antiguas del frontend ----

    @PostMapping("/efectivo")
    public ResponseEntity<?> procesarEfectivo(@RequestBody PagoRequest request) {
        return cobrar(unSoloMedio(request, PagoVenta.EFECTIVO), "Venta en efectivo registrada correctamente");
    }

    @PostMapping("/cobrar")
    public ResponseEntity<?> procesarPagoTarjeta(@RequestBody PagoRequest request) {
        return cobrar(unSoloMedio(request, PagoVenta.TARJETA), "Venta con tarjeta registrada correctamente");
    }

    @PostMapping("/credito")
    public ResponseEntity<?> procesarCredito(@RequestBody PagoRequest request) {
        if (request.getClienteId() == null) {
            throw new NegocioException("El cliente es obligatorio para ventas a crédito.");
        }
        return cobrar(unSoloMedio(request, PagoVenta.CREDITO), "Venta a crédito registrada exitosamente.");
    }

    private PagoRequest unSoloMedio(PagoRequest request, String metodo) {
        LineaPagoDTO linea = new LineaPagoDTO();
        linea.setMetodo(metodo);
        linea.setMonto((long) request.getMonto());
        linea.setConfirmado(true);
        request.setPagos(List.of(linea));
        return request;
    }

    private ResponseEntity<?> cobrar(PagoRequest request, String mensaje) {
        AuthPrincipal p = SecurityUtils.actual();
        // Identidad siempre desde el token: lo que venga en el cuerpo se pisa
        request.setEmpresaId(p.empresaId());
        request.setUsuarioId(p.usuarioId());

        // Reintento del mismo cobro (corte de red, doble clic): se devuelve la venta ya registrada
        Optional<Venta> previa = ventaService.buscarPorClave(p, request.getIdempotencyKey());
        if (previa.isPresent()) {
            return respuestaDuplicada(previa.get());
        }

        Venta venta;
        try {
            venta = ventaService.procesarVenta(request, p);
        } catch (DataIntegrityViolationException e) {
            // Dos intentos simultáneos con la misma clave: gana el primero, el segundo recibe esa venta
            Optional<Venta> dup = ventaService.buscarPorClave(p, request.getIdempotencyKey());
            if (dup.isPresent()) {
                return respuestaDuplicada(dup.get());
            }
            throw e;
        }

        Set<String> metodos = venta.getPagos().stream().map(PagoVenta::getMetodo).collect(Collectors.toSet());
        DteService.Emision doc = dteService.documentoDeVenta(venta, request, metodos, p);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", "success");
        cuerpo.put("message", mensaje);
        cuerpo.put("ventaId", venta.getId());
        cuerpo.put("metodoPago", venta.getMetodoPago());
        cuerpo.put("boletaPdf", doc.pdf() == null ? "" : doc.pdf());
        // Estado de la boleta electrónica: EMITIDO | PENDIENTE | REVISAR | NO_APLICA (venta sin boleta)
        cuerpo.put("dteEstado", doc.estado());
        cuerpo.put("dteFolio", doc.folio());
        cuerpo.put("dteAviso", doc.aviso() == null ? "" : doc.aviso());
        // Venta con parte a crédito: vale con firma para imprimir junto al ticket
        String vale = null;
        if (metodos.contains(PagoVenta.CREDITO)) {
            vale = valeService.generarValeBase64(venta,
                    venta.getEmpresa() != null ? venta.getEmpresa().getRazonSocial() : null);
        }
        cuerpo.put("valePdf", vale == null ? "" : vale);
        // Ticket + vale en un solo PDF: se imprimen en un único trabajo y el vale para firmar nunca se pierde
        String impresion = null;
        if (vale != null && !vale.isBlank()) {
            if (metodos.size() == 1) {
                // Venta 100 % a crédito: el vale ya trae el detalle, el total y la firma. Imprimir además el
                // comprobante de venta duplicaría el papel sin aportar nada.
                impresion = vale;
            } else if (doc.pdf() != null && !doc.pdf().isBlank()) {
                impresion = PdfUnionUtil.unirBase64(List.of(doc.pdf(), vale));
            }
        }
        cuerpo.put("impresionPdf", impresion == null ? "" : impresion);
        // Fiado: el cliente recibe por correo el comprobante de lo que quedó a su nombre (si tiene correo)
        boolean correoFiado = false;
        if (metodos.contains(PagoVenta.CREDITO)) {
            long montoCredito = venta.getPagos().stream()
                    .filter(pg -> PagoVenta.CREDITO.equals(pg.getMetodo()))
                    .mapToLong(pg -> pg.getMonto() == null ? 0L : pg.getMonto()).sum();
            correoFiado = fiadoCorreo.enviarComprobante(venta, montoCredito, vale,
                    request.isFiadoManual() ? request.getNumeroVale() : null);
        }
        cuerpo.put("correoFiadoEnviado", correoFiado);
        cuerpo.put("tieneTransferencia", metodos.contains(PagoVenta.TRANSFERENCIA));
        return ResponseEntity.ok(cuerpo);
    }

    private ResponseEntity<?> respuestaDuplicada(Venta venta) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", "duplicate");
        cuerpo.put("message", "Esta venta ya estaba registrada (N° " + venta.getId() + "). No se cobró dos veces.");
        cuerpo.put("ventaId", venta.getId());
        cuerpo.put("metodoPago", venta.getMetodoPago());
        cuerpo.put("boletaPdf", "");
        cuerpo.put("valePdf", "");
        cuerpo.put("dteEstado", "");
        cuerpo.put("dteAviso", "");
        cuerpo.put("tieneTransferencia", false);
        return ResponseEntity.ok(cuerpo);
    }
}
