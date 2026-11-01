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

import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.VentaDocumentosService;
import com.posbarlacteo.PosBarLacteo.service.VentaService;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final VentaDocumentosService documentos;

    public VentaController(VentaService ventaService, VentaDocumentosService documentos) {
        this.ventaService = ventaService;
        this.documentos = documentos;
    }

    /** Solo ADMIN (ver SecurityConfig). Body: {"motivo": "..."}. */
    @PostMapping("/{id}/anular")
    public Map<String, Object> anular(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ventaService.anular(SecurityUtils.actual(), id, body == null ? null : body.get("motivo"));
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
