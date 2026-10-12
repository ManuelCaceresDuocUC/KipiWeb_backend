package com.posbarlacteo.PosBarLacteo.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.posbarlacteo.PosBarLacteo.model.ConfiguracionDte;
import com.posbarlacteo.PosBarLacteo.repository.ConfiguracionDteRepository;

@RestController
@RequestMapping("/api/dte")
@CrossOrigin(origins = "*") // Ajusta los orígenes permitidos como en tu otro controller
public class DteConfigController {

    private final ConfiguracionDteRepository repository;

    public DteConfigController(ConfiguracionDteRepository repository) {
        this.repository = repository;
    }

    // 1. Obtener la configuración actual del cliente
    @GetMapping("/configuracion/{empresaId}")
    public ResponseEntity<ConfiguracionDte> getConfiguracion(@PathVariable Long empresaId) {
        Optional<ConfiguracionDte> config = repository.findByEmpresaId(empresaId);
        
        // Si existe la retorna, si no, retorna un HTTP 404 o un objeto vacío por defecto
        return config.map(ResponseEntity::ok)
                     .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 2. Guardar o actualizar la configuración desde el panel de administración
    @PostMapping("/configuracion")
    public ResponseEntity<?> guardarConfiguracion(@RequestBody ConfiguracionDte payload) {
        try {
            // Buscamos si ya existe configuración para esta empresa
            Optional<ConfiguracionDte> existente = repository.findByEmpresaId(payload.getEmpresaId());
            
            ConfiguracionDte config = existente.orElse(new ConfiguracionDte());
            
            // Actualizamos los campos
            config.setEmpresaId(payload.getEmpresaId());
            config.setProveedor(payload.getProveedor());
            config.setApiKey(payload.getApiKey());
            config.setApiSecret(payload.getApiSecret());
            config.setRutEmisor(payload.getRutEmisor());
            config.setAmbiente(payload.getAmbiente());
            config.setEmitirBoletaEfectivo(payload.isEmitirBoletaEfectivo());
            config.setEmitirBoletaTarjeta(payload.isEmitirBoletaTarjeta());
            config.setEmitirBoletaTransferencia(payload.isEmitirBoletaTransferencia());
            
            repository.save(config);

            return ResponseEntity.ok(Map.of("status", "success", "message", "Configuración DTE guardada."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }
}