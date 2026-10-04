package com.posbarlacteo.PosBarLacteo.dto;

import java.util.List;

import lombok.Data;

@Data
public class RegistroEmpresaDTO {
    
    private EmpresaDTO empresa;
    private SucursalDTO sucursal; // ✨ NUEVO: Agregamos el objeto sucursal
    private UsuarioDTO admin;
    private List<UsuarioDTO> empleados;

    @Data
    public static class EmpresaDTO {
        private String rut_empresa;
        private String razon_social;
        private String giro;
        // 🗑️ Eliminamos direccion y comuna de aquí porque ahora van en la sucursal
    }

    // ✨ NUEVA CLASE: Representa los datos de la sucursal matriz que envía el frontend
    @Data
    public static class SucursalDTO {
        private String nombre;
        private String direccion;
        private String comuna;
    }

    @Data
    public static class UsuarioDTO {
        private String usuario;
        private String correo;
        private String contrasena;
        private String rol;
    }
}