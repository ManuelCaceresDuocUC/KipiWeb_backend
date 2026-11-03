package com.posbarlacteo.PosBarLacteo.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;

/**
 * Avisa por correo a los administradores que su prueba gratuita está por terminar.
 *  Aviso 1: faltan 10 días o menos (día 20 de 30) · Aviso 2: faltan 3 días o menos (día 27) · Aviso 3: la prueba terminó (día 30).
 * Cada aviso se envía una sola vez (empresas.prueba_aviso guarda el último). Si el servidor estuvo caído y se salta
 * un hito, se envía solo el vigente. Si el correo no está configurado o falla, no se marca y se reintenta en el ciclo siguiente.
 */
@Component
public class AvisosPruebaJob {

    private static final Logger log = LoggerFactory.getLogger(AvisosPruebaJob.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmpresaRepository empresaRepository;
    private final CorreosEmpresa correos;
    private final MailService mail;
    private final String frontendUrl;
    private final boolean habilitado;

    public AvisosPruebaJob(EmpresaRepository empresaRepository, CorreosEmpresa correos, MailService mail,
                           @Value("${app.frontend.url:https://www.kipipos.cl}") String frontendUrl,
                           @Value("${suscripcion.avisos-habilitado:true}") boolean habilitado) {
        this.empresaRepository = empresaRepository;
        this.correos = correos;
        this.mail = mail;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.habilitado = habilitado;
    }

    @Scheduled(initialDelayString = "${suscripcion.avisos-inicial:PT5M}", fixedDelayString = "${suscripcion.avisos-intervalo:PT6H}")
    public void enviar() {
        if (!habilitado || !mail.habilitado()) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        for (Empresa e : empresaRepository.findByEstado(SuscripcionService.PRUEBA)) {
            if (e.getPruebaHasta() == null) {
                continue;
            }
            int hito = hitoVigente(ahora, e.getPruebaHasta());
            if (hito == 0 || hito <= e.getPruebaAviso()) {
                continue;
            }
            try {
                if (avisar(e, hito, ahora)) {
                    e.setPruebaAviso(hito);
                    empresaRepository.save(e);
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo procesar el aviso de prueba de la empresa {}: {}", e.getId(), ex.getMessage());
            }
        }
    }

    /** 0 = todavía no toca avisar, 1 = faltan ≤ 10 días, 2 = faltan ≤ 3 días, 3 = ya venció. */
    static int hitoVigente(LocalDateTime ahora, LocalDateTime hasta) {
        if (!ahora.isBefore(hasta)) return 3;
        long dias = SuscripcionService.diasRestantes(ahora, hasta);
        if (dias <= 3) return 2;
        if (dias <= 10) return 1;
        return 0;
    }

    private boolean avisar(Empresa e, int hito, LocalDateTime ahora) {
        String portal = frontendUrl + "/administracion";
        String asunto;
        String cuerpo;
        if (hito == 3) {
            asunto = "Tu prueba gratuita de KIPI terminó";
            cuerpo = "Hola,\n\nLa prueba gratuita de " + e.getRazonSocial() + " terminó el " + e.getPruebaHasta().format(FECHA) + ". "
                    + "Tu cuenta quedó en modo de solo lectura: puedes consultar tu información, pero no registrar ventas.\n\n"
                    + "Para seguir usando KIPI, activa tu suscripción inscribiendo tu tarjeta (el cobro mensual parte al activarla):\n" + portal + "\n\n"
                    + "Si prefieres no continuar, puedes dar de baja tu cuenta desde la misma página; tus datos se eliminan tras un plazo de gracia.";
        } else {
            long dias = SuscripcionService.diasRestantes(ahora, e.getPruebaHasta());
            String restan = dias <= 1 ? "1 día" : dias + " días";
            asunto = "Te " + (dias <= 1 ? "queda " : "quedan ") + restan + " de prueba gratuita en KIPI";
            cuerpo = "Hola,\n\nLa prueba gratuita de " + e.getRazonSocial() + " termina el " + e.getPruebaHasta().format(FECHA)
                    + " (" + (dias <= 1 ? "queda " : "quedan ") + restan + ").\n\n"
                    + "Para no perder el acceso, activa tu suscripción inscribiendo tu tarjeta:\n" + portal + "\n\n"
                    + "Importante: si activas antes de que termine la prueba, el cobro mensual parte ese mismo día.\n"
                    + "Si no activas, al terminar la prueba tu cuenta pasa a solo lectura (no se borra nada ni se te cobra).";
        }
        boolean alguno = false;
        for (String destino : correos.administradores(e.getId())) {
            try {
                mail.enviarTexto(destino, asunto, cuerpo, null);
                alguno = true;
            } catch (RuntimeException ex) {
                log.warn("No se pudo enviar el aviso de prueba a {}: {}", destino, ex.getMessage());
            }
        }
        if (alguno) {
            log.info("Aviso de prueba {} enviado a la empresa {}.", hito, e.getId());
        }
        return alguno;
    }
}
