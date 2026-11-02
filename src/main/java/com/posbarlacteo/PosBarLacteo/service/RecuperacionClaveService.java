package com.posbarlacteo.PosBarLacteo.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.TokenRecuperacion;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.repository.TokenRecuperacionRepository;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;

/**
 * Recuperación de contraseña por correo. Nunca revela si el usuario existe ni si tiene correo.
 * Los empleados sin correo real (el sistema les asigna usuario@rut.local) se recuperan con su administrador.
 */
@Service
public class RecuperacionClaveService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionClaveService.class);
    private static final Duration VIGENCIA = Duration.ofMinutes(30);

    private final UsuarioRepository usuarios;
    private final TokenRecuperacionRepository tokens;
    private final MailService mail;
    private final PasswordEncoder encoder;
    private final String frontendUrl;

    public RecuperacionClaveService(UsuarioRepository usuarios, TokenRecuperacionRepository tokens, MailService mail,
                                    PasswordEncoder encoder,
                                    @Value("${app.frontend.url:https://www.kipipos.cl}") String frontendUrl) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.mail = mail;
        this.encoder = encoder;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public static boolean tieneCorreoReal(Usuario u) {
        String c = u.getCorreo();
        return c != null && !c.isBlank() && !c.toLowerCase().endsWith(".local");
    }

    /** Silencioso: si no hay nada que enviar, no hace nada y no lo informa. */
    @Transactional
    public void solicitar(String nombreUsuario) {
        String nombre = nombreUsuario == null ? "" : nombreUsuario.trim();
        if (nombre.isEmpty() || nombre.length() > 80) {
            return;
        }
        List<Usuario> candidatos = usuarios.findAllByUsuario(nombre);
        for (Usuario u : candidatos) {
            if (!tieneCorreoReal(u)) {
                continue;
            }
            try {
                // Un solo enlace vigente a la vez
                tokens.findByUsuarioIdAndUsadoFalse(u.getId()).forEach(t -> { t.setUsado(true); tokens.save(t); });

                String token = HashUtil.tokenAleatorio();
                TokenRecuperacion t = new TokenRecuperacion();
                t.setUsuarioId(u.getId());
                t.setTokenHash(HashUtil.sha256(token));
                t.setExpira(LocalDateTime.now().plus(VIGENCIA));
                tokens.save(t);

                mail.enviarTexto(u.getCorreo(), "Restablece tu contraseña de KIPI",
                        "Hola " + u.getUsuario() + ",\n\nRecibimos una solicitud para restablecer tu contraseña. "
                                + "Ingresa a este enlace (vence en 30 minutos y sirve una sola vez):\n\n"
                                + frontendUrl + "/restablecer?token=" + token
                                + "\n\nSi no fuiste tú, ignora este correo: tu contraseña no cambiará.\n\nEquipo KIPI", null);
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar la recuperación al usuario {}: {}", u.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void restablecer(String token, String nueva) {
        if (nueva == null || nueva.length() < 8) {
            throw new NegocioException("La contraseña debe tener al menos 8 caracteres.");
        }
        if (nueva.length() > 100) {
            throw new NegocioException("La contraseña es demasiado larga.");
        }
        TokenRecuperacion t = token == null ? null : tokens.findByTokenHashAndUsadoFalse(HashUtil.sha256(token.trim())).orElse(null);
        if (t == null || t.getExpira().isBefore(LocalDateTime.now())) {
            throw new NegocioException("El enlace no es válido o ya venció. Solicita uno nuevo.");
        }
        Usuario u = usuarios.findById(t.getUsuarioId()).orElseThrow(() -> new NegocioException("El enlace no es válido."));
        u.setContrasena(encoder.encode(nueva));
        u.revocarSesiones(); // quien tuviera una sesión abierta con la clave anterior queda fuera
        usuarios.save(u);
        t.setUsado(true);
        tokens.save(t);
        tokens.findByUsuarioIdAndUsadoFalse(u.getId()).forEach(o -> { o.setUsado(true); tokens.save(o); });
    }

    @Transactional
    public void limpiar() {
        tokens.borrarAnteriores(LocalDateTime.now().minusDays(2));
    }
}
