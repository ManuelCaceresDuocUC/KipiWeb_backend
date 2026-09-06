package com.posbarlacteo.PosBarLacteo.dto;

import java.util.List;

import com.posbarlacteo.PosBarLacteo.model.ItemVenta;

import lombok.Data;

@Data // Si usas Lombok, si no, usa los getters y setters que ya tienes
public class PagoRequest {
    private int monto;
    private List<ItemVenta> items;
    
    private Long usuarioId;
    private Long empresaId; // ✨ Agrega esto si no existe
    private Long clienteId;
    public Long getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(Long empresaId) {
        this.empresaId = empresaId;
    }
}