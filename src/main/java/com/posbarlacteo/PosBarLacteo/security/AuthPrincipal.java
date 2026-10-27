package com.posbarlacteo.PosBarLacteo.security;

/**
 * Identidad del usuario autenticado, tomada del JWT firmado por el servidor.
 * Es la ÚNICA fuente confiable de empresaId / usuarioId / sucursalId: ningún endpoint
 * debe confiar en valores equivalentes enviados por el navegador.
 */
public record AuthPrincipal(Long usuarioId, String usuario, Long empresaId, Long sucursalId, String rol) {

    public boolean esAdmin() {
        return Roles.ADMIN.equals(rol);
    }
}
