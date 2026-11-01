package com.posbarlacteo.PosBarLacteo.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.LimiteExcedidoException;
import com.posbarlacteo.PosBarLacteo.security.LimiteSolicitudes;
import com.posbarlacteo.PosBarLacteo.service.ContactoService;
import com.posbarlacteo.PosBarLacteo.service.RecuperacionClaveService;
import com.posbarlacteo.PosBarLacteo.service.TurnstileService;
import com.posbarlacteo.PosBarLacteo.service.VerificacionCorreoService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/** Endpoints públicos (sin sesión): contacto, verificación de correo del registro y recuperación de contraseña. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PublicoController {

    private final ContactoService contacto;
    private final VerificacionCorreoService verificacion;
    private final RecuperacionClaveService recuperacion;
    private final TurnstileService turnstile;
    private final LimiteSolicitudes limite;

    @Data
    public static class ContactoReq {
        private String nombre, correo, telefono, asunto, mensaje, turnstile;
        /** Campo trampa: un humano nunca lo ve ni lo llena. */
        private String sitioWeb;
    }

    @Data
    public static class CorreoReq {
        private String correo, turnstile;
    }

    @Data
    public static class VerificarReq {
        private String correo, codigo;
    }

    @Data
    public static class RecuperarReq {
        private String usuario, turnstile;
    }

    @Data
    public static class RestablecerReq {
        private String token, contrasena;
    }

    @PostMapping("/contacto")
    public ResponseEntity<?> contacto(@RequestBody ContactoReq r, HttpServletRequest http) {
        String ip = LimiteSolicitudes.ip(http);
        if (r.getSitioWeb() != null && !r.getSitioWeb().isBlank()) {
            return ResponseEntity.ok(Map.of("message", "Mensaje enviado.")); // bot: se finge éxito
        }
        exigir(limite.permitir("contacto|" + ip, 4, Duration.ofHours(1)));
        turnstile.exigir(r.getTurnstile(), ip);
        contacto.enviar(r.getNombre(), r.getCorreo(), r.getTelefono(), r.getAsunto(), r.getMensaje());
        return ResponseEntity.ok(Map.of("message", "Mensaje enviado. Te respondimos con una copia a tu correo."));
    }

    @PostMapping("/registro/codigo")
    public ResponseEntity<?> enviarCodigo(@RequestBody CorreoReq r, HttpServletRequest http) {
        String ip = LimiteSolicitudes.ip(http);
        exigir(limite.permitir("codigo|" + ip, 6, Duration.ofHours(1)));
        turnstile.exigir(r.getTurnstile(), ip);
        verificacion.enviarCodigo(r.getCorreo());
        return ResponseEntity.ok(Map.of("message", "Te enviamos un código de 6 dígitos. Revisa tu correo (y la carpeta de spam)."));
    }

    @PostMapping("/registro/verificar")
    public ResponseEntity<?> verificar(@RequestBody VerificarReq r, HttpServletRequest http) {
        exigir(limite.permitir("verificar|" + LimiteSolicitudes.ip(http), 20, Duration.ofHours(1)));
        String token = verificacion.verificar(r.getCorreo(), r.getCodigo());
        return ResponseEntity.ok(Map.of("tokenVerificacion", token));
    }

    @PostMapping("/recuperar")
    public ResponseEntity<?> recuperar(@RequestBody RecuperarReq r, HttpServletRequest http) {
        String ip = LimiteSolicitudes.ip(http);
        exigir(limite.permitir("recuperar|" + ip, 5, Duration.ofHours(1)));
        turnstile.exigir(r.getTurnstile(), ip);
        String usuario = r.getUsuario() == null ? "" : r.getUsuario().trim().toLowerCase();
        if (limite.permitir("recuperar-u|" + usuario, 3, Duration.ofHours(1))) {
            recuperacion.solicitar(r.getUsuario());
        }
        // Respuesta idéntica exista o no el usuario, tenga o no correo
        return ResponseEntity.ok(Map.of("message",
                "Si el usuario existe y tiene un correo registrado, te enviamos un enlace para restablecer la contraseña. "
                        + "Si no recibes nada, pide a tu administrador que la restablezca."));
    }

    @PostMapping("/restablecer")
    public ResponseEntity<?> restablecer(@RequestBody RestablecerReq r, HttpServletRequest http) {
        exigir(limite.permitir("restablecer|" + LimiteSolicitudes.ip(http), 10, Duration.ofHours(1)));
        recuperacion.restablecer(r.getToken(), r.getContrasena());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada. Ya puedes iniciar sesión."));
    }

    private static void exigir(boolean permitido) {
        if (!permitido) {
            throw new LimiteExcedidoException("Demasiadas solicitudes. Inténtalo más tarde.");
        }
    }
}
