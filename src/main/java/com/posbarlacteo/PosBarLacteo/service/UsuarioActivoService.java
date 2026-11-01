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

    private record Entrada(boolean activo, long venceMs) { }

    private final ConcurrentHashMap<Long, Entrada> cache = new ConcurrentHashMap<>();
    private final UsuarioRepository usuarios;

    public UsuarioActivoService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public boolean activo(Long usuarioId) {
        long ahora = System.currentTimeMillis();
        Entrada e = cache.get(usuarioId);
        if (e != null && e.venceMs() > ahora) {
            return e.activo();
        }
        boolean activo = usuarios.findById(usuarioId).map(u -> u.estaActivo()).orElse(false);
        cache.put(usuarioId, new Entrada(activo, ahora + TTL_MS));
        return activo;
    }

    public void invalidar(Long usuarioId) {
        cache.remove(usuarioId);
    }
}
