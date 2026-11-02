package com.posbarlacteo.PosBarLacteo.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.posbarlacteo.PosBarLacteo.model.AuditoriaEvento;
import com.posbarlacteo.PosBarLacteo.repository.AuditoriaEventoRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/**
 * Deja constancia de las acciones sensibles. Es "a prueba de fallos": si no se puede escribir el registro, la
 * operación original NO se ve afectada (solo queda un aviso en el log del servidor).
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final AuditoriaEventoRepository repo;

    public AuditoriaService(AuditoriaEventoRepository repo) {
        this.repo = repo;
    }

    /** Se registra en su propia transacción: no se mezcla con la de la operación que la invoca. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(AuthPrincipal p, String accion, String detalle) {
        try {
            AuditoriaEvento e = new AuditoriaEvento();
            e.setEmpresaId(p.empresaId());
            e.setUsuarioId(p.usuarioId());
            e.setUsuario(recortar(p.usuario(), 60));
            e.setAccion(recortar(accion, 40));
            e.setDetalle(recortar(detalle, 500));
            e.setIp(ipActual());
            repo.save(e);
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar el evento de auditoría {}: {}", accion, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AuditoriaEvento> recientes(Long empresaId) {
        return repo.findTop150ByEmpresaIdOrderByIdDesc(empresaId);
    }

    private static String ipActual() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
                return recortar(a.getRequest().getRemoteAddr(), 45);
            }
        } catch (RuntimeException ignorado) {
            // sin petición activa (p. ej. tareas programadas)
        }
        return null;
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        String limpio = s.replaceAll("\\s+", " ").trim();
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }
}
