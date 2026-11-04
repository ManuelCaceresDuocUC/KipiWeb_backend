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
import lombok.Getter;
import lombok.Setter;

/** Línea de una compra: cuánto se compró de un producto y a qué valor unitario. */
@Entity
@Table(name = "pos_compra_items")
@Getter
@Setter
public class CompraItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    /** Nombre del producto al momento de comprar (por si luego se renombra). */
    @Column(nullable = false, length = 150)
    private String descripcion;

    @Column(nullable = false)
    private Double cantidad;

    @Column(name = "costo_unitario", nullable = false)
    private Long costoUnitario;

    @Column(nullable = false)
    private Long subtotal;
}
