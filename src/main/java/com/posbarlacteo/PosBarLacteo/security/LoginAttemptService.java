package com.posbarlacteo.PosBarLacteo.security;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Límite simple de intentos fallidos de login (anti fuerza bruta).
 * En memoria: suficiente para una sola instancia en Render. Si se escala a varias
 * instancias hay que moverlo a Redis/base de datos.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_INTENTOS = 8;
    private static final long VENTANA_MS = 15L * 60L * 1000L;
    private static final int MAX_REGISTROS = 10_000;

    private record Registro(int intentos, long inicio) {
    }

    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();

    public boolean bloqueado(String clave) {
        Registro r = registros.get(clave);
        if (r == null) {
            return false;
        }
        if (System.currentTimeMillis() - r.inicio() > VENTANA_MS) {
            registros.remove(clave);
            return false;
        }
        return r.intentos() >= MAX_INTENTOS;
    }

    public void fallo(String clave) {
        long ahora = System.currentTimeMillis();
        if (registros.size() > MAX_REGISTROS) {
            registros.entrySet().removeIf(e -> ahora - e.getValue().inicio() > VENTANA_MS);
        }
        registros.merge(clave, new Registro(1, ahora), (viejo, nuevo) ->
                ahora - viejo.inicio() > VENTANA_MS ? nuevo : new Registro(viejo.intentos() + 1, viejo.inicio()));
    }

    public void exito(String clave) {
        registros.remove(clave);
    }
}
