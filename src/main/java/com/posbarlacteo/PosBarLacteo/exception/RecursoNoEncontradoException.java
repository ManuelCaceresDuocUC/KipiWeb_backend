package com.posbarlacteo.PosBarLacteo.exception;

/** El recurso no existe O pertenece a otra empresa (HTTP 404). No se distingue a propósito. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String message) {
        super(message);
    }
}
