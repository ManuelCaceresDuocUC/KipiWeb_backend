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
@Table(name = "pos_categorias", uniqueConstraints = {
    // Esto permite que distintas empresas tengan la categoría "Bebidas", 
    // pero una misma empresa no puede tener "Bebidas" dos veces.
    @UniqueConstraint(columnNames = {"nombre", "empresa_id"})
})
@Data
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre; // Ej: "Lácteos", "Sándwiches", "Bebidas"

    @Column(name = "activo")
    private boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    // Nota: Como estás usando @Data de Lombok, no necesitas escribir 
    // manualmente getEmpresa() y setEmpresa(). Lombok los genera por ti.
}