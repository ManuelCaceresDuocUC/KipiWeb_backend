package com.posbarlacteo.PosBarLacteo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.ServicioExternoException;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/** Correo saliente por SMTP (Gmail). Sin MAIL_USERNAME configurado queda deshabilitado. */
@Slf4j
@Service
public class MailService {

    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final JavaMailSender sender;
    private final String remitente;

    public MailService(JavaMailSender sender, @Value("${spring.mail.username:}") String remitente) {
        this.sender = sender;
        this.remitente = remitente;
    }

    public boolean habilitado() {
        return remitente != null && !remitente.isBlank();
    }

    public static String validarDestino(String email) {
        String e = email == null ? "" : email.trim();
        if (!EMAIL.matcher(e).matches() || e.length() > 150) {
            throw new NegocioException("El correo ingresado no es válido.");
        }
        return e;
    }

    public void enviarConAdjunto(String destino, String nombreRemitente, String asunto, String mensaje,
                                 String nombreAdjunto, byte[] pdf) {
        if (!habilitado()) {
            throw new NegocioException("El envío de correos no está configurado en el servidor.");
        }
        String para = validarDestino(destino);
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(remitente, nombreRemitente == null || nombreRemitente.isBlank() ? "KIPIPOS" : nombreRemitente);
            h.setTo(para);
            h.setSubject(asunto);
            h.setText(mensaje, false);
            h.addAttachment(nombreAdjunto, new org.springframework.core.io.ByteArrayResource(pdf), "application/pdf");
            sender.send(msg);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo a {}: {}", para, e.getMessage(), e);
            throw new ServicioExternoException("No se pudo enviar el correo. Revisa la dirección e intenta nuevamente.");
        }
    }

    static String limpio(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
