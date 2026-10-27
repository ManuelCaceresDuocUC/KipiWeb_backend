package com.posbarlacteo.PosBarLacteo.exception;

/** El usuario no tiene permiso para la acción (HTTP 403). */
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String message) {
        super(message);
    }
}
