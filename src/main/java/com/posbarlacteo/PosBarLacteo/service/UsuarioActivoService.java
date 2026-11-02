package com.posbarlacteo.PosBarLacteo.service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;

/**
 * Indica si un usuario sigue activo. Se consulta en cada petición, por eso va con caché de 30 s:
 * un usuario desactivado pierde el acceso en segundos aunque su token siga vigente.
 */
@Service
public class UsuarioActivoService {

    private static final long TTL_MS = 30_000;

    private record Entrada(boolean activo, Long sesionDesdeMs, long venceMs) { }

    private final ConcurrentHashMap<Long, Entrada> cache = new ConcurrentHashMap<>();
    private final UsuarioRepository usuarios;

    public UsuarioActivoService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    private Entrada entrada(Long usuarioId) {
        long ahora = System.currentTimeMillis();
        Entrada e = cache.get(usuarioId);
        if (e != null && e.venceMs() > ahora) {
            return e;
        }
        Entrada nueva = usuarios.findById(usuarioId)
                .map(u -> new Entrada(u.estaActivo(), u.getSesionDesde(), ahora + TTL_MS))
                .orElse(new Entrada(false, null, ahora + TTL_MS));
        cache.put(usuarioId, nueva);
        return nueva;
    }

    public boolean activo(Long usuarioId) {
        return entrada(usuarioId).activo();
    }

    /** false si el token (emitido en emitidoSeg) es anterior a una revocación de sesiones del usuario. */
    public boolean sesionVigente(Long usuarioId, long emitidoSeg) {
        Long desde = entrada(usuarioId).sesionDesdeMs();
        return desde == null || emitidoSeg >= desde / 1000L;
    }

    public void invalidar(Long usuarioId) {
        cache.remove(usuarioId);
    }
}
