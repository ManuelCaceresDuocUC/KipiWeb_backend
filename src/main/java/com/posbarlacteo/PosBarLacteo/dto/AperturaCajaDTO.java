package com.posbarlacteo.PosBarLacteo.dto;

import java.math.BigDecimal;

public class AperturaCajaDTO {
    
    private BigDecimal montoInicial;

    // Constructor vacío
    public AperturaCajaDTO() {
    }

    // Getters y Setters
    public BigDecimal getMontoInicial() {
        return montoInicial;
    }

    public void setMontoInicial(BigDecimal montoInicial) {
        this.montoInicial = montoInicial;
    }
}