package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

/** Registro de acciones sensibles de una empresa (quién hizo qué y cuándo). Nunca contiene contraseñas ni API Keys. */
@Data
@Entity
@Table(name = "auditoria_eventos", indexes = @Index(name = "idx_auditoria_empresa", columnList = "empresa_id,id"))
public class AuditoriaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario", length = 60)
    private String usuario;

    @Column(name = "accion", length = 40, nullable = false)
    private String accion;

    @Column(name = "detalle", length = 500)
    private String detalle;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
