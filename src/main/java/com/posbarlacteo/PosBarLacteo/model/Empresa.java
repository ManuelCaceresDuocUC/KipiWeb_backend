package com.posbarlacteo.PosBarLacteo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "empresas")
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

    @Column(name = "haulmer_api_key", length = 500)
    private String haulmerApiKey;

    @Column(name = "getnet_client_id")
    private String getnetClientId;

    @Column(nullable = false)
    private Boolean activo = false;
    
    // ✨ NUEVOS CAMPOS PARA FLOW ✨
    @Column(name = "flow_customer_id")
    private String flowCustomerId;

    @Column(name = "estado")
    private String estado;

    @Column(name = "fecha_registro", updatable = false, insertable = false)
    private LocalDateTime fechaRegistro;

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
}