package com.posbarlacteo.PosBarLacteo.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "turnos_caja")
public class TurnoCaja {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cajero_id", nullable = false)
    private Long cajeroId;

    @Column(name = "monto_apertura", nullable = false)
    private BigDecimal montoApertura;

    @Column(nullable = false)
    private String estado; 

    @Column(name = "fecha_apertura")
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "ventas_efectivo")
    private BigDecimal ventasEfectivo = BigDecimal.ZERO;

    @Column(name = "ventas_tarjeta")
    private BigDecimal ventasTarjeta = BigDecimal.ZERO;

    @Column(name = "ventas_credito")
    private BigDecimal ventasCredito = BigDecimal.ZERO;

    @Column(name = "ingresos_extra")
    private BigDecimal ingresosExtra = BigDecimal.ZERO;

    // ✨ NUEVO: Campo para registrar los abonos a crédito pagados en esta caja
    @Column(name = "abonos_credito")
    private BigDecimal abonosCredito = BigDecimal.ZERO;

    @Column(name = "retiros")
    private BigDecimal retiros = BigDecimal.ZERO;

    // Campos para el cierre y cuadratura de caja
    @Column(name = "total_sistema")
    private BigDecimal totalSistema;

    @Column(name = "total_real_fisico")
    private BigDecimal totalRealFisico;

    @Column(name = "diferencia")
    private BigDecimal diferencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    public TurnoCaja() {
    }

    // --- Getters y Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCajeroId() { return cajeroId; }
    public void setCajeroId(Long cajeroId) { this.cajeroId = cajeroId; }

    public BigDecimal getMontoApertura() { return montoApertura; }
    public void setMontoApertura(BigDecimal montoApertura) { this.montoApertura = montoApertura; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(LocalDateTime fechaApertura) { this.fechaApertura = fechaApertura; }

    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }

    public BigDecimal getVentasEfectivo() { return ventasEfectivo; }
    public void setVentasEfectivo(BigDecimal ventasEfectivo) { this.ventasEfectivo = ventasEfectivo; }

    public BigDecimal getVentasTarjeta() { return ventasTarjeta; }
    public void setVentasTarjeta(BigDecimal ventasTarjeta) { this.ventasTarjeta = ventasTarjeta; }

    public BigDecimal getVentasCredito() { return ventasCredito; }
    public void setVentasCredito(BigDecimal ventasCredito) { this.ventasCredito = ventasCredito; }

    public BigDecimal getIngresosExtra() { return ingresosExtra; }
    public void setIngresosExtra(BigDecimal ingresosExtra) { this.ingresosExtra = ingresosExtra; }

    // ✨ NUEVO: Getter y Setter de abonosCredito
    public BigDecimal getAbonosCredito() { return abonosCredito; }
    public void setAbonosCredito(BigDecimal abonosCredito) { this.abonosCredito = abonosCredito; }

    public BigDecimal getRetiros() { return retiros; }
    public void setRetiros(BigDecimal retiros) { this.retiros = retiros; }

    public BigDecimal getTotalSistema() { return totalSistema; }
    public void setTotalSistema(BigDecimal totalSistema) { this.totalSistema = totalSistema; }

    public BigDecimal getTotalRealFisico() { return totalRealFisico; }
    public void setTotalRealFisico(BigDecimal totalRealFisico) { this.totalRealFisico = totalRealFisico; }

    public BigDecimal getDiferencia() { return diferencia; }
    public void setDiferencia(BigDecimal diferencia) { this.diferencia = diferencia; }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
}