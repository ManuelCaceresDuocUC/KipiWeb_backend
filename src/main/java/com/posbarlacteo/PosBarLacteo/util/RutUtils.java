package com.posbarlacteo.PosBarLacteo.util;

/** Validación y normalización de RUT chileno (módulo 11). */
public final class RutUtils {

    private RutUtils() {
    }

    /** Devuelve el RUT como "12345678-5" (sin puntos, DV en mayúscula) o null si el formato es inválido. */
    public static String normalizar(String rut) {
        if (rut == null) {
            return null;
        }
        String limpio = rut.replace(".", "").replace(" ", "").replace("-", "").toUpperCase();
        if (!limpio.matches("\\d{7,8}[0-9K]")) {
            return null;
        }
        return limpio.substring(0, limpio.length() - 1) + "-" + limpio.charAt(limpio.length() - 1);
    }

    public static boolean esValido(String rut) {
        String n = normalizar(rut);
        if (n == null) {
            return false;
        }
        String cuerpo = n.substring(0, n.length() - 2);
        char dv = n.charAt(n.length() - 1);
        return calcularDv(cuerpo) == dv;
    }

    private static char calcularDv(String cuerpo) {
        int suma = 0;
        int factor = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += (cuerpo.charAt(i) - '0') * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int resto = 11 - (suma % 11);
        if (resto == 11) return '0';
        if (resto == 10) return 'K';
        return (char) ('0' + resto);
    }
}
