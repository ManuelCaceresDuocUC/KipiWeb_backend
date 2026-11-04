package com.posbarlacteo.PosBarLacteo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Proveedor de la empresa (a quien se le compra mercadería). Nunca se borra: se desactiva, para conservar el historial. */
@Entity
@Table(name = "pos_proveedores", indexes = {
    @Index(name = "idx_proveedor_empresa", columnList = "empresa_id")
})
@Getter
@Setter
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 12)
    private String rut;

    @Column(length = 40)
    private String telefono;

    @Column(length = 120)
    private String email;

    @Column(length = 300)
    private String notas;

    @Column(nullable = false)
    private Boolean activo = true;
}
