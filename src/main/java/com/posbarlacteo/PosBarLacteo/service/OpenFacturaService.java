package com.posbarlacteo.PosBarLacteo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.posbarlacteo.PosBarLacteo.dto.PagoRequest;
import com.posbarlacteo.PosBarLacteo.model.ConfiguracionDte;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle; // Usaremos VentaDetalle

@Service
public class OpenFacturaService {

    private final RestTemplate restTemplate;

    public OpenFacturaService() {
        this.restTemplate = new RestTemplate();
    }

    public String emitirBoletaOpenFactura(Venta venta, PagoRequest request, ConfiguracionDte config) {
        String url = "sandbox".equalsIgnoreCase(config.getAmbiente())
                ? "https://dev-api.haulmer.com/v2/dte/issue"
                : "https://api.haulmer.com/v2/dte/issue";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", config.getApiKey());

        Map<String, Object> body = new HashMap<>();
        body.put("response", List.of("PDF"));

        Map<String, Object> dte = new HashMap<>();
        Map<String, Object> encabezado = new HashMap<>();

        Map<String, Object> idDoc = new HashMap<>();
        idDoc.put("TipoDTE", 39); // 39 = Boleta Electrónica
        idDoc.put("Folio", 0); // Haulmer exige que vaya en 0 para asignar el folio real
        idDoc.put("FchEmis", java.time.LocalDate.now().toString()); // Esto genera la fecha en formato AAAA-MM-DD
        
        encabezado.put("IdDoc", idDoc);

        Map<String, Object> emisor = new HashMap<>();
        emisor.put("RUTEmisor", config.getRutEmisor());
        encabezado.put("Emisor", emisor);

        Map<String, Object> receptor = new HashMap<>();
        receptor.put("RUTRecep", "66666666-6");
        receptor.put("RznSocRecep", "Persona Natural");
        encabezado.put("Receptor", receptor);

        Map<String, Object> totales = new HashMap<>();
        totales.put("MntTotal", Math.round(request.getMonto()));
        encabezado.put("Totales", totales);

        dte.put("Encabezado", encabezado);

        List<Map<String, Object>> detalles = new ArrayList<>();
        int nroLinea = 1;
        
        // Corrección: usar los nombres reales de los campos del modelo
        for (VentaDetalle detalle : venta.getDetalles()) {
            Map<String, Object> det = new HashMap<>();
            det.put("NroLinDet", nroLinea++);
            det.put("NmbItem", detalle.getProducto() != null ? 	detalle.getProducto().getDescripcion() : "Item Genérico");
            det.put("QtyItem", detalle.getCantidad());
            det.put("PrcItem", Math.round(detalle.getPrecioUnitario()));
            det.put("MntItem", Math.round(detalle.getPrecioUnitario() * detalle.getCantidad()));
            detalles.add(det);
        }
        
        dte.put("Detalle", detalles);
        body.put("dte", dte);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, 
            HttpMethod.POST, 
            entity, 
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> responseBody = response.getBody();
            if (responseBody.containsKey("pdf")) {
                return (String) responseBody.get("pdf");
            }
        }

        throw new RuntimeException("Error en la respuesta del proveedor DTE: " + response.getStatusCode());
    }
}