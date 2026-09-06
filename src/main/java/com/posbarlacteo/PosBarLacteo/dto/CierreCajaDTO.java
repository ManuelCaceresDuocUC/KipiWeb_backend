package com.posbarlacteo.PosBarLacteo.dto;

import java.math.BigDecimal;

public class CierreCajaDTO {
    private BigDecimal totalSistema;
    private BigDecimal totalRealFisico;
    private BigDecimal diferencia;
    private BigDecimal ventasEfectivo;
    private BigDecimal ventasTarjeta;
    private BigDecimal ingresosExtra;
    private BigDecimal abonosCredito; // ✨ NUEVO
    private BigDecimal retiros;

    public CierreCajaDTO() {}

    // Getters y Setters
    public BigDecimal getTotalSistema() { return totalSistema; }
    public void setTotalSistema(BigDecimal totalSistema) { this.totalSistema = totalSistema; }

    public BigDecimal getTotalRealFisico() { return totalRealFisico; }
    public void setTotalRealFisico(BigDecimal totalRealFisico) { this.totalRealFisico = totalRealFisico; }

    public BigDecimal getDiferencia() { return diferencia; }
    public void setDiferencia(BigDecimal diferencia) { this.diferencia = diferencia; }

    public BigDecimal getVentasEfectivo() { return ventasEfectivo; }
    public void setVentasEfectivo(BigDecimal ventasEfectivo) { this.ventasEfectivo = ventasEfectivo; }

    public BigDecimal getVentasTarjeta() { return ventasTarjeta; }
    public void setVentasTarjeta(BigDecimal ventasTarjeta) { this.ventasTarjeta = ventasTarjeta; }

    public BigDecimal getIngresosExtra() { return ingresosExtra; }
    public void setIngresosExtra(BigDecimal ingresosExtra) { this.ingresosExtra = ingresosExtra; }

    public BigDecimal getAbonosCredito() { return abonosCredito; } // ✨ GETTER
    public void setAbonosCredito(BigDecimal abonosCredito) { this.abonosCredito = abonosCredito; } // ✨ SETTER

    public BigDecimal getRetiros() { return retiros; }
    public void setRetiros(BigDecimal retiros) { this.retiros = retiros; }
}