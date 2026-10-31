package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Una línea de pago de una venta. Una venta puede tener varias (pago mixto). */
@Entity
@Table(name = "pos_ventas_pagos")
@Getter
@Setter
public class PagoVenta {

    public static final String EFECTIVO = "EFECTIVO";
    public static final String TARJETA = "TARJETA";
    public static final String TRANSFERENCIA = "TRANSFERENCIA";
    public static final String CREDITO = "CREDITO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    @JsonBackReference("pagos")
    private Venta venta;

    @Column(nullable = false, length = 20)
    private String metodo;

    /** Pesos chilenos enteros. */
    @Column(nullable = false)
    private Long monto;

    /** GETNET / MERCADOPAGO / TRANSBANK cuando es tarjeta. */
    @Column(name = "proveedor_tarjeta", length = 20)
    private String proveedorTarjeta;

    /** N° de operación / referencia de la transferencia o del voucher de la tarjeta. */
    @Column(length = 100)
    private String referencia;

    /** El cajero confirmó haber recibido el dinero (transferencia) o la aprobación (tarjeta). */
    @Column(nullable = false)
    private boolean confirmado = true;

    @Column(name = "confirmado_por", length = 100)
    private String confirmadoPor;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    /** Clave del objeto en el bucket (R2) del comprobante adjunto; se completa en la etapa 3B. */
    @Column(name = "comprobante_key", length = 255)
    private String comprobanteKey;

    @Column(name = "comprobante_nombre", length = 255)
    private String comprobanteNombre;

    @Column(name = "comprobante_tipo", length = 100)
    private String comprobanteTipo;
}
