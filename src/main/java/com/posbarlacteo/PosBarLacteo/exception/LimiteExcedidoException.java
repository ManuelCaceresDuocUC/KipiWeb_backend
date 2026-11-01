package com.posbarlacteo.PosBarLacteo.exception;

/** Demasiadas solicitudes desde un mismo origen (HTTP 429). */
public class LimiteExcedidoException extends RuntimeException {
    public LimiteExcedidoException(String message) {
        super(message);
    }
}
