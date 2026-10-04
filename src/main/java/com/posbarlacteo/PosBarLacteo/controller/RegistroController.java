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
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.posbarlacteo.PosBarLacteo.model.Sucursal;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.SucursalRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final SucursalRepository sucursalRepository;
    @Value("${flow.api.key}")
    private String flowApiKey;

    @Value("${flow.secret.key}")
    private String flowSecretKey;

    @Value("${app.frontend.url:https://www.kipipos.cl}")
    private String frontendUrl;

    @Value("${app.backend.url:http://localhost:8080}")
    private String backendUrl;

    private static final String FLOW_BASE_URL = "https://sandbox.flow.cl/api";
    private static final String PLAN_ID = "KIPI";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate = new RestTemplate();

    // ------------------------------------------------------------------
    // REGISTRO
    // ------------------------------------------------------------------
    @PostMapping("/registrar-empresa")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> registrarEmpresaCompleta(@RequestBody RegistroEmpresaDTO data) {
        log.info("Registro recibido: correo={}, razonSocial={}, rut={}",
                data.getAdmin().getCorreo(),
                data.getEmpresa().getRazon_social(),
                data.getEmpresa().getRut_empresa());

        try {
            // 1. Crear cliente en Flow
            String customerId = crearClienteFlow(
                    data.getAdmin().getCorreo(),
                    data.getEmpresa().getRazon_social(),
                    data.getEmpresa().getRut_empresa());

            // 2. Guardar empresa PENDIENTE / INACTIVA
            Empresa nuevaEmpresa = new Empresa();
            nuevaEmpresa.setRazonSocial(data.getEmpresa().getRazon_social());
            nuevaEmpresa.setRutEmpresa(data.getEmpresa().getRut_empresa());
            nuevaEmpresa.setGiro(data.getEmpresa().getGiro());
            
            // 👇 CORRECCIÓN: Leemos la dirección y comuna desde la Sucursal que viene en el DTO
            nuevaEmpresa.setDireccion(data.getSucursal().getDireccion());
            nuevaEmpresa.setComuna(data.getSucursal().getComuna());
            
            nuevaEmpresa.setFlowCustomerId(customerId);
            nuevaEmpresa.setEstado("PENDIENTE");
            nuevaEmpresa.setActivo(false);
            empresaRepository.save(nuevaEmpresa);
            // ✨ 2.5 CREAR SUCURSAL PRINCIPAL (CASA MATRIZ) ✨
            Sucursal sucursalMatriz = new Sucursal();
            // Ahora leemos los datos desde el objeto sucursal del DTO:
            sucursalMatriz.setNombre(data.getSucursal().getNombre()); 
            sucursalMatriz.setDireccion(data.getSucursal().getDireccion());
            sucursalMatriz.setComuna(data.getSucursal().getComuna());
            
            sucursalMatriz.setEsCasaMatriz(true);
            sucursalMatriz.setEmpresa(nuevaEmpresa);
            sucursalMatriz.setActivo(true);
            sucursalRepository.save(sucursalMatriz);

            // 3. Usuario administrador
            Usuario nuevoAdmin = new Usuario();
            nuevoAdmin.setUsuario(data.getAdmin().getUsuario());
            nuevoAdmin.setContrasena(passwordEncoder.encode(data.getAdmin().getContrasena()));
            nuevoAdmin.setCorreo(data.getAdmin().getCorreo());
            nuevoAdmin.setRol(data.getAdmin().getRol() != null ? data.getAdmin().getRol() : "admin");
            nuevoAdmin.setEmpresa(nuevaEmpresa);
            nuevoAdmin.setSucursal(sucursalMatriz);
            usuarioRepository.save(nuevoAdmin);

            // 3.5 Empleados adicionales
            if (data.getEmpleados() != null && !data.getEmpleados().isEmpty()) {
                for (RegistroEmpresaDTO.UsuarioDTO empData : data.getEmpleados()) {
                    Usuario nuevoEmpleado = new Usuario();
                    nuevoEmpleado.setUsuario(empData.getUsuario());
                    nuevoEmpleado.setContrasena(passwordEncoder.encode(empData.getContrasena()));
                    nuevoEmpleado.setCorreo(empData.getUsuario() + "@" + data.getEmpresa().getRut_empresa() + ".local");
                    nuevoEmpleado.setRol(empData.getRol() != null ? empData.getRol() : "vendedor");
                    nuevoEmpleado.setEmpresa(nuevaEmpresa);
                    nuevoEmpleado.setSucursal(sucursalMatriz);
                    usuarioRepository.save(nuevoEmpleado);
                }
            }

            // 4. URL de registro de tarjeta
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

    // ------------------------------------------------------------------
    // RETORNO DESDE FLOW
    // ------------------------------------------------------------------
    @RequestMapping(value = "/registro-exitoso", method = {RequestMethod.POST, RequestMethod.GET})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> retornoRegistroFlow(@RequestParam("token") String token) {
        try {
            Map<String, Object> estadoRegistro = consultarEstadoRegistro(token);
            Object statusObj = estadoRegistro.get("status");
            boolean registroTarjetaExitoso = statusObj != null && "1".equals(String.valueOf(statusObj));

            if (!registroTarjetaExitoso) {
                return redirigir("error");
            }

            String customerId = (String) estadoRegistro.get("customerId");

            Map<String, Object> suscripcion = suscribirClienteAlPlan(customerId, PLAN_ID);
            String statusStr = String.valueOf(suscripcion.get("status"));

            // 1 = Activa, 2 = Trial
            boolean suscripcionValida = "1".equals(statusStr) || "2".equals(statusStr);

            if (!suscripcionValida) {
                log.warn("Suscripción rechazada o fallida. Estado: {}", statusStr);
                return redirigir("payment_failed");
            }

            Empresa empresa = empresaRepository.findByFlowCustomerId(customerId)
                    .orElseThrow(() -> new RuntimeException("Empresa no encontrada con el ID de Flow"));

            if (suscripcion.containsKey("subscriptionId")) {
                empresa.setFlowSubscriptionId(String.valueOf(suscripcion.get("subscriptionId")));
            }
            empresa.setEstado("ACTIVA");
            empresa.setActivo(true);
            empresaRepository.save(empresa);

            log.info("Empresa activada. Suscripción ID: {}", suscripcion.get("subscriptionId"));
            return redirigir("success");

        } catch (Exception e) {
            log.error("Error validando el retorno de Flow: ", e);
            return redirigir("error");
        }
    }

    private ResponseEntity<?> redirigir(String status) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/registro-exitoso?status=" + status))
                .build();
    }

    // ------------------------------------------------------------------
    // LLAMADAS A FLOW
    // ------------------------------------------------------------------
    private String crearClienteFlow(String email, String nombre, String rutId) {
        Map<String, String> params = new TreeMap<>();
        params.put("name", nombre);
        params.put("email", email);
        params.put("externalId", rutId);

        Map<String, Object> responseMap = flowPost("/customer/create", params);

        if (responseMap != null && responseMap.containsKey("customerId")) {
            return String.valueOf(responseMap.get("customerId"));
        }
        throw new RuntimeException("Error al crear cliente en Flow: " + responseMap);
    }

    private String generarEnlaceRegistroTarjeta(String customerId, String urlReturn) {
        Map<String, String> params = new TreeMap<>();
        params.put("customerId", customerId);
        params.put("url_return", urlReturn);

        Map<String, Object> responseMap = flowPost("/customer/register", params);

        if (responseMap != null && responseMap.containsKey("url") && responseMap.containsKey("token")) {
            return responseMap.get("url") + "?token=" + responseMap.get("token");
        }
        throw new RuntimeException("Error al generar enlace de registro: " + responseMap);
    }

    private Map<String, Object> suscribirClienteAlPlan(String customerId, String planId) {
        Map<String, String> params = new TreeMap<>();
        params.put("customerId", customerId);
        params.put("planId", planId);
        return flowPost("/subscription/create", params);
    }

    private Map<String, Object> consultarEstadoRegistro(String token) {
        Map<String, String> params = new TreeMap<>();
        params.put("token", token);
        return flowGet("/customer/getRegisterStatus", params);
    }

    private Map<String, Object> consultarEstadoSuscripcion(String subscriptionId) {
        Map<String, String> params = new TreeMap<>();
        params.put("subscriptionId", subscriptionId);
        return flowGet("/subscription/get", params);
    }

    // ------------------------------------------------------------------
    // HTTP + FIRMA
    // ------------------------------------------------------------------
    private Map<String, Object> flowPost(String endpoint, Map<String, String> params) {
        params.put("apiKey", flowApiKey.trim());
        params.put("s", firmar(params)); // se firma antes de insertar "s"

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        params.forEach(body::add);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    FLOW_BASE_URL + endpoint, HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Flow POST {} -> {} | body: [{}]", endpoint, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Rechazo de Flow (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }
    }

    private Map<String, Object> flowGet(String endpoint, Map<String, String> params) {
        params.put("apiKey", flowApiKey.trim());
        params.put("s", firmar(params));

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(FLOW_BASE_URL + endpoint);
        params.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();

        try {
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(uri, HttpMethod.GET, null, MAP_TYPE);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Flow GET {} -> {} | body: [{}]", endpoint, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Rechazo de Flow GET (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }
    }

    /**
     * Firma HMAC-SHA256 de Flow: parámetros ordenados alfabéticamente (TreeMap),
     * concatenando nombre+valor sin separadores. No debe incluir "s".
     */
    private String firmar(Map<String, String> params) {
        try {
            StringBuilder toSign = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if ("s".equals(e.getKey())) continue;
                toSign.append(e.getKey()).append(e.getValue());
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(flowSecretKey.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(toSign.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error firmando petición a Flow", e);
        }
    }
}