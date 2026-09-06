package com.posbarlacteo.PosBarLacteo.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "pos_ventas_detalles")
@Data
public class VentaDetalle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venta_id")
    @JsonBackReference // <-- ESTO CORTA EL BUCLE HACIA ARRIBA
    private Venta venta; 

    @ManyToOne
    @JoinColumn(name = "producto_id")
    @JsonIgnoreProperties({"receta", "hibernateLazyInitializer", "handler"}) 
    private Producto producto;
    
    private Double cantidad; 
    private Double precioUnitario; 
}