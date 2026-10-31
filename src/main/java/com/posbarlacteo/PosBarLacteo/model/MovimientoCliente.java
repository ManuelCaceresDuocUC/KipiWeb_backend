package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Estado de cuenta del cliente: cada cargo, abono, anulación o ajuste de su deuda. */
@Entity
@Table(name = "cliente_movimientos", indexes = {
    @Index(name = "idx_cli_mov_cliente", columnList = "cliente_id, id")
})
@Getter
@Setter
public class MovimientoCliente {

    public static final String CARGO = "CARGO";
    public static final String ABONO = "ABONO";
    public static final String ANULACION = "ANULACION";
    public static final String AJUSTE = "AJUSTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 20)
    private String tipo;

    /** Con signo: cargo y ajuste al alza suman deuda (+); abono, anulación y ajuste a la baja la reducen (−). */
    @Column(nullable = false)
    private Double monto;

    @Column(name = "saldo_resultante", nullable = false)
    private Double saldoResultante;

    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "metodo_pago", length = 20)
    private String metodoPago;

    @Column(length = 255)
    private String motivo;

    @Column(name = "usuario_nombre", length = 100)
    private String usuarioNombre;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @PrePersist
    protected void alCrear() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }
}
