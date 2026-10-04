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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Table(
    name = "pos_usuarios", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"usuario", "empresa_id"})
    }
)
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario", nullable = false)
    private String usuario;

    @Column(name = "contrasena")
    private String contrasena;

    @Column(name = "correo")
    private String correo;

    @Column(name = "rol", nullable = false)
    private String rol = "vendedor";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    // ✨ NUEVO CAMPO: Relación con la sucursal
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = true) // Puede ser false si TODO usuario debe tener sucursal
    private Sucursal sucursal;
}