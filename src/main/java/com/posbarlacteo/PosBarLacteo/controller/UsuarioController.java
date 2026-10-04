package com.posbarlacteo.PosBarLacteo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;
import com.posbarlacteo.PosBarLacteo.service.JwtService;

@RestController
@CrossOrigin(origins = {
    "http://posbarlacteo-manuel-2026.s3-website-us-east-1.amazonaws.com",
    "http://localhost:5173",
    "http://34.203.91.138",
    "https://ordpos.duckdns.org",
    "http://192.168.100.85:5173"
})
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public List<Usuario> listarUsuarios(@RequestParam(defaultValue = "1") Long empresaId) {
        return usuarioRepository.findByEmpresaId(empresaId);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Usuario loginRequest) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsuario(loginRequest.getUsuario());

        if (usuarioOpt.isPresent() && loginRequest.getContrasena() != null) {
            Usuario usuario = usuarioOpt.get();
            String guardada = usuario.getContrasena();
            String ingresada = loginRequest.getContrasena();

            boolean coincide;
            if (esHashBcrypt(guardada)) {
                coincide = passwordEncoder.matches(ingresada, guardada);
            } else {
                // Usuario antiguo con contraseña en texto plano
                coincide = guardada != null && guardada.equals(ingresada);
                if (coincide) {
                    // Migración automática a BCrypt en el primer login exitoso
                    usuario.setContrasena(passwordEncoder.encode(ingresada));
                    usuarioRepository.save(usuario);
                }
            }

            if (coincide) {
                // Empresa que aún no completó el pago en Flow
                if (usuario.getEmpresa() != null
                        && "PENDIENTE".equals(usuario.getEmpresa().getEstado())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("Tu empresa aún no completa el pago de la suscripción.");
                }

                String token = jwtService.generarToken(usuario.getUsuario(), usuario.getRol());

                // Va después de cualquier save(): no se debe persistir este null
                usuario.setContrasena(null);

                Map<String, Object> respuesta = new HashMap<>();
                respuesta.put("usuario", usuario);
                respuesta.put("token", token);
                return ResponseEntity.ok(respuesta);
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                             .body("Usuario o contraseña incorrectos");
    }

    private boolean esHashBcrypt(String valor) {
        return valor != null
                && (valor.startsWith("$2a$") || valor.startsWith("$2b$") || valor.startsWith("$2y$"));
    }
}