package com.posbarlacteo.PosBarLacteo.dte;

/** Credenciales ya descifradas de UNA empresa. Viven solo en memoria durante la llamada al proveedor. */
public record CredencialesDte(String apiKey, String apiSecret, String rutEmisor, String ambiente) {

    public boolean sandbox() {
        return "sandbox".equalsIgnoreCase(ambiente) || "desarrollo".equalsIgnoreCase(ambiente);
    }

    @Override
    public String toString() {
        return "CredencialesDte[rutEmisor=" + rutEmisor + ", ambiente=" + ambiente + "]"; // nunca imprimir llaves
    }
}
