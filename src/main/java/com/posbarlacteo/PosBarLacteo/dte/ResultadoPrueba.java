package com.posbarlacteo.PosBarLacteo.dte;

/** verificado=false significa que el proveedor no dio una respuesta concluyente (no que la llave esté mala). */
public record ResultadoPrueba(boolean valido, boolean verificado, String mensaje) {

    public static ResultadoPrueba ok(String mensaje) {
        return new ResultadoPrueba(true, true, mensaje);
    }

    public static ResultadoPrueba rechazada(String mensaje) {
        return new ResultadoPrueba(false, true, mensaje);
    }

    public static ResultadoPrueba inconclusa(String mensaje) {
        return new ResultadoPrueba(false, false, mensaje);
    }
}
