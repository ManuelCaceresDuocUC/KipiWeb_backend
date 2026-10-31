package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Historial inmutable de cada cambio de stock (venta, entrada, merma, ajuste...). */
@Entity
@Table(name = "movimientos_inventario", indexes = {
    @Index(name = "idx_mov_inv_empresa_fecha", columnList = "empresa_id, fecha_hora"),
    @Index(name = "idx_mov_inv_producto", columnList = "producto_id, sucursal_id")
})
@Getter
@Setter
public class MovimientoInventario {

    public static final String VENTA = "VENTA";
    public static final String ENTRADA = "ENTRADA";
    public static final String MERMA = "MERMA";
    public static final String AJUSTE = "AJUSTE";
    public static final String INICIAL = "INICIAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 20)
    private String tipo;

    /** Variación con signo: positivo entra, negativo sale. */
    @Column(nullable = false)
    private Double cantidad;

    @Column(name = "stock_resultante", nullable = false)
    private Double stockResultante;

    @Column(length = 255)
    private String motivo;

    @Column(name = "usuario_nombre", length = 100)
    private String usuarioNombre;

    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @PrePersist
    protected void alCrear() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }
}
