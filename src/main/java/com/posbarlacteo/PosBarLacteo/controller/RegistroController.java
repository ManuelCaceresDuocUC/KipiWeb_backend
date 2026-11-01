package com.posbarlacteo.PosBarLacteo.controller;

import java.net.URI;
import java.util.HashSet;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Map;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
import com.posbarlacteo.PosBarLacteo.exception.LimiteExcedidoException;
import com.posbarlacteo.PosBarLacteo.security.LimiteSolicitudes;
import com.posbarlacteo.PosBarLacteo.service.FlowClient;
import com.posbarlacteo.PosBarLacteo.service.MailService;
import com.posbarlacteo.PosBarLacteo.service.VerificacionCorreoService;

import jakarta.servlet.http.HttpServletRequest;

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

    private final FlowClient flow;
    private final VerificacionCorreoService verificacionCorreo;
    private final LimiteSolicitudes limite;

    @Value("${legal.terminos-version:2026-11-01}")
    private String terminosVigente;

    @Value("${app.frontend.url:https://www.kipipos.cl}")
    private String frontendUrl;

    @Value("${app.backend.url:http://localhost:8080}")
    private String backendUrl;

    /** Minutos tras los cuales un registro sin pagar se considera abandonado y se puede reemplazar. */
    private static final long MINUTOS_REGISTRO_ABANDONADO = 30;

    private static final String PLAN_ID = "KIPI";

    // ------------------------------------------------------------------
    // REGISTRO
    // ------------------------------------------------------------------
    @PostMapping("/registrar-empresa")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> registrarEmpresaCompleta(@RequestBody RegistroEmpresaDTO data, HttpServletRequest http) {
        String ip = LimiteSolicitudes.ip(http);
        if (!limite.permitir("registro|" + ip, 5, java.time.Duration.ofHours(1))) {
            throw new LimiteExcedidoException("Demasiados intentos de registro. Inténtalo más tarde.");
        }

        // Si el RUT quedó a medio registrar (nunca pagó) hace más de 30 min, se descarta para permitir registrarse de nuevo.
        // Va dentro de la misma transacción: si la validación falla, el descarte se revierte.
        String clienteFlowDescartado = descartarRegistroAbandonado(data);

        // Validar TODO antes de crear nada en Flow (evita clientes huérfanos)
        validarRegistro(data);

        // Consentimiento explícito y constancia de qué versión aceptó
        if (!data.isAceptaTerminos()) {
            throw new NegocioException("Debes aceptar los Términos y Condiciones y la Política de Privacidad para continuar.");
        }
        if (!terminosVigente.equals(data.getTerminosVersion())) {
            throw new ConflictoException("Actualizamos nuestros Términos y Condiciones. Recarga la página, léelos y vuelve a aceptarlos.");
        }
        // El correo del administrador debe estar verificado (se canjea dentro de la transacción)
        verificacionCorreo.canjear(data.getAdmin().getCorreo(), data.getTokenVerificacion());

        if (clienteFlowDescartado != null) {
            eliminarClienteFlowSilencioso(clienteFlowDescartado);
        }
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
        nuevaEmpresa.setTerminosVersion(terminosVigente);
        nuevaEmpresa.setTerminosAceptadosEn(LocalDateTime.now());
        nuevaEmpresa.setTerminosIp(ip);
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
                String correoEmp = empData.getCorreo() == null ? "" : empData.getCorreo().trim();
                nuevoEmpleado.setCorreo(correoEmp.isEmpty()
                        ? empData.getUsuario().trim() + "@" + rutEmpresa + ".local" // sin correo real: se recupera con el administrador
                        : MailService.validarDestino(correoEmp));
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
    // REGISTRO ABANDONADO
    // ------------------------------------------------------------------
    /**
     * Si el RUT pertenece a un registro PENDIENTE (sin suscripción) con más de 30 minutos, lo elimina junto con sus
     * usuarios y sucursales. Devuelve el id de cliente en Flow del registro descartado (para borrarlo después), o null.
     */
    private String descartarRegistroAbandonado(RegistroEmpresaDTO data) {
        if (data == null || data.getEmpresa() == null || !RutUtils.esValido(data.getEmpresa().getRut_empresa())) {
            return null; // validarRegistro informará el error
        }
        String rut = RutUtils.normalizar(data.getEmpresa().getRut_empresa());
        Empresa existente = empresaRepository.findByRutEmpresa(rut).orElse(null);
        if (existente == null || !"PENDIENTE".equals(existente.getEstado())) {
            return null;
        }
        boolean sinSuscripcion = existente.getFlowSubscriptionId() == null || existente.getFlowSubscriptionId().isBlank();
        boolean vencido = existente.getFechaRegistro() != null
                && existente.getFechaRegistro().isBefore(LocalDateTime.now().minusMinutes(MINUTOS_REGISTRO_ABANDONADO));
        if (!sinSuscripcion || !vencido) {
            return null;
        }
        log.warn("Registro abandonado de la empresa {} (RUT {}): se descarta para permitir un nuevo registro.", existente.getId(), rut);
        String clienteFlow = existente.getFlowCustomerId();
        usuarioRepository.deleteAll(usuarioRepository.findByEmpresaId(existente.getId()));
        sucursalRepository.deleteAll(sucursalRepository.findByEmpresaId(existente.getId()));
        empresaRepository.delete(existente);
        empresaRepository.flush();
        return clienteFlow;
    }

    private void eliminarClienteFlowSilencioso(String customerId) {
        try {
            Map<String, String> params = new TreeMap<>();
            params.put("customerId", customerId);
            flow.post("/customer/delete", params);
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar el cliente {} en Flow (se ignora): {}", customerId, e.getMessage());
        }
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
            Empresa existente = empresaRepository.findByRutEmpresa(rut).get();
            if ("PENDIENTE".equals(existente.getEstado())) {
                throw new ConflictoException("Hay un registro en curso con ese RUT. Si no alcanzaste a pagar, espera " + MINUTOS_REGISTRO_ABANDONADO + " minutos y vuelve a intentarlo, o contáctanos.");
            }
            throw new ConflictoException("Ya existe una empresa registrada con ese RUT. Si ya tienes cuenta, inicia sesión.");
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

            // La empresa debe existir ANTES de suscribir: si el registro fue descartado, no se cobra nada.
            Empresa empresa = empresaRepository.findByFlowCustomerId(customerId).orElse(null);
            if (empresa == null) {
                log.warn("Retorno de Flow para el cliente {} sin empresa asociada (registro descartado): no se crea suscripción.", customerId);
                return redirigir("error");
            }
            if ("ACTIVA".equals(empresa.getEstado()) && empresa.getFlowSubscriptionId() != null) {
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

        Map<String, Object> responseMap = flow.post("/customer/create", params);

        if (responseMap != null && responseMap.containsKey("customerId")) {
            return String.valueOf(responseMap.get("customerId"));
        }
        throw new ServicioExternoException("No se pudo crear tu cuenta de pagos. Inténtalo más tarde.");
    }

    private String generarEnlaceRegistroTarjeta(String customerId, String urlReturn) {
        Map<String, String> params = new TreeMap<>();
        params.put("customerId", customerId);
        params.put("url_return", urlReturn);

        Map<String, Object> responseMap = flow.post("/customer/register", params);

        if (responseMap != null && responseMap.containsKey("url") && responseMap.containsKey("token")) {
            return responseMap.get("url") + "?token=" + responseMap.get("token");
        }
        throw new ServicioExternoException("No se pudo generar el enlace de pago. Inténtalo más tarde.");
    }

    private Map<String, Object> suscribirClienteAlPlan(String customerId, String planId) {
        Map<String, String> params = new TreeMap<>();
        params.put("customerId", customerId);
        params.put("planId", planId);
        return flow.post("/subscription/create", params);
    }

    private Map<String, Object> consultarEstadoRegistro(String token) {
        Map<String, String> params = new TreeMap<>();
        params.put("token", token);
        return flow.get("/customer/getRegisterStatus", params);
    }

}
