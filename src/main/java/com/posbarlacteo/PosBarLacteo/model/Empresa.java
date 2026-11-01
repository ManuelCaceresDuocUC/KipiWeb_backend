package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "empresas")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rut_empresa", nullable = false, unique = true, length = 12)
    private String rutEmpresa;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    // Se quitó nullable=false temporalmente, o puedes dejarlo y enviar un string vacío "" desde tu DTO
    @Column
    private String giro;

    @Column
    private String direccion;

    @Column(length = 100)
    private String comuna;

    // LEGADO: la API Key vigente vive en configuracion_dte. Nunca se serializa.
    @JsonIgnore
    @Column(name = "haulmer_api_key", length = 500)
    private String haulmerApiKey;

    @JsonIgnore
    @Column(name = "getnet_client_id")
    private String getnetClientId;

    @Column(nullable = false)
    private Boolean activo = false;
    
    // ✨ NUEVOS CAMPOS PARA FLOW ✨
    @JsonIgnore
    @Column(name = "flow_customer_id")
    private String flowCustomerId;

    @Column(name = "estado")
    private String estado;

    @Column(name = "fecha_registro", updatable = false, insertable = false)
    private LocalDateTime fechaRegistro;

    @JsonIgnore
    @Column(name = "flow_subscription_id")
    private String flowSubscriptionId;

    // ─── CONSTRUCTORES ───
    public Empresa() {
    }

    public Empresa(Long id) {
        this.id = id;
    }

    // ─── GETTERS Y SETTERS ───
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRutEmpresa() {
        return rutEmpresa;
    }

    public void setRutEmpresa(String rutEmpresa) {
        this.rutEmpresa = rutEmpresa;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getGiro() {
        return giro;
    }

    public void setGiro(String giro) {
        this.giro = giro;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getComuna() {
        return comuna;
    }

    public void setComuna(String comuna) {
        this.comuna = comuna;
    }

    public String getHaulmerApiKey() {
        return haulmerApiKey;
    }

    public void setHaulmerApiKey(String haulmerApiKey) {
        this.haulmerApiKey = haulmerApiKey;
    }

    public String getGetnetClientId() {
        return getnetClientId;
    }

    public void setGetnetClientId(String getnetClientId) {
        this.getnetClientId = getnetClientId;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public String getFlowCustomerId() {
        return flowCustomerId;
    }

    public void setFlowCustomerId(String flowCustomerId) {
        this.flowCustomerId = flowCustomerId;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
    public String getFlowSubscriptionId() {
        return flowSubscriptionId;
    }

    public void setFlowSubscriptionId(String flowSubscriptionId) {
        this.flowSubscriptionId = flowSubscriptionId;
    }

    // Política de inventario por empresa. null = comportamiento histórico (se permite vender sin stock).
    @Column(name = "permite_stock_negativo")
    private Boolean permiteStockNegativo;

    public Boolean getPermiteStockNegativo() {
        return permiteStockNegativo;
    }

    public void setPermiteStockNegativo(Boolean permiteStockNegativo) {
        this.permiteStockNegativo = permiteStockNegativo;
    }

    // Fiado (venta a crédito) por empresa. null = deshabilitado: hay que activarlo explícitamente.
    @Column(name = "permite_fiado")
    private Boolean permiteFiado;

    public Boolean getPermiteFiado() {
        return permiteFiado;
    }

    public void setPermiteFiado(Boolean permiteFiado) {
        this.permiteFiado = permiteFiado;
    }

    // ---- Estado de la suscripción (sincronizado con Flow, ver SuscripcionService) ----

    /** Desde cuándo Flow reporta una factura vencida; null si está al día. */
    @JsonIgnore
    @Column(name = "mora_desde")
    private LocalDateTime moraDesde;

    /** Si la suscripción fue cancelada: hasta cuándo alcanza el período ya pagado. */
    @JsonIgnore
    @Column(name = "acceso_hasta")
    private LocalDateTime accesoHasta;

    @JsonIgnore
    @Column(name = "suscripcion_cancelada")
    private Boolean suscripcionCancelada;

    @JsonIgnore
    @Column(name = "flow_ultima_sync")
    private LocalDateTime flowUltimaSync;

    /** Último status de la suscripción informado por Flow (0 inactiva, 1 activa, 2 trial, 4 cancelada). */
    @JsonIgnore
    @Column(name = "flow_status")
    private Integer flowStatus;

    /** Último "morose" informado por Flow (0 al día, 1 vencida, 2 pendiente). */
    @JsonIgnore
    @Column(name = "flow_morosidad")
    private Integer flowMorosidad;

    public LocalDateTime getMoraDesde() { return moraDesde; }
    public void setMoraDesde(LocalDateTime moraDesde) { this.moraDesde = moraDesde; }
    public LocalDateTime getAccesoHasta() { return accesoHasta; }
    public void setAccesoHasta(LocalDateTime accesoHasta) { this.accesoHasta = accesoHasta; }
    public Boolean getSuscripcionCancelada() { return suscripcionCancelada; }
    public void setSuscripcionCancelada(Boolean suscripcionCancelada) { this.suscripcionCancelada = suscripcionCancelada; }
    public LocalDateTime getFlowUltimaSync() { return flowUltimaSync; }
    public void setFlowUltimaSync(LocalDateTime flowUltimaSync) { this.flowUltimaSync = flowUltimaSync; }
    public Integer getFlowStatus() { return flowStatus; }
    public void setFlowStatus(Integer flowStatus) { this.flowStatus = flowStatus; }
    public Integer getFlowMorosidad() { return flowMorosidad; }
    public void setFlowMorosidad(Integer flowMorosidad) { this.flowMorosidad = flowMorosidad; }
}
