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
        String base64Pdf = dteService.obtenerDocumentoVenta(venta, request, metodos);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", "success");
        cuerpo.put("message", mensaje);
        cuerpo.put("ventaId", venta.getId());
        cuerpo.put("metodoPago", venta.getMetodoPago());
        cuerpo.put("boletaPdf", base64Pdf == null ? "" : base64Pdf);
        return ResponseEntity.ok(cuerpo);
    }

    private ResponseEntity<?> respuestaDuplicada(Venta venta) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", "duplicate");
        cuerpo.put("message", "Esta venta ya estaba registrada (N° " + venta.getId() + "). No se cobró dos veces.");
        cuerpo.put("ventaId", venta.getId());
        cuerpo.put("metodoPago", venta.getMetodoPago());
        cuerpo.put("boletaPdf", "");
        return ResponseEntity.ok(cuerpo);
    }
}
