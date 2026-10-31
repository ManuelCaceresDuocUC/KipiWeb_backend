package com.posbarlacteo.PosBarLacteo.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pos_productos", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(name = "uk_producto_codigo_empresa", columnNames = {"codigo_barras", "empresa_id"})
})
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_barras")
    private String codigoBarras;

    @Column(name = "unidad_medida")
    private String unidadMedida;

    @Column(name = "es_insumo")
    private Boolean esInsumo;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private Double precio;

    // LEGADO: columna "stock" original (stock global). Ya no se usa: el stock real vive en inventario_sucursal.
    // Se conserva (siempre 0) porque la columna es NOT NULL; la migración SQL la lee una vez.
    @JsonIgnore
    @Column(name = "stock", nullable = false)
    private Double stockLegacy = 0.0;

    // Stock de la sucursal consultada: se completa en cada respuesta, no se guarda aquí.
    @Transient
    private Double stock;

    // Costo de compra unitario (opcional). Sirve para valorizar el inventario.
    @Column(name = "costo")
    private Double costo;

    @Column(name = "stock_critico")
    private Double stockCritico;

    @Column(name = "activo")
    private Boolean activo = true;

    @JsonIgnore 
    @ManyToOne
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @OneToMany(mappedBy = "productoPrincipal", cascade = CascadeType.ALL, orphanRemoval = true)
    // AGREGAR ESTO:
    @JsonIgnoreProperties({"productoPrincipal"})
    private List<Receta> receta;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "productos"})
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "productos", "usuarios"})
    private Empresa empresa;
}