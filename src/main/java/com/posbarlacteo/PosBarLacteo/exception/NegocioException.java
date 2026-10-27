package com.posbarlacteo.PosBarLacteo.exception;

/** Error de regla de negocio o dato inválido enviado por el cliente (HTTP 400). El mensaje es apto para mostrar al usuario. */
public class NegocioException extends RuntimeException {
    public NegocioException(String message) {
        super(message);
    }
}
