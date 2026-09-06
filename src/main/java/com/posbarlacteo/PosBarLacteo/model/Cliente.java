package com.posbarlacteo.PosBarLacteo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "pos_clientes")
@Data
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id")
    private Long empresaId;

    @Column(nullable = false)
    private String nombre;

    private String rut;
    private String telefono;

    // ✨ AGREGAR ESTA LÍNEA
    private String email;

    @Column(name = "limite_credito")
    private Double limiteCredito = 0.0;

    @Column(name = "deuda_actual")
    private Double deudaActual = 0.0;
}