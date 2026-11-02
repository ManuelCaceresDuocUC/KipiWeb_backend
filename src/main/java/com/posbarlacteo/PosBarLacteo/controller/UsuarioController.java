package com.posbarlacteo.PosBarLacteo.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import com.posbarlacteo.PosBarLacteo.service.UsuarioActivoService;
import com.posbarlacteo.PosBarLacteo.repository.SucursalRepository;
import com.posbarlacteo.PosBarLacteo.exception.ConflictoException;
import org.springframework.web.bind.annotation.PatchMapping;
import com.posbarlacteo.PosBarLacteo.service.MailService;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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
import com.posbarlacteo.PosBarLacteo.service.AuditoriaService;
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
    private final SucursalRepository sucursalRepository;
    private final UsuarioActivoService usuarioActivo;
    private final AuditoriaService auditoria;

    public UsuarioController(UsuarioRepository usuarioRepository, JwtService jwtService,
                             PasswordEncoder passwordEncoder, LoginAttemptService loginAttempts,
                             SucursalRepository sucursalRepository, UsuarioActivoService usuarioActivo,
                             AuditoriaService auditoria) {
        this.auditoria = auditoria;
        this.sucursalRepository = sucursalRepository;
        this.usuarioActivo = usuarioActivo;
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
            m.put("activo", u.estaActivo());
            if (u.getSucursal() != null) {
                m.put("sucursal", Map.of("id", u.getSucursal().getId(), "nombre", u.getSucursal().getNombre()));
            }
            return m;
        }).toList();
    }

    public record NuevoUsuarioReq(String usuario, String contrasena, String rol, Long sucursalId, String correo) { }

    public record EditarUsuarioReq(String rol, Long sucursalId) { }

    public record EstadoReq(Boolean activo) { }

    private static final java.util.regex.Pattern NOMBRE_USUARIO = java.util.regex.Pattern.compile("^[\\p{L}0-9._-]{3,50}$");

    /** Crea un usuario en la empresa del administrador. */
    @PostMapping
    @Transactional
    public Map<String, Object> crear(@RequestBody NuevoUsuarioReq req) {
        AuthPrincipal yo = SecurityUtils.actual();
        Usuario admin = usuarioRepository.findById(yo.usuarioId()).orElseThrow(() -> new RecursoNoEncontradoException("Sesión no válida."));
        Empresa empresa = admin.getEmpresa();

        String nombre = req.usuario() == null ? "" : req.usuario().trim();
        if (!NOMBRE_USUARIO.matcher(nombre).matches()) {
            throw new NegocioException("El usuario debe tener entre 3 y 50 caracteres: letras, números, punto, guion o guion bajo (sin espacios).");
        }
        if (usuarioRepository.existsByUsuario(nombre)) {
            throw new ConflictoException("El nombre de usuario \"" + nombre + "\" ya está en uso.");
        }
        String clave = req.contrasena();
        if (clave == null || clave.length() < 8 || clave.length() > 100) {
            throw new NegocioException("La contraseña debe tener entre 8 y 100 caracteres.");
        }
        Sucursal sucursal = sucursalDeMiEmpresa(req.sucursalId(), empresa.getId());

        String correoIngresado = req.correo() == null ? "" : req.correo().trim();
        String correo = correoIngresado.isEmpty()
                ? nombre + "@" + empresa.getRutEmpresa() + ".local" // sin correo real: se recupera con el administrador
                : MailService.validarDestino(correoIngresado);

        Usuario u = new Usuario();
        u.setUsuario(nombre);
        u.setContrasena(passwordEncoder.encode(clave));
        u.setCorreo(correo);
        u.setRol(Roles.normalizar(req.rol()));
        u.setEmpresa(empresa);
        u.setSucursal(sucursal);
        u.setActivo(true);
        usuarioRepository.save(u);
        auditoria.registrar(yo, "USUARIO_CREADO", "Creó el usuario \"" + nombre + "\" (" + u.getRol() + ", sucursal " + sucursal.getNombre() + ")");
        return Map.of("id", u.getId(), "message", "Usuario creado.");
    }

    /** Cambia el rol y/o la sucursal de otro usuario de la empresa. */
    @PutMapping("/{id}")
    @Transactional
    public Map<String, String> editar(@PathVariable Long id, @RequestBody EditarUsuarioReq req) {
        AuthPrincipal yo = SecurityUtils.actual();
        Usuario u = usuarioDeMiEmpresa(id);
        if (u.getId().equals(yo.usuarioId())) {
            throw new NegocioException("No puedes cambiar tu propio rol ni sucursal. Pídeselo a otro administrador.");
        }
        String nuevoRol = req.rol() == null ? Roles.normalizar(u.getRol()) : Roles.normalizar(req.rol());
        if (Roles.esAdmin(u.getRol()) && u.estaActivo() && !Roles.ADMIN.equals(nuevoRol)) {
            exigirOtroAdminActivo(u);
        }
        if (req.sucursalId() != null) {
            u.setSucursal(sucursalDeMiEmpresa(req.sucursalId(), u.getEmpresa().getId()));
        }
        u.setRol(nuevoRol);
        u.revocarSesiones(); // su token actual lleva el rol/sucursal anteriores: debe iniciar sesión de nuevo
        usuarioRepository.save(u);
        usuarioActivo.invalidar(u.getId());
        auditoria.registrar(yo, "USUARIO_EDITADO", "Cambió rol/sucursal de \"" + u.getUsuario() + "\": " + nuevoRol
                + (u.getSucursal() != null ? ", sucursal " + u.getSucursal().getNombre() : ""));
        return Map.of("message", "Usuario actualizado. Su sesión actual se cerrará y entrará con los permisos nuevos.");
    }

    /** Activa o desactiva un usuario (no se borra: conserva su historial de ventas y cajas). */
    @PatchMapping("/{id}/estado")
    @Transactional
    public Map<String, String> cambiarEstado(@PathVariable Long id, @RequestBody EstadoReq req) {
        AuthPrincipal yo = SecurityUtils.actual();
        if (req.activo() == null) {
            throw new NegocioException("Indica si el usuario debe quedar activo o desactivado.");
        }
        Usuario u = usuarioDeMiEmpresa(id);
        if (!req.activo()) {
            if (u.getId().equals(yo.usuarioId())) {
                throw new NegocioException("No puedes desactivar tu propia cuenta.");
            }
            if (Roles.esAdmin(u.getRol()) && u.estaActivo()) {
                exigirOtroAdminActivo(u);
            }
        }
        u.setActivo(req.activo());
        usuarioRepository.save(u);
        usuarioActivo.invalidar(u.getId());
        auditoria.registrar(yo, req.activo() ? "USUARIO_ACTIVADO" : "USUARIO_DESACTIVADO",
                (req.activo() ? "Activó" : "Desactivó") + " al usuario \"" + u.getUsuario() + "\"");
        return Map.of("message", req.activo() ? "Usuario activado." : "Usuario desactivado. Perderá el acceso en unos segundos.");
    }

    private void exigirOtroAdminActivo(Usuario objetivo) {
        long otros = usuarioRepository.findByEmpresaId(objetivo.getEmpresa().getId()).stream()
                .filter(x -> !x.getId().equals(objetivo.getId()) && x.estaActivo() && Roles.esAdmin(x.getRol()))
                .count();
        if (otros == 0) {
            throw new NegocioException("La empresa debe conservar al menos un administrador activo.");
        }
    }

    private Sucursal sucursalDeMiEmpresa(Long sucursalId, Long empresaId) {
        if (sucursalId == null) {
            throw new NegocioException("Selecciona la sucursal del usuario.");
        }
        return sucursalRepository.findByIdAndEmpresaId(sucursalId, empresaId)
                .filter(s -> !Boolean.FALSE.equals(s.getActivo()))
                .orElseThrow(() -> new NegocioException("La sucursal seleccionada no existe o está inactiva."));
    }

    public record NuevaClaveReq(String contrasena) { }

    public record NuevoCorreoReq(String correo) { }

    /** El administrador define una nueva contraseña para un usuario de SU empresa (empleados sin correo, olvidos, etc.). */
    @PutMapping("/{id}/contrasena")
    @Transactional
    public Map<String, String> restablecerContrasena(@PathVariable Long id, @RequestBody NuevaClaveReq req) {
        Usuario u = usuarioDeMiEmpresa(id);
        String nueva = req.contrasena();
        if (nueva == null || nueva.length() < 8 || nueva.length() > 100) {
            throw new NegocioException("La contraseña debe tener entre 8 y 100 caracteres.");
        }
        u.setContrasena(passwordEncoder.encode(nueva));
        u.revocarSesiones(); // cierra las sesiones abiertas con la contraseña anterior
        usuarioRepository.save(u);
        usuarioActivo.invalidar(u.getId());
        auditoria.registrar(SecurityUtils.actual(), "USUARIO_CLAVE", "Restableció la contraseña de \"" + u.getUsuario() + "\"");
        return Map.of("message", "Contraseña de \"" + u.getUsuario() + "\" actualizada. Sus sesiones abiertas se cerraron.");
    }

    /** El administrador registra o corrige el correo de un usuario de su empresa (permite que recupere su clave solo). */
    @PutMapping("/{id}/correo")
    @Transactional
    public Map<String, String> actualizarCorreo(@PathVariable Long id, @RequestBody NuevoCorreoReq req) {
        Usuario u = usuarioDeMiEmpresa(id);
        u.setCorreo(MailService.validarDestino(req.correo()));
        usuarioRepository.save(u);
        auditoria.registrar(SecurityUtils.actual(), "USUARIO_CORREO", "Cambió el correo de \"" + u.getUsuario() + "\"");
        return Map.of("message", "Correo actualizado.");
    }

    private Usuario usuarioDeMiEmpresa(Long id) {
        Long empresaId = SecurityUtils.actual().empresaId();
        Usuario u = usuarioRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        if (u.getEmpresa() == null || !u.getEmpresa().getId().equals(empresaId)) {
            throw new RecursoNoEncontradoException("Usuario no encontrado."); // no se revela que existe en otra empresa
        }
        return u;
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

        if (!encontrado.estaActivo()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Tu usuario está desactivado. Contacta a tu administrador."));
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
