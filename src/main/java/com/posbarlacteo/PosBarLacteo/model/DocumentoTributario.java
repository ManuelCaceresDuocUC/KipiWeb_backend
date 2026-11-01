package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

/**
 * Registro de cada documento tributario (boleta 39 / nota de crédito 61) pedido al proveedor para una venta.
 * La restricción única (empresa, venta, tipo) impide emitir dos veces el mismo documento.
 */
@Data
@Entity
@Table(name = "pos_documentos_dte",
        uniqueConstraints = @UniqueConstraint(name = "uk_dte_venta_tipo", columnNames = {"empresa_id", "venta_id", "tipo_dte"}),
        indexes = @Index(name = "idx_dte_empresa_estado", columnList = "empresa_id, estado"))
public class DocumentoTributario {

    public static final int BOLETA = 39;
    public static final int NOTA_CREDITO = 61;

    /** Un proceso está hablando con el proveedor en este momento (evita emitir dos veces por dobles clics). */
    public static final String EMITIENDO = "EMITIENDO";
    /** Emitido y con folio. */
    public static final String EMITIDO = "EMITIDO";
    /** No salió (error claro, nada emitido): se puede reintentar. */
    public static final String PENDIENTE = "PENDIENTE";
    /** No se sabe si salió (p. ej. el proveedor no respondió): verificar en su portal antes de reintentar. */
    public static final String REVISAR = "REVISAR";
    /** Boleta que nunca salió y la venta se anuló: ya no corresponde emitirla. */
    public static final String CANCELADO = "CANCELADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "venta_id", nullable = false)
    private Long ventaId;

    @Column(name = "tipo_dte", nullable = false)
    private int tipoDte;

    @Column(length = 30)
    private String proveedor;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    private Long folio;

    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    /** Clave en S3 de la copia del PDF 80 mm (si el almacenamiento está configurado). */
    @Column(name = "pdf_key", length = 255)
    private String pdfKey;

    @Column(name = "creado_por", length = 100)
    private String creadoPor;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_emision")
    private LocalDateTime fechaEmision;

    @PrePersist
    void alCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}
