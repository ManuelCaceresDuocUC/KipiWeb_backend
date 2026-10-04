package com.posbarlacteo.PosBarLacteo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "pos_clientes")
@Data
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✨ CORREGIDO: Cambiado de Long a objeto Empresa para mantener la consistencia JPA
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(nullable = false)
    private String nombre;

    private String rut;
    private String telefono;

    private String email;

    @Column(name = "limite_credito")
    private Double limiteCredito = 0.0;

    @Column(name = "deuda_actual")
    private Double deudaActual = 0.0;
}