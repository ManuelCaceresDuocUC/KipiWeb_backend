package com.posbarlacteo.PosBarLacteo.model;

import java.math.BigDecimal;
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

/** Auditoría de movimientos manuales de caja: ingresos, retiros, abonos de clientes y reversas por anulación. */
@Entity
@Table(name = "movimientos_caja", indexes = {
    @Index(name = "idx_mov_caja_turno", columnList = "turno_id")
})
@Getter
@Setter
public class MovimientoCaja {

    public static final String INGRESO = "INGRESO";
    public static final String RETIRO = "RETIRO";
    public static final String ABONO = "ABONO";
    public static final String ANULACION = "ANULACION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private TurnoCaja turno;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 20)
    private String tipo;

    /** EFECTIVO / TARJETA / TRANSFERENCIA cuando aplica. */
    @Column(name = "metodo_pago", length = 20)
    private String metodoPago;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(length = 255)
    private String motivo;

    @Column(name = "usuario_nombre", length = 100)
    private String usuarioNombre;

    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @PrePersist
    protected void alCrear() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }
}
