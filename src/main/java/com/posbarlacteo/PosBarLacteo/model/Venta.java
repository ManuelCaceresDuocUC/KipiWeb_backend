package com.posbarlacteo.PosBarLacteo.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "pos_ventas", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(name = "uk_venta_idempotencia", columnNames = {"empresa_id", "idempotency_key"})
})
@Data
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double total;

    @Column(name = "metodo_pago")
    private String metodoPago; 

    @Column(name = "fecha_hora")
    private java.time.LocalDateTime fechaHora;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    @JsonManagedReference 
    private List<VentaDetalle> detalles;

    /** COMPLETADA (o null en ventas antiguas) | ANULADA */
    @Column(name = "estado", length = 20)
    private String estado = "COMPLETADA";

    /** Turno de caja en que se cobró; null en ventas anteriores a esta versión. */
    @Column(name = "turno_id")
    private Long turnoId;

    /** Clave enviada por el cliente para que un reintento no duplique la venta. */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "anulada_por", length = 100)
    private String anuladaPor;

    @Column(name = "fecha_anulacion")
    private java.time.LocalDateTime fechaAnulacion;

    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    @com.fasterxml.jackson.annotation.JsonManagedReference("pagos")
    private List<PagoVenta> pagos;

    @PrePersist
    protected void onCreate() {
        this.fechaHora = java.time.LocalDateTime.now();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    // ✨ NUEVO: Relación con Sucursal
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    // Con @Data ya tienes los getters y setters, pero mantenemos los tuyos si prefieres.
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
}