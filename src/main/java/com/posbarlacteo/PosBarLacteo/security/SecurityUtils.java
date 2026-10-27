package com.posbarlacteo.PosBarLacteo.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.posbarlacteo.PosBarLacteo.exception.AccesoDenegadoException;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** Usuario autenticado de la petición actual. Lanza 403 si no hay sesión válida. */
    public static AuthPrincipal actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new AccesoDenegadoException("Sesión no válida.");
        }
        return principal;
    }
}
