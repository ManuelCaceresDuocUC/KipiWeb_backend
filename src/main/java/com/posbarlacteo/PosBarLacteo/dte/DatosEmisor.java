package com.posbarlacteo.PosBarLacteo.dte;

import java.util.List;

/**
 * Datos tributarios de la empresa emisora. Algunos proveedores los exigen en cada documento.
 * acteco: códigos de actividad económica del SII (hasta 4); lo exigen las notas de crédito y facturas.
 */
public record DatosEmisor(String razonSocial, String giro, String direccion, String comuna, List<Integer> acteco) {

    public DatosEmisor(String razonSocial, String giro, String direccion, String comuna) {
        this(razonSocial, giro, direccion, comuna, List.of());
    }

    public boolean completo() {
        return noVacio(razonSocial) && noVacio(giro) && noVacio(direccion) && noVacio(comuna);
    }

    public boolean tieneActeco() {
        return acteco != null && !acteco.isEmpty();
    }

    /** Convierte "471910, 620200" en [471910, 620200]; ignora lo que no sea un código de 6 dígitos. */
    public static List<Integer> parsearActeco(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        return java.util.Arrays.stream(texto.split("[,;\\s]+"))
                .filter(t -> t.matches("\\d{6}"))
                .map(Integer::valueOf)
                .distinct()
                .limit(4)
                .toList();
    }

    private static boolean noVacio(String s) {
        return s != null && !s.isBlank();
    }
}
