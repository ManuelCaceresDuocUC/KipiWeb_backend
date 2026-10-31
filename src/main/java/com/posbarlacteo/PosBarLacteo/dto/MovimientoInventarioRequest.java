package com.posbarlacteo.PosBarLacteo.dto;

import lombok.Data;

@Data
public class MovimientoInventarioRequest {
    private Long productoId;
    /** ENTRADA, MERMA o AJUSTE */
    private String tipo;
    /** ENTRADA/MERMA: unidades. AJUSTE: stock contado físicamente. */
    private Double cantidad;
    private String motivo;
    /** Opcional (solo admin): sucursal distinta a la propia. */
    private Long sucursalId;
}
