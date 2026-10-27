package com.posbarlacteo.PosBarLacteo.exception;

/** Falla de un servicio externo (Flow, proveedor DTE...). Se responde 502 con mensaje seguro para el usuario. */
public class ServicioExternoException extends RuntimeException {
    public ServicioExternoException(String message) {
        super(message);
    }
}
