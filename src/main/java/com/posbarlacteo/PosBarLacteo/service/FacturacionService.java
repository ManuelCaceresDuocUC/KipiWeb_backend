package com.posbarlacteo.PosBarLacteo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class FacturacionService {

    private final RestClient restClient;

    public FacturacionService(RestClient haulmerRestClient) {
        this.restClient = haulmerRestClient;
    }

    public Map<String, Object> emitirBoleta(Map<String, Object> dtePayload) {
        
        // Genera un código único para evitar boletas duplicadas
        String idempotencyKey = UUID.randomUUID().toString();

        // 1. Guardamos la respuesta en una variable en vez de hacer return directo
        Map<String, Object> respuesta = restClient.post()
                .uri("/v2/dte/document")
                .header("Idempotency-Key", idempotencyKey)
                .body(dtePayload)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
                
        // ✨ 2. AQUÍ LLAMAMOS AL MÉTODO PARA GUARDAR EL PDF ✨
        if (respuesta != null && respuesta.containsKey("PDF")) {
            String pdfBase64 = (String) respuesta.get("PDF");
            try {
                // Le damos un nombre único basado en la fecha/hora actual
                String nombreArchivo = "Boleta_" + System.currentTimeMillis();
                guardarPdfEnPC(pdfBase64, nombreArchivo);
            } catch (IOException e) {
                System.out.println("No se pudo guardar el archivo en el PC: " + e.getMessage());
            }
        }

        // 3. Retornamos la respuesta para que tu Controlador la reciba normal
        return respuesta;
    }
    
    public Map<String, Object> anularDocumento(int dte, int folio, String fecha) {
        Map<String, Object> body = Map.of(
            "Dte", dte,
            "Folio", folio,
            "Fecha", fecha
        );

        return restClient.post()
                .uri("/v2/dte/anularDTE52")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public void guardarPdfEnPC(String base64String, String nombreArchivo) throws IOException {
        // 1. Limpiar el string por si acaso viene con espacios o saltos de línea
        String cleanBase64 = base64String.trim().replace("\n", "").replace("\r", "");

        // 2. Decodificar la cadena Base64 a un arreglo de bytes (el archivo binario real)
        byte[] pdfBytes = Base64.getDecoder().decode(cleanBase64);

        // 3. Definir la ruta donde quieres guardarlo (ejemplo en Windows o Linux/Mac)
        String rutaDestino = "C:/Users/manue/Desktop/" + nombreArchivo + ".pdf"; 
        Path path = Paths.get(rutaDestino);

        // 4. Crear los directorios si no existen y escribir el archivo
        Files.createDirectories(path.getParent());
        Files.write(path, pdfBytes);

        System.out.println("¡Boleta guardada con éxito en: " + rutaDestino);
    }
}