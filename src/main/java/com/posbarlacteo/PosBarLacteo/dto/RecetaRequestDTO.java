package com.posbarlacteo.PosBarLacteo.dto;

import java.util.List;

import com.posbarlacteo.PosBarLacteo.model.Producto;

import lombok.Data;

@Data
public class RecetaRequestDTO {
    private Producto productoPrincipal;
    private List<IngredienteDTO> ingredientes;
}