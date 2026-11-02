package com.posbarlacteo.PosBarLacteo.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.AuditoriaService;
import com.posbarlacteo.PosBarLacteo.service.DteService;
import com.posbarlacteo.PosBarLacteo.service.VentaDocumentosService;
import com.posbarlacteo.PosBarLacteo.service.VentaService;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final VentaDocumentosService documentos;
    private final DteService dteService;
    private final AuditoriaService auditoria;

    public VentaController(VentaService ventaService, VentaDocumentosService documentos, DteService dteService,
                           AuditoriaService auditoria) {
        this.auditoria = auditoria;
        this.ventaService = ventaService;
        this.documentos = documentos;
        this.dteService = dteService;
    }

    /**
     * Solo ADMIN (ver SecurityConfig). Body: {"motivo": "..."}.
     * Si la venta tenía boleta electrónica se emite la nota de crédito; si eso falla, la anulación igual queda hecha
     * y la nota de crédito queda pendiente de reintento.
     */
    @PostMapping("/{id}/anular")
    public Map<String, Object> anular(@PathVariable Long id, @RequestBody Map<String, String> body) {
        AuthPrincipal p = SecurityUtils.actual();
        Map<String, Object> resultado = new LinkedHashMap<>(ventaService.anular(p, id, body == null ? null : body.get("motivo")));
        auditoria.registrar(p, "VENTA_ANULADA", "Anuló la venta #" + id + (body != null && body.get("motivo") != null ? ". Motivo: " + body.get("motivo") : ""));
        resultado.put("notaCredito", notaCredito(p, id));
        return resultado;
    }

    /** Reintenta la nota de crédito de una venta anulada (solo ADMIN). */
    @PostMapping("/{id}/nota-credito/reintentar")
    public Map<String, Object> reintentarNotaCredito(@PathVariable Long id) {
        return notaCredito(SecurityUtils.actual(), id);
    }

    private Map<String, Object> notaCredito(AuthPrincipal p, Long ventaId) {
        DteService.Emision e;
        try {
            e = dteService.notaCreditoDeVentaAnulada(p, ventaId);
        } catch (RuntimeException ex) {
            e = new DteService.Emision("PENDIENTE", null,
                    "La venta se anuló, pero no se pudo emitir la nota de crédito: " + ex.getMessage(), null);
        }
        return emision(e, true); // con PDF: así el POS imprime la nota de crédito al instante
    }

    /** Copia guardada del PDF 80 mm de la nota de crédito (solo ADMIN): {"pdf": "<base64>"}. */
    @GetMapping("/{id}/nota-credito")
    public Map<String, Object> notaCreditoPdf(@PathVariable Long id) {
        return Map.of("ventaId", id, "pdf", dteService.pdfNotaCredito(SecurityUtils.actual(), id));
    }

    /** Reintenta la boleta pendiente de una venta. forzar=true (solo admin) para una marcada "REVISAR". */
    @PostMapping("/{id}/boleta/reintentar")
    public Map<String, Object> reintentarBoleta(@PathVariable Long id,
                                                @RequestParam(value = "forzar", defaultValue = "false") boolean forzar) {
        return emision(dteService.reintentarBoleta(SecurityUtils.actual(), id, forzar), true);
    }

    /** Copia guardada del PDF 80 mm de la boleta electrónica: {"pdf": "<base64>"}. */
    @GetMapping("/{id}/boleta")
    public Map<String, Object> boleta(@PathVariable Long id) {
        return Map.of("ventaId", id, "pdf", dteService.pdfBoleta(SecurityUtils.actual(), id));
    }

    private static Map<String, Object> emision(DteService.Emision e, boolean conPdf) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estado", e.estado());
        m.put("folio", e.folio());
        m.put("aviso", e.aviso() == null ? "" : e.aviso());
        if (conPdf) {
            m.put("pdf", e.pdf() == null ? "" : e.pdf());
        }
        return m;
    }

    /** Ticket 80 mm para reimprimir: {"pdf": "<base64>"}. */
    @GetMapping("/{id}/ticket")
    public Map<String, Object> ticket(@PathVariable Long id) {
        return Map.of("ventaId", id, "pdf", documentos.ticketBase64(SecurityUtils.actual(), id));
    }

    @GetMapping("/{id}/vale")
    public Map<String, Object> vale(@PathVariable Long id) {
        return Map.of("ventaId", id, "pdf", documentos.valeBase64(SecurityUtils.actual(), id));
    }

    /** Body: {"email": "cliente@correo.cl"}. */
    @PostMapping("/{id}/ticket/enviar")
    public Map<String, Object> enviarTicket(@PathVariable Long id, @RequestBody Map<String, String> body) {
        documentos.enviarTicketPorCorreo(SecurityUtils.actual(), id, body == null ? null : body.get("email"));
        return Map.of("status", "success", "message", "Comprobante enviado por correo.");
    }

    /** Adjunta la foto/PDF del comprobante de transferencia (multipart: file, pagoId opcional). */
    @PostMapping(value = "/{id}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> adjuntarComprobante(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "pagoId", required = false) Long pagoId) {
        Map<String, Object> r = new LinkedHashMap<>(
                documentos.adjuntarComprobante(SecurityUtils.actual(), id, pagoId, file));
        r.put("status", "success");
        return r;
    }

    /** Comprobantes adjuntos con URL temporal (10 min). */
    @GetMapping("/{id}/comprobantes")
    public List<Map<String, Object>> comprobantes(@PathVariable Long id) {
        return documentos.comprobantes(SecurityUtils.actual(), id);
    }
}
