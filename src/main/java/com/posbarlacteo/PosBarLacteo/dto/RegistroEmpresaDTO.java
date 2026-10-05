package com.posbarlacteo.PosBarLacteo.dto;

import java.util.List;

import lombok.Data;

@Data
public class RegistroEmpresaDTO {

    private EmpresaDTO empresa;
    private List<SucursalDTO> sucursales; // ✨ Lista de sucursales
    private AdminDTO admin;
    private List<UsuarioDTO> empleados;

    @Data
    public static class EmpresaDTO {
        private String rut_empresa;
        private String razon_social;
        private String giro;
    }

    @Data
    public static class SucursalDTO {
        private String nombre;
        private String direccion;
        private String comuna;
    }

    @Data
    public static class AdminDTO {
        private String usuario;
        private String correo;
        private String contrasena;
        private String rol;
    }

    @Data
    public static class UsuarioDTO {
        private String usuario;
        private String contrasena;
        private String rol;
        private String sucursalNombre; // ✨ Sucursal a la que pertenece el empleado
    }
}