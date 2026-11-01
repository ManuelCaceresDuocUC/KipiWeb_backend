package com.posbarlacteo.PosBarLacteo.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.SuscripcionService;

import lombok.RequiredArgsConstructor;

/** Estado de la suscripción de la empresa del usuario autenticado (visible para todos los roles). */
@RestController
@RequestMapping("/api/empresa/suscripcion")
@RequiredArgsConstructor
public class SuscripcionController {

    private static final Duration ENTRE_SINCRONIZACIONES = Duration.ofSeconds(30);

    private final SuscripcionService suscripcionService;
    private final EmpresaRepository empresaRepository;

    @GetMapping
    public Map<String, Object> estado(@RequestParam(defaultValue = "false") boolean conLink) {
        Long empresaId = SecurityUtils.actual().empresaId();
        SuscripcionService.Vista v = suscripcionService.vista(empresaId);
        return responder(v, conLink && v.moraDesde() != null ? suscripcionService.enlaceDePago(empresaId) : null);
    }

    /** "Ya pagué": consulta Flow ahora mismo y actualiza el estado. */
    @PostMapping("/sincronizar")
    public Map<String, Object> sincronizar() {
        Long empresaId = SecurityUtils.actual().empresaId();
        Empresa e = empresaRepository.findById(empresaId).orElseThrow(() -> new NegocioException("Empresa no encontrada."));
        LocalDateTime ultima = e.getFlowUltimaSync();
        if (ultima != null && Duration.between(ultima, LocalDateTime.now()).compareTo(ENTRE_SINCRONIZACIONES) < 0) {
            throw new NegocioException("Acabamos de revisar tu pago. Espera unos segundos e inténtalo de nuevo.");
        }
        SuscripcionService.Vista v = suscripcionService.sincronizar(empresaId);
        return responder(v, v.moraDesde() != null ? suscripcionService.enlaceDePago(empresaId) : null);
    }

    private Map<String, Object> responder(SuscripcionService.Vista v, String enlacePago) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estado", v.estado());
        m.put("modo", v.modo().name());
        m.put("moraDesde", v.moraDesde());
        m.put("limiteGracia", v.limiteGracia());
        m.put("accesoHasta", v.accesoHasta());
        m.put("cancelada", v.cancelada());
        m.put("mensaje", v.mensaje());
        m.put("enlacePago", enlacePago);
        return m;
    }
}
