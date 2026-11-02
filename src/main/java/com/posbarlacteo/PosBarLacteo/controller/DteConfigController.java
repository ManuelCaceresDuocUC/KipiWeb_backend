package com.posbarlacteo.PosBarLacteo.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.dte.DteProveedorRegistry;
import com.posbarlacteo.PosBarLacteo.dte.ResultadoPrueba;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.ConfiguracionDte;
import com.posbarlacteo.PosBarLacteo.repository.ConfiguracionDteRepository;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.AuditoriaService;
import com.posbarlacteo.PosBarLacteo.service.DteService;
import com.posbarlacteo.PosBarLacteo.util.CifradoClaves;

/**
 * Configuración DTE de la empresa del token (solo ADMIN, ver SecurityConfig).
 * La API Key se guarda CIFRADA y nunca viaja de vuelta al navegador: solo "configurada" y los últimos 4 caracteres.
 */
@RestController
@RequestMapping("/api/dte")
public class DteConfigController {

    private static final int MAX_LLAVE = 120; // el valor cifrado debe caber en la columna de 255

    private final ConfiguracionDteRepository repository;
    private final DteProveedorRegistry registry;
    private final DteService dteService;
    private final AuditoriaService auditoria;

    public DteConfigController(ConfiguracionDteRepository repository, DteProveedorRegistry registry, DteService dteService,
                               AuditoriaService auditoria) {
        this.auditoria = auditoria;
        this.repository = repository;
        this.registry = registry;
        this.dteService = dteService;
    }

    /** Proveedores disponibles para el selector del frontend. */
    @GetMapping("/proveedores")
    public List<Map<String, Object>> proveedores() {
        return registry.todos().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo", p.codigo());
            m.put("nombre", p.nombre());
            m.put("verificado", p.verificado());
            return m;
        }).toList();
    }

    @GetMapping("/configuracion")
    public ResponseEntity<ConfiguracionDte> getConfiguracion() {
        try {
            return repository.findByEmpresaId(SecurityUtils.actual().empresaId())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalStateException e) {
            throw new NegocioException("No se pudo leer la configuración guardada: " + e.getMessage());
        }
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

        String proveedor = payload.getProveedor() == null ? "ninguno" : payload.getProveedor().trim().toLowerCase();
        if (!"ninguno".equals(proveedor) && registry.buscar(proveedor).isEmpty()) {
            throw new NegocioException("El proveedor \"" + payload.getProveedor() + "\" no está soportado.");
        }
        boolean llaveNueva = payload.getApiKey() != null && !payload.getApiKey().isBlank();
        boolean secretNuevo = payload.getApiSecret() != null && !payload.getApiSecret().isBlank();
        if ((llaveNueva || secretNuevo) && !CifradoClaves.disponible()) {
            throw new NegocioException("El servidor no tiene configurada la clave de cifrado (DTE_ENCRYPTION_KEY). "
                    + "Por seguridad no se guardan API keys sin cifrar.");
        }
        if ((llaveNueva && payload.getApiKey().trim().length() > MAX_LLAVE)
                || (secretNuevo && payload.getApiSecret().trim().length() > MAX_LLAVE)) {
            throw new NegocioException("La API Key es demasiado larga. Revisa que hayas pegado solo la llave.");
        }

        ConfiguracionDte config;
        try {
            config = repository.findByEmpresaId(empresaId).orElseGet(ConfiguracionDte::new);
        } catch (IllegalStateException e) {
            throw new NegocioException("No se pudo leer la configuración guardada: " + e.getMessage()
                    + " Si cambió la clave de cifrado, ingresa la API Key nuevamente.");
        }

        // Las credenciales de un proveedor no sirven en otro: al cambiar de proveedor se exigen nuevas
        boolean cambioProveedor = config.getId() != null && !"ninguno".equals(proveedor)
                && !proveedor.equalsIgnoreCase(config.getProveedor());
        if (cambioProveedor && !llaveNueva) {
            throw new NegocioException("Cambiaste de proveedor: ingresa sus credenciales (las del proveedor anterior no sirven).");
        }
        if (cambioProveedor && !secretNuevo) {
            config.setApiSecret(null); // no arrastrar la contraseña del proveedor anterior
        }

        config.setEmpresaId(empresaId);
        config.setProveedor(proveedor);
        // Llave en blanco = conservar la que ya estaba guardada
        if (llaveNueva) {
            config.setApiKey(payload.getApiKey().trim());
        }
        if (secretNuevo) {
            config.setApiSecret(payload.getApiSecret().trim());
        }
        config.setRutEmisor(payload.getRutEmisor() == null ? null : payload.getRutEmisor().trim());
        config.setAmbiente(payload.getAmbiente());
        config.setEmitirBoletaEfectivo(payload.isEmitirBoletaEfectivo());
        config.setEmitirBoletaTarjeta(payload.isEmitirBoletaTarjeta());
        config.setEmitirBoletaTransferencia(payload.isEmitirBoletaTransferencia());

        repository.save(config);
        // Nunca se registra la llave: solo qué cambió
        auditoria.registrar(SecurityUtils.actual(), "DTE_CONFIG", "Proveedor " + proveedor + ", ambiente " + config.getAmbiente()
                + (llaveNueva ? ", credenciales nuevas" : ""));
        return ResponseEntity.ok(Map.of("status", "success", "message", "Configuración DTE guardada."));
    }

    /**
     * Consulta real al proveedor elegido. "verificado=false" significa que el proveedor no dio una respuesta
     * concluyente: no se afirma que la llave sea buena ni mala.
     */
    @PostMapping("/probar-conexion")
    public ResponseEntity<?> probarConexion(@RequestBody ConfiguracionDte payload) {
        ResultadoPrueba r = dteService.probarConexion(SecurityUtils.actual().empresaId(), payload);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valido", r.valido());
        m.put("verificado", r.verificado());
        m.put("mensaje", r.mensaje());
        return ResponseEntity.ok(m);
    }

    /** Documentos DTE que requieren atención (pendientes de reintento o por revisar). */
    @GetMapping("/pendientes")
    public List<Map<String, Object>> pendientes() {
        return dteService.pendientes(SecurityUtils.actual().empresaId());
    }
}
