package com.posbarlacteo.PosBarLacteo.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.dto.LoginRequest;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.model.Sucursal;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.LoginAttemptService;
import com.posbarlacteo.PosBarLacteo.security.Roles;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.JwtService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private static final String MSG_CREDENCIALES = "Usuario o contraseña incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;

    public UsuarioController(UsuarioRepository usuarioRepository, JwtService jwtService,
                             PasswordEncoder passwordEncoder, LoginAttemptService loginAttempts) {
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
    }

    /** Usuarios de la empresa del administrador autenticado (solo ADMIN, ver SecurityConfig). */
    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarUsuarios() {
        AuthPrincipal p = SecurityUtils.actual();
        return usuarioRepository.findByEmpresaId(p.empresaId()).stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("usuario", u.getUsuario());
            m.put("correo", u.getCorreo());
            m.put("rol", Roles.normalizar(u.getRol()));
            if (u.getSucursal() != null) {
                m.put("sucursal", Map.of("id", u.getSucursal().getId(), "nombre", u.getSucursal().getNombre()));
            }
            return m;
        }).toList();
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        String nombre = req.getUsuario() == null ? "" : req.getUsuario().trim();
        String clave = req.getContrasena();
        String llave = nombre.toLowerCase() + "|" + http.getRemoteAddr();

        if (loginAttempts.bloqueado(llave)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo."));
        }
        if (nombre.isEmpty() || clave == null || clave.isEmpty()) {
            return credencialesInvalidas(llave);
        }

        // El nombre de usuario es único por empresa; si hay homónimos, entra el que coincida con la contraseña.
        Usuario encontrado = null;
        for (Usuario u : usuarioRepository.findAllByUsuario(nombre)) {
            if (coincide(u, clave)) {
                encontrado = u;
                break;
            }
        }
        if (encontrado == null) {
            return credencialesInvalidas(llave);
        }

        Empresa empresa = encontrado.getEmpresa();
        if (empresa == null) {
            return credencialesInvalidas(llave);
        }
        if ("PENDIENTE".equals(empresa.getEstado())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Tu empresa aún no completa el pago de la suscripción."));
        }

        loginAttempts.exito(llave);

        Sucursal sucursal = encontrado.getSucursal();
        String rol = Roles.normalizar(encontrado.getRol());
        AuthPrincipal principal = new AuthPrincipal(
                encontrado.getId(), encontrado.getUsuario(), empresa.getId(),
                sucursal != null ? sucursal.getId() : null, rol);
        String token = jwtService.generarToken(principal);

        Map<String, Object> usuarioJson = new LinkedHashMap<>();
        usuarioJson.put("id", encontrado.getId());
        usuarioJson.put("usuario", encontrado.getUsuario());
        usuarioJson.put("rol", rol);
        usuarioJson.put("correo", encontrado.getCorreo());
        usuarioJson.put("empresa", Map.of(
                "id", empresa.getId(),
                "razonSocial", empresa.getRazonSocial() == null ? "" : empresa.getRazonSocial(),
                "rutEmpresa", empresa.getRutEmpresa() == null ? "" : empresa.getRutEmpresa()));
        if (sucursal != null) {
            usuarioJson.put("sucursal", Map.of("id", sucursal.getId(), "nombre", sucursal.getNombre()));
        }

        return ResponseEntity.ok(Map.of("usuario", usuarioJson, "token", token));
    }

    private ResponseEntity<?> credencialesInvalidas(String llave) {
        loginAttempts.fallo(llave);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", MSG_CREDENCIALES));
    }

    private boolean coincide(Usuario u, String ingresada) {
        String guardada = u.getContrasena();
        if (guardada == null) {
            return false;
        }
        if (esHashBcrypt(guardada)) {
            return passwordEncoder.matches(ingresada, guardada);
        }
        // Usuario antiguo con contraseña en texto plano: se migra a BCrypt en el primer login correcto
        if (guardada.equals(ingresada)) {
            u.setContrasena(passwordEncoder.encode(ingresada));
            usuarioRepository.save(u);
            return true;
        }
        return false;
    }

    private boolean esHashBcrypt(String valor) {
        return valor.startsWith("$2a$") || valor.startsWith("$2b$") || valor.startsWith("$2y$");
    }
}
