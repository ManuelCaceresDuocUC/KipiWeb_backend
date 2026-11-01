package com.posbarlacteo.PosBarLacteo.dte;

/**
 * Estrategia de un proveedor de facturación electrónica. Para sumar uno nuevo: implementar esta interfaz
 * y anotarla con @Component; el registro la detecta sola y aparece disponible por su código().
 */
public interface DteProveedor {

    /** Código guardado en configuracion_dte.proveedor (minúsculas), p. ej. "openfactura". */
    String codigo();

    String nombre();

    /** false = integración construida desde la documentación pero aún sin probar con una cuenta real. */
    boolean verificado();

    ResultadoDte emitirBoleta(SolicitudDte solicitud, CredencialesDte credenciales);

    ResultadoDte emitirNotaCredito(SolicitudDte solicitud, CredencialesDte credenciales);

    /** Consulta liviana al proveedor para validar la API key. No emite documentos. */
    ResultadoPrueba probarConexion(CredencialesDte credenciales);
}
