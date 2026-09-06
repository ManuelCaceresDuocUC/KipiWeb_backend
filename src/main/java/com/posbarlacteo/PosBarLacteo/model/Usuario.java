package com.posbarlacteo.PosBarLacteo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "pos_usuarios")
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre de usuario para iniciar sesión (ej: "admin", "juan_vendedor")
    @Column(name = "usuario", unique = true, nullable = false)
    private String usuario;

    @Column(name = "contrasena")
    private String contrasena;

    // ✨ NUEVO CAMPO: Correo de contacto (opcional en BD para que los vendedores no lo requieran)
    @Column(name = "correo")
    private String correo;

    @Column(name = "rol", nullable = false)
    private String rol = "vendedor";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
}