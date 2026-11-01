package com.posbarlacteo.PosBarLacteo.dte;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que KIPIPOS pide emitir, independiente del proveedor. Para una nota de crédito vienen además
 * los datos de la boleta que se anula (folioReferencia, fechaReferencia).
 */
public record SolicitudDte(Long ventaId, LocalDate fecha, long total, List<LineaDte> lineas,
                           Long folioReferencia, LocalDate fechaReferencia, String razon) {
}
