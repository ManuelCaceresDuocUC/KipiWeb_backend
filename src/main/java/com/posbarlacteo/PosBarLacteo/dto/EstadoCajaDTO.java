package com.posbarlacteo.PosBarLacteo.dto;

public class EstadoCajaDTO {
    
    private boolean abierta;

    public EstadoCajaDTO() {}

    public EstadoCajaDTO(boolean abierta) {
        this.abierta = abierta;
    }

    public boolean isAbierta() {
        return abierta;
    }

    public void setAbierta(boolean abierta) {
        this.abierta = abierta;
    }
}