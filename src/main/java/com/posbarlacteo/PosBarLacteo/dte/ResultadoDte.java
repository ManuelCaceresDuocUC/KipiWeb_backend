package com.posbarlacteo.PosBarLacteo.dte;

/** Documento emitido. pdfBase64 puede ser null si el documento salió pero el PDF no se pudo obtener. */
public record ResultadoDte(Long folio, String pdfBase64) {
}
