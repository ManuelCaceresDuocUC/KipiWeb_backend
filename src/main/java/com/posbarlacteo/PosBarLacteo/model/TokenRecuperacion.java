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

/** Enlace de recuperación de contraseña de un solo uso (se guarda solo su hash). */
@Entity
@Table(name = "pos_tokens_recuperacion", indexes = @Index(name = "idx_tokrec_hash", columnList = "token_hash"))
@Getter
@Setter
public class TokenRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime creado = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime expira;

    @Column(nullable = false)
    private boolean usado = false;
}
