package com.posbarlacteo.PosBarLacteo.model;

import lombok.Data;

@Data // Uso Lombok para ahorrarme los getters y setters
public class ItemVenta {
    private Long productoId;
    private Double cantidad; 
}