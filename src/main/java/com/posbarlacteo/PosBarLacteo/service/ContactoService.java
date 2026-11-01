package com.posbarlacteo.PosBarLacteo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;

/** Formulario de contacto público: avisa al equipo de KIPI y envía copia de confirmación al consultante. */
@Service
public class ContactoService {

    private final MailService mail;
    private final String destino;

    public ContactoService(MailService mail, @Value("${app.contacto.destino:}") String destino) {
        this.mail = mail;
        this.destino = destino == null || destino.isBlank() ? mail.remitente() : destino.trim();
    }

    public void enviar(String nombre, String correo, String telefono, String asunto, String mensaje) {
        String n = limpiar(nombre, 100);
        String c = MailService.validarDestino(correo);
        String t = limpiar(telefono, 30);
        String a = limpiar(asunto, 120);
        String m = mensaje == null ? "" : mensaje.trim();
        if (n.length() < 2) throw new NegocioException("Ingresa tu nombre.");
        if (m.length() < 10) throw new NegocioException("Cuéntanos un poco más en tu mensaje (mínimo 10 caracteres).");
        if (m.length() > 3000) throw new NegocioException("El mensaje es demasiado largo (máximo 3000 caracteres).");
        if (destino == null || destino.isBlank()) throw new NegocioException("El formulario no está disponible por ahora.");

        String cuerpoEquipo = "Nuevo mensaje desde el formulario de contacto de KIPI\n\n"
                + "Nombre: " + n + "\nCorreo: " + c + "\nTeléfono: " + (t.isEmpty() ? "-" : t)
                + "\nAsunto: " + (a.isEmpty() ? "-" : a) + "\n\nMensaje:\n" + m;
        mail.enviarTexto(destino, "[Contacto KIPI] " + (a.isEmpty() ? "Nuevo mensaje de " + n : a), cuerpoEquipo, c);

        // La confirmación es "best effort": si falla, el equipo igual recibió el mensaje.
        try {
            mail.enviarTexto(c, "Recibimos tu mensaje - KIPI",
                    "Hola " + n + ",\n\nRecibimos tu mensaje y te responderemos a la brevedad a este mismo correo.\n\n"
                            + "Esto es lo que nos enviaste:\n\n" + m + "\n\nEquipo KIPI", destino);
        } catch (RuntimeException ignorada) {
            // sin acción
        }
    }

    private static String limpiar(String s, int max) {
        String v = s == null ? "" : s.replaceAll("[\\r\\n\\t]+", " ").trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
