package com.posbarlacteo.PosBarLacteo.dto;

public class MovimientoCajaDTO {
    private String tipo; // Puede ser "ingreso" o "retiro"
    private Double monto;
    private String motivo;

    // Constructor vacío requerido por Jackson (Spring Boot)
    public MovimientoCajaDTO() {}

    public MovimientoCajaDTO(String tipo, Double monto, String motivo) {
        this.tipo = tipo;
        this.monto = monto;
        this.motivo = motivo;
    }

    // Getters y Setters
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}