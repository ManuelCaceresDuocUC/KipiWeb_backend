package com.posbarlacteo.PosBarLacteo.model;

import lombok.Data;

@Data // Uso Lombok para ahorrarme los getters y setters
public class ItemVenta {
    private Long productoId;
    private Double cantidad; 

    /**
     * Precio con el que el cliente vio el producto. SOLO se respeta en ventas diferidas (hechas sin conexión),
     * donde el cobro ya ocurrió a ese precio. En una venta normal se ignora: manda el precio del servidor.
     */
    private Double precioUnitario;
}