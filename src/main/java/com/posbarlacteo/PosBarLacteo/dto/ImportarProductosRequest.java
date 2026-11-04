package com.posbarlacteo.PosBarLacteo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/** Carga masiva de productos (hasta 500 filas por envío; el frontend divide archivos grandes). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportarProductosRequest {

    /** true = hace todo el proceso pero no guarda nada: sirve para la vista previa. */
    private boolean soloValidar;

    /** true = si el producto ya existe (mismo código, o mismo nombre cuando no trae código) se actualiza. */
    private boolean actualizarExistentes = true;

    /** true = en productos existentes, el stock del archivo reemplaza al actual (queda como ajuste en el historial). */
    private boolean ajustarStock;

    private List<Fila> filas;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fila {
        /** Número de fila en el archivo del usuario (para ubicar los errores). */
        private Integer fila;
        private String codigoBarras;
        private String descripcion;
        private Double precio;
        private Double costo;
        private Double stock;
        private Double stockCritico;
        private String unidadMedida;
        private String categoria;
        private Boolean pesable;
        private Boolean esInsumo;
    }
}
