package com.posbarlacteo.PosBarLacteo.exception;

/** Conflicto con el estado actual (duplicados, precios cambiados...) (HTTP 409). */
public class ConflictoException extends RuntimeException {
    public ConflictoException(String message) {
        super(message);
    }
}
