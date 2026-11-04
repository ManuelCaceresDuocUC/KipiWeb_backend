package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Registro de una devolución o cambio de mercadería de una venta. Es solo un REGISTRO de control: no mueve la caja,
 * no toca la deuda del cliente ni emite documentos tributarios. Opcionalmente repone el stock.
 */
@Entity
@Table(name = "pos_devoluciones", indexes = {
    @Index(name = "idx_devol_empresa_venta", columnList = "empresa_id, venta_id")
})
@Getter
@Setter
public class Devolucion {

    public static final String DEVOLUCION = "DEVOLUCION";
    public static final String CAMBIO = "CAMBIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "venta_id", nullable = false)
    private Long ventaId;

    /** DEVOLUCION | CAMBIO */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 255)
    private String motivo;

    /** En un cambio: qué se entregó a cambio (texto libre). */
    @Column(length = 255)
    private String nota;

    @Column(name = "repone_stock")
    private Boolean reponeStock = false;

    /** Valor de lo devuelto al precio al que se vendió. Solo referencial. */
    @Column(name = "monto_referencial")
    private Double montoReferencial;

    @Column(length = 100)
    private String usuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("devolucionItems")
    private List<DevolucionItem> items = new ArrayList<>();

    @PrePersist
    protected void alCrear() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }
}
