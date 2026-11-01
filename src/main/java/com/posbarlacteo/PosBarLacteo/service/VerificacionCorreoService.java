package com.posbarlacteo.PosBarLacteo.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.VerificacionCorreo;
import com.posbarlacteo.PosBarLacteo.repository.VerificacionCorreoRepository;

/**
 * Comprueba que quien se registra controla el correo del administrador, ANTES de crear nada o llegar a Flow.
 * Paso 1: se envía un código de 6 dígitos (vence en 10 min, máx. 5 intentos).
 * Paso 2: al acertarlo se entrega un token de un solo uso (válido 30 min) que el registro canjea.
 */
@Service
public class VerificacionCorreoService {

    private static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);
    private static final Duration VIGENCIA_TOKEN = Duration.ofMinutes(30);
    private static final int MAX_INTENTOS = 5;

    private final VerificacionCorreoRepository repo;
    private final MailService mail;

    public VerificacionCorreoService(VerificacionCorreoRepository repo, MailService mail) {
        this.repo = repo;
        this.mail = mail;
    }

    @Transactional
    public void enviarCodigo(String correoCrudo) {
        String correo = MailService.validarDestino(correoCrudo).toLowerCase();

        LocalDateTime ahora = LocalDateTime.now();
        var ultimo = repo.findFirstByCorreoAndUsadoFalseOrderByIdDesc(correo);
        if (ultimo.isPresent() && ultimo.get().getCreado().isAfter(ahora.minusSeconds(60))) {
            throw new NegocioException("Ya te enviamos un código hace instantes. Espera un minuto para pedir otro.");
        }
        if (repo.countByCorreoAndCreadoAfter(correo, ahora.minusHours(1)) >= 4) {
            throw new NegocioException("Pediste demasiados códigos para este correo. Inténtalo en una hora.");
        }

        String codigo = HashUtil.codigo6();
        VerificacionCorreo v = new VerificacionCorreo();
        v.setCorreo(correo);
        v.setCodigoHash(HashUtil.sha256(correo + ":" + codigo));
        v.setExpira(ahora.plus(VIGENCIA_CODIGO));
        repo.save(v);

        mail.enviarTexto(correo, "Tu código de verificación KIPI: " + codigo,
                "Hola,\n\nTu código para crear tu cuenta en KIPI es: " + codigo
                        + "\n\nVence en 10 minutos. Si no lo solicitaste, ignora este correo.\n\nEquipo KIPI", null);
    }

    /** Devuelve el token que debe enviarse junto al registro. */
    @Transactional(noRollbackFor = NegocioException.class)
    public String verificar(String correoCrudo, String codigo) {
        String correo = MailService.validarDestino(correoCrudo).toLowerCase();
        VerificacionCorreo v = repo.findFirstByCorreoAndUsadoFalseOrderByIdDesc(correo)
                .orElseThrow(() -> new NegocioException("Primero solicita un código de verificación."));

        if (v.getTokenHash() != null) {
            throw new NegocioException("Este código ya fue verificado. Continúa con tu registro.");
        }
        if (v.getExpira().isBefore(LocalDateTime.now())) {
            throw new NegocioException("El código venció. Solicita uno nuevo.");
        }
        if (v.getIntentos() >= MAX_INTENTOS) {
            throw new NegocioException("Superaste los intentos permitidos. Solicita un código nuevo.");
        }
        v.setIntentos(v.getIntentos() + 1);

        String esperado = v.getCodigoHash();
        String recibido = HashUtil.sha256(correo + ":" + (codigo == null ? "" : codigo.trim()));
        if (!HashUtil.iguales(esperado, recibido)) {
            repo.save(v);
            throw new NegocioException("El código no es correcto.");
        }

        String token = HashUtil.tokenAleatorio();
        v.setTokenHash(HashUtil.sha256(token));
        v.setTokenExpira(LocalDateTime.now().plus(VIGENCIA_TOKEN));
        repo.save(v);
        return token;
    }

    /** Canjea el token del registro (un solo uso). Debe llamarse dentro de la transacción del registro. */
    @Transactional
    public void canjear(String correoCrudo, String token) {
        if (token == null || token.isBlank()) {
            throw new NegocioException("Verifica tu correo antes de continuar.");
        }
        String correo = MailService.validarDestino(correoCrudo).toLowerCase();
        var lista = repo.findByCorreoAndTokenHashAndUsadoFalse(correo, HashUtil.sha256(token));
        if (lista.isEmpty() || lista.get(0).getTokenExpira() == null || lista.get(0).getTokenExpira().isBefore(LocalDateTime.now())) {
            throw new NegocioException("La verificación de tu correo venció o no es válida. Verifícalo nuevamente.");
        }
        VerificacionCorreo v = lista.get(0);
        v.setUsado(true);
        repo.save(v);
    }

    @Transactional
    public void limpiar() {
        repo.borrarAnteriores(LocalDateTime.now().minusDays(2));
    }
}
