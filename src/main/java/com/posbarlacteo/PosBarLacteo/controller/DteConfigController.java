package com.posbarlacteo.PosBarLacteo.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.model.ConfiguracionDte;
import com.posbarlacteo.PosBarLacteo.repository.ConfiguracionDteRepository;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;

/**
 * Configuración DTE de la empresa del token (solo ADMIN, ver SecurityConfig).
 * La API Key nunca viaja de vuelta al navegador: solo "configurada" y los últimos 4 caracteres.
 */
@RestController
@RequestMapping("/api/dte")
public class DteConfigController {

    private final ConfiguracionDteRepository repository;

    public DteConfigController(ConfiguracionDteRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/configuracion")
    public ResponseEntity<ConfiguracionDte> getConfiguracion() {
        return repository.findByEmpresaId(SecurityUtils.actual().empresaId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Compatibilidad con el frontend anterior: el id de la URL se ignora, manda el token. */
    @GetMapping("/configuracion/{ignorado}")
    public ResponseEntity<ConfiguracionDte> getConfiguracionLegacy(@PathVariable String ignorado) {
        return getConfiguracion();
    }

    @PostMapping("/configuracion")
    @Transactional
    public ResponseEntity<?> guardarConfiguracion(@RequestBody ConfiguracionDte payload) {
        Long empresaId = SecurityUtils.actual().empresaId();
        ConfiguracionDte config = repository.findByEmpresaId(empresaId).orElseGet(ConfiguracionDte::new);

        config.setEmpresaId(empresaId);
        config.setProveedor(payload.getProveedor());
        // Llave en blanco = conservar la que ya estaba guardada
        if (payload.getApiKey() != null && !payload.getApiKey().isBlank()) {
            config.setApiKey(payload.getApiKey().trim());
        }
        if (payload.getApiSecret() != null && !payload.getApiSecret().isBlank()) {
            config.setApiSecret(payload.getApiSecret().trim());
        }
        config.setRutEmisor(payload.getRutEmisor());
        config.setAmbiente(payload.getAmbiente());
        config.setEmitirBoletaEfectivo(payload.isEmitirBoletaEfectivo());
        config.setEmitirBoletaTarjeta(payload.isEmitirBoletaTarjeta());
        config.setEmitirBoletaTransferencia(payload.isEmitirBoletaTransferencia());

        repository.save(config);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Configuración DTE guardada."));
    }

    /**
     * Por ahora solo valida que haya una llave (la enviada o la ya guardada). NO contacta al proveedor,
     * y el mensaje lo dice con claridad. La verificación real llega con la arquitectura multi-proveedor (Etapa 4).
     */
    @PostMapping("/probar-conexion")
    public ResponseEntity<?> probarConexion(@RequestBody ConfiguracionDte payload) {
        String llave = payload.getApiKey();
        if (llave == null || llave.isBlank()) {
            llave = repository.findByEmpresaId(SecurityUtils.actual().empresaId())
                    .map(ConfiguracionDte::getApiKey).orElse(null);
        }
        if (llave == null || llave.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valido", false, "mensaje", "La API Key no puede estar vacía"));
        }
        return ResponseEntity.ok(Map.of("valido", true,
                "mensaje", "Llave recibida. Aún no se verificó contra el proveedor; la verificación real se habilitará próximamente."));
    }
}
