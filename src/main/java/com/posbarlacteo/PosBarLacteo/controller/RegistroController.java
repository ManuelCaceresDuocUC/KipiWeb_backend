package com.posbarlacteo.PosBarLacteo.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.posbarlacteo.PosBarLacteo.dto.RegistroEmpresaDTO;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class RegistroController {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${flow.api.key}")
    private String flowApiKey;

    @Value("${flow.secret.key}")
    private String flowSecretKey;

    // Inyectamos las URLs desde el application.properties
    // Se añade un valor por defecto después de los dos puntos (:) por si olvidas configurarlo
    @Value("${app.frontend.url:https://www.kipipos.cl}")
    private String frontendUrl;

    @Value("${app.backend.url:http://localhost:8080}")
    private String backendUrl;

    private final String FLOW_BASE_URL = "https://sandbox.flow.cl/api"; 
    private final String PLAN_ID = "KIPI";

    @PostMapping("/registrar-empresa")
    @Transactional(rollbackFor = Exception.class) 
    public ResponseEntity<?> registrarEmpresaCompleta(@RequestBody RegistroEmpresaDTO data) {
        log.info("Datos recibidos: Correo={}, RazonSocial={}, Rut={}", 
        data.getAdmin().getCorreo(), 
        data.getEmpresa().getRazon_social(), 
        data.getEmpresa().getRut_empresa());
        
        try {
            // 1. Crear cliente en Flow
            String customerId = crearClienteFlow(data.getAdmin().getCorreo(), data.getEmpresa().getRazon_social(), data.getEmpresa().getRut_empresa());

            // 2. Guardar empresa en estado PENDIENTE / INACTIVA
            Empresa nuevaEmpresa = new Empresa();
            nuevaEmpresa.setRazonSocial(data.getEmpresa().getRazon_social());
            nuevaEmpresa.setRutEmpresa(data.getEmpresa().getRut_empresa());
            nuevaEmpresa.setGiro(data.getEmpresa().getGiro());
            nuevaEmpresa.setDireccion(data.getEmpresa().getDireccion());
            nuevaEmpresa.setComuna(data.getEmpresa().getComuna());
            
            nuevaEmpresa.setFlowCustomerId(customerId); 
            nuevaEmpresa.setEstado("PENDIENTE"); 
            nuevaEmpresa.setActivo(false); // <-- NUEVA LÍNEA: 0 en base de datos
            empresaRepository.save(nuevaEmpresa);

            // 3. Guardar el usuario administrador
            Usuario nuevoAdmin = new Usuario();
            nuevoAdmin.setUsuario(data.getAdmin().getUsuario());
            nuevoAdmin.setContrasena(data.getAdmin().getContrasena());
            nuevoAdmin.setCorreo(data.getAdmin().getCorreo()); 
            nuevoAdmin.setRol(data.getAdmin().getRol() != null ? data.getAdmin().getRol() : "admin"); 
            nuevoAdmin.setEmpresa(nuevaEmpresa);
            usuarioRepository.save(nuevoAdmin);

            // 3.5 Guardar empleados adicionales (colaboradores)
            if (data.getEmpleados() != null && !data.getEmpleados().isEmpty()) {
                for (RegistroEmpresaDTO.UsuarioDTO empData : data.getEmpleados()) {
                    Usuario nuevoEmpleado = new Usuario();
                    nuevoEmpleado.setUsuario(empData.getUsuario());
                    nuevoEmpleado.setContrasena(empData.getContrasena());
                    nuevoEmpleado.setCorreo(empData.getUsuario() + "@" + data.getEmpresa().getRut_empresa() + ".local"); 
                    nuevoEmpleado.setRol(empData.getRol() != null ? empData.getRol() : "vendedor");
                    nuevoEmpleado.setEmpresa(nuevaEmpresa);
                    usuarioRepository.save(nuevoEmpleado);
                }
            }

            // 4. Generar URL de pago usando la URL dinámica del Backend
            String urlReturn = backendUrl + "/api/auth/registro-exitoso";
            String urlRegistroTarjeta = generarEnlaceRegistroTarjeta(customerId, urlReturn);

            Map<String, String> response = new HashMap<>();
            response.put("url_pago", urlRegistroTarjeta);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error en proceso de registro: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @RequestMapping(value = "/registro-exitoso", method = {RequestMethod.POST, RequestMethod.GET})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> retornoRegistroFlow(@RequestParam("token") String token) {
        try {
            // 1. Consultar si se registró la tarjeta en Flow
            Map<String, Object> estadoRegistro = consultarEstadoRegistro(token);
            Object statusObj = estadoRegistro.get("status");
            boolean registroTarjetaExitoso = statusObj != null && "1".equals(String.valueOf(statusObj));

            if (registroTarjetaExitoso) {
                String customerId = (String) estadoRegistro.get("customerId");
                
                // 2. Suscribir al cliente y capturar el estado real
                Map<String, Object> suscripcion = suscribirClienteAlPlan(customerId, PLAN_ID);
                Object subStatusObj = suscripcion.get("status");
                String statusStr = String.valueOf(subStatusObj);
                
                // 3. Validar: 1 = Activa, 2 = Trial
                boolean suscripcionValida = "1".equals(statusStr) || "2".equals(statusStr);

                if (suscripcionValida) {
                    Empresa empresa = empresaRepository.findByFlowCustomerId(customerId)
                        .orElseThrow(() -> new RuntimeException("Empresa no encontrada con el ID de Flow"));
                    
                    if (suscripcion.containsKey("subscriptionId")) {
                        empresa.setFlowSubscriptionId((String) suscripcion.get("subscriptionId")); 
                    }
                    
                    empresa.setEstado("ACTIVA");
                    empresa.setActivo(true); // <-- NUEVA LÍNEA: Cambia a 1 en base de datos
                    empresaRepository.save(empresa);
                    
                    log.info("Empresa activada (Trial/Activa). Suscripción ID: {}", suscripcion.get("subscriptionId"));
                    
                    // Redirigir al Frontend Dinámico (Éxito)
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(URI.create(frontendUrl + "/registro-exitoso?status=success"))
                            .build();
                } else {
                    log.warn("Suscripción rechazada o fallida. Estado devuelto: {}", statusStr);
                    // Redirigir al Frontend Dinámico (Fallo de pago)
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(URI.create(frontendUrl + "/registro-exitoso?status=payment_failed"))
                            .build();
                }
            } else {
                // Redirigir al Frontend Dinámico (Error en tarjeta)
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(frontendUrl + "/registro-exitoso?status=error"))
                        .build();
            }
        } catch (Exception e) {
            log.error("Error validando el retorno de Flow: ", e);
            // Redirigir al Frontend Dinámico (Excepción)
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/registro-exitoso?status=error"))
                    .build();
        }
    }

    private String crearClienteFlow(String email, String nombre, String rutId) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("apiKey", flowApiKey);
        params.put("name", nombre);
        params.put("email", email);
        params.put("externalId", rutId); 

        Map<String, Object> responseMap = enviarPeticionFlow("/customer/create", params);
        
        if (responseMap.containsKey("customerId")) {
            return (String) responseMap.get("customerId");
        } else {
            throw new RuntimeException("Error al crear cliente en Flow: " + responseMap);
        }
    }

    private String generarEnlaceRegistroTarjeta(String customerId, String urlReturn) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("apiKey", flowApiKey);
        params.put("customerId", customerId);
        params.put("url_return", urlReturn); 

        Map<String, Object> responseMap = enviarPeticionFlow("/customer/register", params);
        
        if (responseMap.containsKey("url") && responseMap.containsKey("token")) {
            return responseMap.get("url") + "?token=" + responseMap.get("token");
        } else {
            throw new RuntimeException("Error al generar enlace de registro: " + responseMap);
        }
    }

    private Map<String, Object> suscribirClienteAlPlan(String customerId, String planId) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("apiKey", flowApiKey);
        params.put("customerId", customerId);
        params.put("planId", planId);

        return enviarPeticionFlow("/subscription/create", params);
    }

    private Map<String, Object> consultarEstadoRegistro(String token) throws Exception {
        String cleanApiKey = flowApiKey.trim();
        String cleanSecretKey = flowSecretKey.trim();

        Map<String, String> params = new TreeMap<>();
        params.put("apiKey", cleanApiKey);
        params.put("token", token);

        StringBuilder dataToSign = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            dataToSign.append(entry.getKey()).append(entry.getValue());
        }

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(cleanSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(dataToSign.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        params.put("s", hexString.toString());

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(FLOW_BASE_URL + "/customer/getRegisterStatus");
        params.forEach(builder::queryParam);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    builder.toUriString(), HttpMethod.GET, null, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Rechazo de Flow GET (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }
    }

    private Map<String, Object> enviarPeticionFlow(String endpoint, Map<String, String> params) throws Exception {
        String cleanApiKey = flowApiKey.trim();
        String cleanSecretKey = flowSecretKey.trim();

        params.put("apiKey", cleanApiKey);
        
        StringBuilder dataToSign = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            dataToSign.append(entry.getKey()).append(entry.getValue());
        }

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(cleanSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(dataToSign.toString().getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        params.put("s", hexString.toString()); 

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            map.add(entry.getKey(), entry.getValue());
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    FLOW_BASE_URL + endpoint, HttpMethod.POST, request, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            String errorRealFlow = e.getResponseBodyAsString();
            throw new RuntimeException("Rechazo de Flow (" + e.getStatusCode() + "): " + errorRealFlow);
        }
    }

    private Map<String, Object> consultarEstadoSuscripcion(String subscriptionId) throws Exception {
        String cleanApiKey = flowApiKey.trim();
        String cleanSecretKey = flowSecretKey.trim();

        Map<String, String> params = new TreeMap<>();
        params.put("apiKey", cleanApiKey);
        params.put("subscriptionId", subscriptionId); 

        StringBuilder dataToSign = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            dataToSign.append(entry.getKey()).append(entry.getValue());
        }

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(cleanSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(dataToSign.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        params.put("s", hexString.toString());

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(FLOW_BASE_URL + "/subscription/get");
        params.forEach(builder::queryParam);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    builder.toUriString(), HttpMethod.GET, null, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Error consultando suscripción (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }
    }
}