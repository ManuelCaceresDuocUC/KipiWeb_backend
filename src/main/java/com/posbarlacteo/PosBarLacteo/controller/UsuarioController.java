package com.posbarlacteo.PosBarLacteo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController; // ✨ Importar el servicio

import com.posbarlacteo.PosBarLacteo.model.Usuario;                                    // ✨ Importar Map
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;
import com.posbarlacteo.PosBarLacteo.service.JwtService;



@RestController
@CrossOrigin(origins = {
    "http://posbarlacteo-manuel-2026.s3-website-us-east-1.amazonaws.com", // Producción AWS
    "http://localhost:5173",
    "http://34.203.91.138",
    "https://ordpos.duckdns.org",
                                                 // PC Local
    "http://192.168.100.85:5173"                                         // Tu Celular
})
@RequestMapping("/api/usuarios")
public class UsuarioController {
    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping
    public List<Usuario> listarUsuarios(@RequestParam(defaultValue = "1") Long empresaId) {
        return usuarioRepository.findByEmpresaId(empresaId);
    }

    @Autowired
    private JwtService jwtService; // Inyectar servicio

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Usuario loginRequest) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsuario(loginRequest.getUsuario());

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            if (usuario.getContrasena().equals(loginRequest.getContrasena())) {
                usuario.setContrasena(null); 
                
                // ✨ GENERAR TOKEN CON EL ROL SELLADO
                String token = jwtService.generarToken(usuario.getUsuario(), usuario.getRol());

                // Devolvemos el usuario junto con su token criptográfico
                Map<String, Object> respuesta = new HashMap<>();
                respuesta.put("usuario", usuario);
                respuesta.put("token", token);
                
                return ResponseEntity.ok(respuesta);
            }
        }
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                             .body("Usuario o contraseña incorrectos");
    }
    
}
