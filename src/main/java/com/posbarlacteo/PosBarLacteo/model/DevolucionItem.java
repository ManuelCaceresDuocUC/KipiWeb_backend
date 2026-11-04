package com.posbarlacteo.PosBarLacteo.model;

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

@Entity
@Table(name = "pos_devoluciones_items")
@Getter
@Setter
public class DevolucionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference("devolucionItems")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devolucion_id", nullable = false)
    private Devolucion devolucion;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private Double cantidad;

    @Column(name = "precio_unitario")
    private Double precioUnitario;
}
