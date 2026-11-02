package com.posbarlacteo.PosBarLacteo.dte;

/**
 * Credenciales ya descifradas de UNA empresa. Viven solo en memoria durante la llamada al proveedor.
 * apiKey / apiSecret: cada proveedor decide qué significan (p. ej. SimpleFactura: usuario y contraseña).
 * emisor puede ser null (solo las pruebas de conexión no lo necesitan).
 */
public record CredencialesDte(String apiKey, String apiSecret, String rutEmisor, String ambiente, DatosEmisor emisor) {

    public CredencialesDte(String apiKey, String apiSecret, String rutEmisor, String ambiente) {
        this(apiKey, apiSecret, rutEmisor, ambiente, null);
    }

    public boolean sandbox() {
        return "sandbox".equalsIgnoreCase(ambiente) || "desarrollo".equalsIgnoreCase(ambiente);
    }

    @Override
    public String toString() {
        return "CredencialesDte[rutEmisor=" + rutEmisor + ", ambiente=" + ambiente + "]"; // nunca imprimir llaves
    }
}
