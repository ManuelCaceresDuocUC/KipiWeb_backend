package com.posbarlacteo.PosBarLacteo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pos_recetas")
@Getter
@Setter
public class Receta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    @JsonIgnoreProperties({"receta", "empresa", "categoria", "venta", "hibernateLazyInitializer", "handler"})
    private Producto productoPrincipal;

    @ManyToOne
    @JoinColumn(name = "insumo_id")
    @JsonIgnoreProperties({"receta", "empresa", "categoria", "hibernateLazyInitializer", "handler"})
    private Producto insumo;

    @Column(name = "cantidad_usada")
    private Double cantidadUsada;
}