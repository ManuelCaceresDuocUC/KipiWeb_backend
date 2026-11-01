package com.posbarlacteo.PosBarLacteo.dte;

/** Una línea del documento. Montos en pesos enteros, con IVA incluido (así se cobra al público). */
public record LineaDte(String nombre, double cantidad, long precioUnitario, long monto) {
}
