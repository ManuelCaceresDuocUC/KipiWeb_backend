package com.posbarlacteo.PosBarLacteo.dte;

/**
 * Falla al emitir. posibleDuplicado=true cuando no se sabe si el proveedor alcanzó a emitir (p. ej. se agotó
 * el tiempo esperando la respuesta): en ese caso NO se debe reintentar solo, hay que verificar en el portal.
 */
public class DteException extends RuntimeException {

    private final boolean posibleDuplicado;

    public DteException(String message) {
        this(message, false);
    }

    public DteException(String message, boolean posibleDuplicado) {
        super(message);
        this.posibleDuplicado = posibleDuplicado;
    }

    public boolean posibleDuplicado() {
        return posibleDuplicado;
    }
}
