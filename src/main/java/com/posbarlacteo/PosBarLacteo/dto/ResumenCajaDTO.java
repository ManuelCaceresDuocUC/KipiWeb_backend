package com.posbarlacteo.PosBarLacteo.dto;

public class ResumenCajaDTO {
    private Double fondoInicial;
    private Double ventasEfectivo;
    private Double ventasTarjeta;
    private Double ventasCredito;
    private Double ingresosExtra;
    private Double abonosCredito; // ✨ CAMPO AGREGADO
    private Double retiros;
    private Double totalEnCaja;

    public ResumenCajaDTO() {}

    public ResumenCajaDTO(Double fondoInicial,
                         Double ventasEfectivo,
                         Double ventasTarjeta,
                         Double ventasCredito,
                         Double ingresosExtra,
                         Double abonosCredito, // ✨ CAMPO AGREGADO
                         Double retiros,
                         Double totalEnCaja) {
        this.fondoInicial = fondoInicial;
        this.ventasEfectivo = ventasEfectivo;
        this.ventasTarjeta = ventasTarjeta;
        this.ventasCredito = ventasCredito;
        this.ingresosExtra = ingresosExtra;
        this.abonosCredito = abonosCredito; // ✨ CAMPO AGREGADO
        this.retiros = retiros;
        this.totalEnCaja = totalEnCaja;
    }

    // Getters y Setters
    public Double getFondoInicial() { return fondoInicial; }
    public void setFondoInicial(Double fondoInicial) { this.fondoInicial = fondoInicial; }

    public Double getVentasEfectivo() { return ventasEfectivo; }
    public void setVentasEfectivo(Double ventasEfectivo) { this.ventasEfectivo = ventasEfectivo; }

    public Double getVentasTarjeta() { return ventasTarjeta; }
    public void setVentasTarjeta(Double ventasTarjeta) { this.ventasTarjeta = ventasTarjeta; }

    public Double getVentasCredito() { return ventasCredito; }
    public void setVentasCredito(Double ventasCredito) { this.ventasCredito = ventasCredito; }

    public Double getIngresosExtra() { return ingresosExtra; }
    public void setIngresosExtra(Double ingresosExtra) { this.ingresosExtra = ingresosExtra; }

    public Double getAbonosCredito() { return abonosCredito; } // ✨ GETTER AGREGADO
    public void setAbonosCredito(Double abonosCredito) { this.abonosCredito = abonosCredito; } // ✨ SETTER AGREGADO

    public Double getRetiros() { return retiros; }
    public void setRetiros(Double retiros) { this.retiros = retiros; }

    public Double getTotalEnCaja() { return totalEnCaja; }
    public void setTotalEnCaja(Double totalEnCaja) { this.totalEnCaja = totalEnCaja; }
}