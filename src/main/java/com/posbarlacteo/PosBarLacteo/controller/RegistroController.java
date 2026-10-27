package com.posbarlacteo.PosBarLacteo.controller;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
import com.posbarlacteo.PosBarLacteo.exception.ConflictoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.ServicioExternoException;
import com.posbarlacteo.PosBarLacteo.security.Roles;
import com.posbarlacteo.PosBarLacteo.util.RutUtils;
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

    @Value("${flow.base-url:https://sandbox.flow.cl/api}")
    private String flowBaseUrl;

    private static final String PLAN_ID = "KIPI";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate = crearRestTemplate();

    private static RestTemplate crearRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return new RestTemplate(factory);
    }

    // ------------------------------------------------------------------
    // REGISTRO
    // ------------------------------------------------------------------
    @PostMapping("/registrar-empresa")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> registrarEmpresaCompleta(@RequestBody RegistroEmpresaDTO data) {
        // Validar TODO antes de crear nada en Flow (evita clientes huérfanos)
        validarRegistro(data);
        String rutEmpresa = RutUtils.normalizar(data.getEmpresa().getRut_empresa());
        log.info("Registro recibido: razonSocial={}, rut={}", data.getEmpresa().getRazon_social(), rutEmpresa);

        // 1. Crear cliente en Flow
        String customerId = crearClienteFlow(
                data.getAdmin().getCorreo(),
                data.getEmpresa().getRazon_social(),
                rutEmpresa);

        // 2. Obtener primera sucursal para la dirección principal de la empresa
        RegistroEmpresaDTO.SucursalDTO primeraSucursal = (data.getSucursales() != null && !data.getSucursales().isEmpty())
                ? data.getSucursales().get(0)
                : null;

        Empresa nuevaEmpresa = new Empresa();
        nuevaEmpresa.setRazonSocial(data.getEmpresa().getRazon_social());
        nuevaEmpresa.setRutEmpresa(rutEmpresa);
        nuevaEmpresa.setGiro(data.getEmpresa().getGiro());

        if (primeraSucursal != null) {
            nuevaEmpresa.setDireccion(primeraSucursal.getDireccion());
            nuevaEmpresa.setComuna(primeraSucursal.getComuna());
        }

        nuevaEmpresa.setFlowCustomerId(customerId);
        nuevaEmpresa.setEstado("PENDIENTE");
        nuevaEmpresa.setActivo(false);
        empresaRepository.save(nuevaEmpresa);

        // ✨ 2.5 CREAR TODAS LAS SUCURSALES REGISTRADAS ✨
        Map<String, Sucursal> sucursalesGuardadas = new HashMap<>();
        Sucursal sucursalMatriz = null;

        if (data.getSucursales() != null && !data.getSucursales().isEmpty()) {
            for (int i = 0; i < data.getSucursales().size(); i++) {
                RegistroEmpresaDTO.SucursalDTO sucDto = data.getSucursales().get(i);

                Sucursal sucursal = new Sucursal();
                sucursal.setNombre(sucDto.getNombre());
                sucursal.setDireccion(sucDto.getDireccion());
                sucursal.setComuna(sucDto.getComuna());
                sucursal.setEsCasaMatriz(i == 0); // La primera sucursal ingresada es Casa Matriz
                sucursal.setEmpresa(nuevaEmpresa);
                sucursal.setActivo(true);

                Sucursal guardada = sucursalRepository.save(sucursal);
                sucursalesGuardadas.put(guardada.getNombre(), guardada);

                if (i == 0) {
                    sucursalMatriz = guardada;
                }
            }
        }

        // 3. Usuario administrador (asociado a la Casa Matriz)
        Usuario nuevoAdmin = new Usuario();
        nuevoAdmin.setUsuario(data.getAdmin().getUsuario().trim());
        nuevoAdmin.setContrasena(passwordEncoder.encode(data.getAdmin().getContrasena()));
        nuevoAdmin.setCorreo(data.getAdmin().getCorreo());
        nuevoAdmin.setRol(Roles.ADMIN); // el primer usuario siempre es administrador
        nuevoAdmin.setEmpresa(nuevaEmpresa);
        nuevoAdmin.setSucursal(sucursalMatriz);
        usuarioRepository.save(nuevoAdmin);

        // ✨ 3.5 Empleados adicionales asignados dinámicamente a sus sucursales ✨
        if (data.getEmpleados() != null && !data.getEmpleados().isEmpty()) {
            for (RegistroEmpresaDTO.UsuarioDTO empData : data.getEmpleados()) {
                Usuario nuevoEmpleado = new Usuario();
                nuevoEmpleado.setUsuario(empData.getUsuario().trim());
                nuevoEmpleado.setContrasena(passwordEncoder.encode(empData.getContrasena()));
                nuevoEmpleado.setCorreo(empData.getUsuario().trim() + "@" + rutEmpresa + ".local");
                nuevoEmpleado.setRol(Roles.normalizar(empData.getRol()));
                nuevoEmpleado.setEmpresa(nuevaEmpresa);

                // Buscar sucursal por el nombre recibido desde el frontend
                Sucursal sucursalAsignada = null;
                if (empData.getSucursalNombre() != null) {
                    sucursalAsignada = sucursalesGuardadas.get(empData.getSucursalNombre());
                }
                if (sucursalAsignada == null) {
                    sucursalAsignada = sucursalMatriz; // Fallback
                }

                nuevoEmpleado.setSucursal(sucursalAsignada);
                usuarioRepository.save(nuevoEmpleado);
            }
        }

        // 4. URL de registro de tarjeta Flow
        String urlReturn = backendUrl + "/api/auth/registro-exitoso";
        String urlRegistroTarjeta = generarEnlaceRegistroTarjeta(customerId, urlReturn);

        Map<String, String> response = new HashMap<>();
        response.put("url_pago", urlRegistroTarjeta);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    // ------------------------------------------------------------------
    // VALIDACIÓN DE REGISTRO
    // ------------------------------------------------------------------
    private void validarRegistro(RegistroEmpresaDTO data) {
        if (data == null || data.getEmpresa() == null || data.getAdmin() == null) {
            throw new NegocioException("Faltan datos de la empresa o del administrador.");
        }
        var emp = data.getEmpresa();
        var admin = data.getAdmin();

        if (emp.getRazon_social() == null || emp.getRazon_social().isBlank()) {
            throw new NegocioException("La razón social es obligatoria.");
        }
        if (!RutUtils.esValido(emp.getRut_empresa())) {
            throw new NegocioException("El RUT de la empresa no es válido.");
        }
        String rut = RutUtils.normalizar(emp.getRut_empresa());
        if (empresaRepository.findByRutEmpresa(rut).isPresent()) {
            throw new ConflictoException("Ya existe una empresa registrada con ese RUT. Si no terminaste el pago, contáctanos.");
        }
        if (admin.getCorreo() == null || !admin.getCorreo().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new NegocioException("El correo del administrador no es válido.");
        }
        validarCredenciales(admin.getUsuario(), admin.getContrasena());

        if (data.getSucursales() == null || data.getSucursales().isEmpty()) {
            throw new NegocioException("Debes registrar al menos una sucursal.");
        }
        Set<String> sucursales = new HashSet<>();
        for (var suc : data.getSucursales()) {
            if (suc.getNombre() == null || suc.getNombre().isBlank()) {
                throw new NegocioException("Todas las sucursales deben tener nombre.");
            }
            if (!sucursales.add(suc.getNombre())) {
                throw new NegocioException("Hay sucursales con el mismo nombre: " + suc.getNombre());
            }
        }

        // Nombres de usuario únicos globalmente (evita ambigüedad de login entre empresas)
        Set<String> usuarios = new HashSet<>();
        usuarios.add(admin.getUsuario().trim().toLowerCase());
        if (usuarioRepository.existsByUsuario(admin.getUsuario().trim())) {
            throw new ConflictoException("El nombre de usuario \"" + admin.getUsuario().trim() + "\" ya está en uso.");
        }
        if (data.getEmpleados() != null) {
            for (var e : data.getEmpleados()) {
                validarCredenciales(e.getUsuario(), e.getContrasena());
                String nombre = e.getUsuario().trim();
                if (!usuarios.add(nombre.toLowerCase()) || usuarioRepository.existsByUsuario(nombre)) {
                    throw new ConflictoException("El nombre de usuario \"" + nombre + "\" ya está en uso.");
                }
            }
        }
    }

    private void validarCredenciales(String usuario, String contrasena) {
        if (usuario == null || usuario.trim().length() < 3) {
            throw new NegocioException("El nombre de usuario debe tener al menos 3 caracteres.");
        }
        if (contrasena == null || contrasena.length() < 8) {
            throw new NegocioException("La contraseña debe tener al menos 8 caracteres.");
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

            Empresa yaRegistrada = empresaRepository.findByFlowCustomerId(customerId).orElse(null);
            if (yaRegistrada != null && "ACTIVA".equals(yaRegistrada.getEstado())
                    && yaRegistrada.getFlowSubscriptionId() != null) {
                // Recarga del retorno de Flow: no se crea una segunda suscripción
                return redirigir("success");
            }

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
        throw new ServicioExternoException("No se pudo crear tu cuenta de pagos. Inténtalo más tarde.");
    }

    private String generarEnlaceRegistroTarjeta(String customerId, String urlReturn) {
        Map<String, String> params = new TreeMap<>();
        params.put("customerId", customerId);
        params.put("url_return", urlReturn);

        Map<String, Object> responseMap = flowPost("/customer/register", params);

        if (responseMap != null && responseMap.containsKey("url") && responseMap.containsKey("token")) {
            return responseMap.get("url") + "?token=" + responseMap.get("token");
        }
        throw new ServicioExternoException("No se pudo generar el enlace de pago. Inténtalo más tarde.");
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
        params.put("s", firmar(params));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        params.forEach(body::add);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    flowBaseUrl + endpoint, HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Flow POST {} -> {} | body: [{}]", endpoint, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServicioExternoException("El sistema de pagos rechazó la solicitud. Revisa los datos e inténtalo nuevamente.");
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("Flow {} no disponible: {}", endpoint, e.getMessage());
            throw new ServicioExternoException("No se pudo conectar con el sistema de pagos. Inténtalo más tarde.");
        }
    }

    private Map<String, Object> flowGet(String endpoint, Map<String, String> params) {
        params.put("apiKey", flowApiKey.trim());
        params.put("s", firmar(params));

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(flowBaseUrl + endpoint);
        params.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();

        try {
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(uri, HttpMethod.GET, null, MAP_TYPE);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Flow GET {} -> {} | body: [{}]", endpoint, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServicioExternoException("No se pudo consultar el sistema de pagos. Inténtalo más tarde.");
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("Flow {} no disponible: {}", endpoint, e.getMessage());
            throw new ServicioExternoException("No se pudo conectar con el sistema de pagos. Inténtalo más tarde.");
        }
    }

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