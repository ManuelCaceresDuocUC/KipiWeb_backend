package com.posbarlacteo.PosBarLacteo.security;

import java.util.Locale;

/**
 * Normalización de roles. En la base de datos conviven valores históricos
 * ("admin", "administrador", "ADMIN", "vendedor"...). Hacia el exterior (JWT y respuesta de login)
 * solo existen dos: "admin" y "vendedor".
 */
public final class Roles {

    public static final String ADMIN = "admin";
    public static final String VENDEDOR = "vendedor";

    private Roles() {
    }

    public static String normalizar(String rolCrudo) {
        if (rolCrudo == null) {
            return VENDEDOR;
        }
        String r = rolCrudo.trim().toLowerCase(Locale.ROOT);
        if (r.startsWith("role_")) {
            r = r.substring(5);
        }
        return (r.equals("admin") || r.equals("administrador")) ? ADMIN : VENDEDOR;
    }

    public static boolean esAdmin(String rolCrudo) {
        return ADMIN.equals(normalizar(rolCrudo));
    }
}
