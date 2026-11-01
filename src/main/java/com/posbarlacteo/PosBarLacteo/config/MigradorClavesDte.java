package com.posbarlacteo.PosBarLacteo.config;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.posbarlacteo.PosBarLacteo.util.CifradoClaves;

/**
 * Al arrancar cifra las API keys que quedaron guardadas en texto plano (anteriores a la etapa 4).
 * Es repetible: solo toca valores sin el prefijo "enc:v1:". Sin DTE_ENCRYPTION_KEY no hace nada.
 */
@Component
public class MigradorClavesDte implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigradorClavesDte.class);

    private final JdbcTemplate jdbc;

    public MigradorClavesDte(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!CifradoClaves.disponible()) {
            log.warn("DTE_ENCRYPTION_KEY no está configurada: las API keys DTE no se pueden guardar ni migrar a formato cifrado.");
            return;
        }
        try {
            List<Map<String, Object>> filas = jdbc.queryForList("SELECT id, api_key, api_secret FROM configuracion_dte");
            int migradas = 0;
            for (Map<String, Object> f : filas) {
                String key = (String) f.get("api_key");
                String secret = (String) f.get("api_secret");
                boolean cambiaKey = key != null && !key.isBlank() && !CifradoClaves.estaCifrado(key);
                boolean cambiaSecret = secret != null && !secret.isBlank() && !CifradoClaves.estaCifrado(secret);
                if (cambiaKey || cambiaSecret) {
                    jdbc.update("UPDATE configuracion_dte SET api_key = ?, api_secret = ? WHERE id = ?",
                            cambiaKey ? CifradoClaves.cifrar(key) : key,
                            cambiaSecret ? CifradoClaves.cifrar(secret) : secret,
                            f.get("id"));
                    migradas++;
                }
            }
            if (migradas > 0) {
                log.info("Se cifraron las API keys DTE de {} configuración(es).", migradas);
            }
        } catch (Exception e) {
            // No debe impedir el arranque (p. ej. la tabla aún no existe)
            log.warn("No se pudo revisar el cifrado de las API keys DTE: {}", e.getMessage());
        }
    }
}
