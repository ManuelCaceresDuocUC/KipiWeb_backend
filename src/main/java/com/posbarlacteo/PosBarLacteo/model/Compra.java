package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
 * Documento de compra a un proveedor (factura, nota de venta, boleta…). Al registrarla puede subir el stock y
 * actualizar el costo de los productos. Si se registra por error se ANULA (no se borra).
 */
@Entity
@Table(name = "pos_compras", indexes = {
    @Index(name = "idx_compra_empresa_fecha", columnList = "empresa_id, fecha_documento"),
    @Index(name = "idx_compra_empresa_prov", columnList = "empresa_id, proveedor_id")
})
@Getter
@Setter
public class Compra {

    public static final String ACTIVA = "ACTIVA";
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proveedor_id", nullable = false)
    private Long proveedorId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    /** FACTURA | FACTURA_EXENTA | BOLETA | NOTA_VENTA | GUIA | OTRO */
    @Column(name = "tipo_documento", nullable = false, length = 20)
    private String tipoDocumento;

    @Column(length = 30)
    private String folio;

    @Column(name = "fecha_documento", nullable = false)
    private LocalDate fechaDocumento;

    @Column(name = "fecha_vencimiento")
    private LocalDate vencimiento;

    /** Los valores unitarios ingresados no incluyen IVA (se suma 19 % al total, salvo factura exenta). */
    @Column(name = "precios_netos")
    private Boolean preciosNetos = false;

    /** Total del documento en pesos (IVA incluido). */
    @Column(nullable = false)
    private Long total;

    @Column(name = "ingreso_stock")
    private Boolean ingresoStock = false;

    @Column(name = "actualizo_costos")
    private Boolean actualizoCostos = false;

    @Column(nullable = false, length = 10)
    private String estado = ACTIVA;

    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    @Column(length = 255)
    private String nota;

    @Column(length = 100)
    private String usuario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    /** Foto o PDF del documento (clave en el bucket privado). Interna: no se envía al navegador. */
    @Column(name = "adjunto_key", length = 255)
    private String adjuntoKey;

    @Column(name = "adjunto_nombre", length = 255)
    private String adjuntoNombre;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CompraItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CompraPago> pagos = new ArrayList<>();

    @PrePersist
    void antesDeGuardar() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
    }
}
