package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Pago (o abono) hecho al proveedor por una compra. No mueve la caja del turno. */
@Entity
@Table(name = "pos_compra_pagos")
@Getter
@Setter
public class CompraPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @Column(nullable = false)
    private Long monto;

    /** EFECTIVO | TRANSFERENCIA | OTRO */
    @Column(nullable = false, length = 20)
    private String metodo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 255)
    private String nota;

    @Column(length = 100)
    private String usuario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void antesDeGuardar() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
    }
}
