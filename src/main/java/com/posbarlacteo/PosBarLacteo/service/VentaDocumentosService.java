package com.posbarlacteo.PosBarLacteo.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.PagoVenta;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.repository.PagoVentaRepository;
import com.posbarlacteo.PosBarLacteo.repository.VentaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/** Documentos de una venta ya registrada: reimprimir ticket/vale, enviarlo por correo y adjuntar comprobantes. */
@Service
public class VentaDocumentosService {

    private final VentaRepository ventaRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final VoucherService voucherService;
    private final ValeCreditoPdfService valeService;
    private final ComprobanteService comprobanteService;
    private final MailService mailService;

    public VentaDocumentosService(VentaRepository ventaRepository, PagoVentaRepository pagoVentaRepository,
                                  VoucherService voucherService, ValeCreditoPdfService valeService,
                                  ComprobanteService comprobanteService, MailService mailService) {
        this.ventaRepository = ventaRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.voucherService = voucherService;
        this.valeService = valeService;
        this.comprobanteService = comprobanteService;
        this.mailService = mailService;
    }

    private Venta venta(AuthPrincipal p, Long id) {
        return ventaRepository.findByIdAndEmpresaId(id, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada."));
    }

    /** Ticket 80 mm (comprobante interno de la venta) en base64. */
    @Transactional(readOnly = true)
    public String ticketBase64(AuthPrincipal p, Long ventaId) {
        Venta v = venta(p, ventaId);
        try {
            return voucherService.generarVoucherBase64(v, null);
        } catch (Exception e) {
            throw new NegocioException("No se pudo generar el ticket de la venta.");
        }
    }

    @Transactional(readOnly = true)
    public String valeBase64(AuthPrincipal p, Long ventaId) {
        Venta v = venta(p, ventaId);
        boolean fiado = v.getPagos() != null && v.getPagos().stream().anyMatch(x -> PagoVenta.CREDITO.equals(x.getMetodo()));
        if (!fiado) {
            throw new NegocioException("Esta venta no tiene pago a crédito.");
        }
        String pdf = valeService.generarValeBase64(v, v.getEmpresa() != null ? v.getEmpresa().getRazonSocial() : null);
        if (pdf == null) {
            throw new NegocioException("No se pudo generar el vale de crédito.");
        }
        return pdf;
    }

    @Transactional(readOnly = true)
    public void enviarTicketPorCorreo(AuthPrincipal p, Long ventaId, String email) {
        String destino = MailService.validarDestino(email);
        Venta v = venta(p, ventaId);
        String empresa = v.getEmpresa() != null ? v.getEmpresa().getRazonSocial() : "KIPIPOS";
        byte[] pdf;
        try {
            pdf = Base64.getDecoder().decode(voucherService.generarVoucherBase64(v, null));
        } catch (Exception e) {
            throw new NegocioException("No se pudo generar el comprobante para enviar.");
        }
        mailService.enviarConAdjunto(destino, empresa,
                "Comprobante de tu compra N° " + v.getId() + " - " + empresa,
                "Hola,\n\nAdjuntamos el comprobante de tu compra N° " + v.getId() + " en " + empresa
                        + ".\n\n¡Gracias por tu preferencia!",
                "comprobante-venta-" + v.getId() + ".pdf", pdf);
    }

    /**
     * Adjunta (o reemplaza) la imagen/PDF del comprobante de una transferencia. Sin pagoId se usa la
     * primera transferencia de la venta. No es transaccional a propósito: no se retiene una conexión
     * de base de datos mientras se sube el archivo.
     */
    public Map<String, Object> adjuntarComprobante(AuthPrincipal p, Long ventaId, Long pagoId, MultipartFile archivo) {
        Venta v = venta(p, ventaId);
        if ("ANULADA".equals(v.getEstado())) {
            throw new NegocioException("La venta está anulada.");
        }
        PagoVenta pago = elegirTransferencia(ventaId, pagoId);
        String anterior = pago.getComprobanteKey();

        ComprobanteService.Archivo subido = comprobanteService.subir(p.empresaId(), ventaId, archivo);
        pago.setComprobanteKey(subido.key());
        pago.setComprobanteNombre(subido.nombre());
        pago.setComprobanteTipo(subido.tipo());
        pagoVentaRepository.save(pago);
        if (anterior != null && !anterior.equals(subido.key())) {
            comprobanteService.eliminar(anterior);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pagoId", pago.getId());
        r.put("nombre", subido.nombre());
        r.put("tipo", subido.tipo());
        return r;
    }

    /** URLs temporales de los comprobantes adjuntos de la venta. */
    public List<Map<String, Object>> comprobantes(AuthPrincipal p, Long ventaId) {
        venta(p, ventaId); // valida que la venta sea de la empresa
        List<Map<String, Object>> salida = new ArrayList<>();
        for (PagoVenta pago : pagoVentaRepository.porVenta(ventaId)) {
            if (pago.tieneComprobante()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("pagoId", pago.getId());
                f.put("metodo", pago.getMetodo());
                f.put("nombre", pago.getComprobanteNombre());
                f.put("tipo", pago.getComprobanteTipo());
                f.put("url", comprobanteService.urlTemporal(pago.getComprobanteKey()));
                salida.add(f);
            }
        }
        return salida;
    }

    private PagoVenta elegirTransferencia(Long ventaId, Long pagoId) {
        List<PagoVenta> pagos = pagoVentaRepository.porVenta(ventaId);
        for (PagoVenta pago : pagos) {
            boolean coincide = pagoId == null ? PagoVenta.TRANSFERENCIA.equals(pago.getMetodo()) : pago.getId().equals(pagoId);
            if (coincide) {
                if (!PagoVenta.TRANSFERENCIA.equals(pago.getMetodo())) {
                    throw new NegocioException("Solo se adjuntan comprobantes a pagos por transferencia.");
                }
                return pago;
            }
        }
        throw new NegocioException("La venta no tiene un pago por transferencia.");
    }
}
