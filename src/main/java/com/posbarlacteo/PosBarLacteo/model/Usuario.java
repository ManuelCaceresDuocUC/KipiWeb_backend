package com.posbarlacteo.PosBarLacteo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario", nullable = false)
    private String usuario;

    // WRITE_ONLY: puede recibirse en JSON (login/registro) pero NUNCA se devuelve al cliente
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "contrasena")
    private String contrasena;

    @Column(name = "correo")
    private String correo;

    @Column(name = "rol", nullable = false)
    private String rol = "vendedor";

    /** null = usuarios anteriores a esta columna (se consideran activos). */
    @JsonIgnore
    @Column(name = "activo")
    private Boolean activo = true;

    /** Epoch (ms) desde el cual vale una sesión: los tokens emitidos antes quedan revocados. null = ninguna revocación. */
    @JsonIgnore
    @Column(name = "sesion_desde")
    private Long sesionDesde;

    public Long getSesionDesde() {
        return sesionDesde;
    }

    /** Cierra todas las sesiones abiertas del usuario (cambio de contraseña o de permisos). */
    public void revocarSesiones() {
        this.sesionDesde = System.currentTimeMillis();
    }

    public boolean estaActivo() {
        return !Boolean.FALSE.equals(activo);
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    // ✨ NUEVO CAMPO: Relación con la sucursal
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = true) // Puede ser false si TODO usuario debe tener sucursal
    private Sucursal sucursal;
}