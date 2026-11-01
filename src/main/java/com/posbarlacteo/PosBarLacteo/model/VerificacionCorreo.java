package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Código de 6 dígitos enviado al correo del administrador antes de poder registrar una empresa. */
@Entity
@Table(name = "pos_verificaciones_correo", indexes = @Index(name = "idx_verif_correo", columnList = "correo"))
@Getter
@Setter
public class VerificacionCorreo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String correo;

    /** SHA-256 del código (nunca se guarda en claro). */
    @Column(name = "codigo_hash", nullable = false, length = 64)
    private String codigoHash;

    @Column(nullable = false)
    private LocalDateTime creado = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime expira;

    @Column(nullable = false)
    private int intentos = 0;

    /** SHA-256 del token entregado al verificar correctamente; se canjea al registrar la empresa. */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "token_expira")
    private LocalDateTime tokenExpira;

    @Column(nullable = false)
    private boolean usado = false;
}
