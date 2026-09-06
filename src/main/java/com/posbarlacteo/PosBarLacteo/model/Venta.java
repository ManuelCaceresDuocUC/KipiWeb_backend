package com.posbarlacteo.PosBarLacteo.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType; // Esto importa @Entity, @Id, @OneToMany, etc.
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne; // <-- NUEVO IMPORT
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;



@Entity
@Table(name = "pos_ventas")
@Data
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double total;

    @Column(name = "metodo_pago")
    private String metodoPago; // EFECTIVO o TARJETA

    @Column(name = "fecha_hora")
    private java.time.LocalDateTime fechaHora;
    // --- NUEVA RELACIÓN ---
    // Enlaza la venta con el cajero que la realizó
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    // ----------------------

    // Relación con los productos (El detalle)
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    @JsonManagedReference // <-- ESTO PERMITE MOSTRAR LOS DETALLES
    private List<VentaDetalle> detalles;

    // Esto pone la hora automáticamente antes de guardar en la DB
    @PrePersist
    protected void onCreate() {
        this.fechaHora = java.time.LocalDateTime.now();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
}