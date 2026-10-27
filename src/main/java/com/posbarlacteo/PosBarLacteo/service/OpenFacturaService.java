package com.posbarlacteo.PosBarLacteo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;

@Service
public class OpenFacturaService {

    private final RestTemplate restTemplate;

    public OpenFacturaService() {
        this.restTemplate = new RestTemplate();
    }

    public String emitirBoletaOpenFactura(Venta venta, PagoRequest request, ConfiguracionDte config) {
        String url = "sandbox".equalsIgnoreCase(config.getAmbiente()) || "desarrollo".equalsIgnoreCase(config.getAmbiente())
                ? "https://dev-api.haulmer.com/v2/dte/document"
                : "https://api.haulmer.com/v2/dte/issue";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", config.getApiKey());

        Map<String, Object> body = new LinkedHashMap<>();
        // Solicitamos el PDF y especificamos la plantilla 80MM
        body.put("response", List.of("PDF", "80MM")); 

        Map<String, Object> dte = new LinkedHashMap<>();
        Map<String, Object> encabezado = new LinkedHashMap<>();

        // -- IdDoc --
        Map<String, Object> idDoc = new LinkedHashMap<>();
        idDoc.put("TipoDTE", 39);                           // 39 = Boleta Electrónica
        idDoc.put("Folio", 0);                              // 0 = Asignación automática por Haulmer
        idDoc.put("FchEmis", LocalDate.now().toString());   // Fecha AAAA-MM-DD
        idDoc.put("IndServicio", 3);                        // 3 = Ventas y servicios
        encabezado.put("IdDoc", idDoc);

        // -- Emisor --
        Map<String, Object> emisor = new LinkedHashMap<>();
        emisor.put("RUTEmisor", config.getRutEmisor());
        encabezado.put("Emisor", emisor);

        // -- Receptor --
        Map<String, Object> receptor = new LinkedHashMap<>();
        receptor.put("RUTRecep", "66666666-6");
        receptor.put("RznSocRecep", "Persona Natural");
        encabezado.put("Receptor", receptor);

        // -- Totales --
        long montoTotal = Math.round(request.getMonto());
        long montoNeto = Math.round(montoTotal / 1.19);
        long iva = montoTotal - montoNeto;

        Map<String, Object> totales = new LinkedHashMap<>();
        totales.put("MntNeto", montoNeto);
        totales.put("IVA", iva);
        totales.put("MntTotal", montoTotal);
        encabezado.put("Totales", totales);

        dte.put("Encabezado", encabezado);

        // -- Detalle --
        List<Map<String, Object>> detalles = new ArrayList<>();
        int nroLinea = 1;

        for (VentaDetalle detalle : venta.getDetalles()) {
            Map<String, Object> det = new LinkedHashMap<>();
            det.put("NroLinDet", nroLinea++);
            det.put("NmbItem", (detalle.getProducto() != null && detalle.getProducto().getDescripcion() != null) 
                    ? detalle.getProducto().getDescripcion() 
                    : "Item Genérico");
            det.put("QtyItem", detalle.getCantidad());
            
            double precioUnit = detalle.getPrecioUnitario() != null ? detalle.getPrecioUnitario() : 0.0;
            det.put("PrcItem", Math.round(precioUnit));
            det.put("MontoItem", Math.round(precioUnit * detalle.getCantidad()));
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
            
            // Inspecciona las claves devueltas en la respuesta
            for (String key : List.of("PDF", "pdf", "80MM", "80mm")) {
                if (responseBody.containsKey(key) && responseBody.get(key) != null) {
                    return (String) responseBody.get(key);
                }
            }

            throw new RuntimeException("Haulmer respondió 200 OK pero no devolvió la clave PDF. Contenido: " + responseBody);
        }

        throw new RuntimeException("Error en la respuesta del proveedor DTE: " + response.getStatusCode());
        //comentario de prueba deploy
    }
}