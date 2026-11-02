package com.posbarlacteo.PosBarLacteo.dte;

/** Datos tributarios de la empresa emisora (de su registro en KIPIPOS). Algunos proveedores los exigen en cada documento. */
public record DatosEmisor(String razonSocial, String giro, String direccion, String comuna) {

    public boolean completo() {
        return noVacio(razonSocial) && noVacio(giro) && noVacio(direccion) && noVacio(comuna);
    }

    private static boolean noVacio(String s) {
        return s != null && !s.isBlank();
    }
}
