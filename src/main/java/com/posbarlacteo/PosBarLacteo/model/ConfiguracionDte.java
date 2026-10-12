package com.posbarlacteo.PosBarLacteo.model;

import jakarta.persistence.*;
import lombok.Data; // Si usas Lombok para getters/setters

@Data
@Entity
@Table(name = "configuracion_dte")
public class ConfiguracionDte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", unique = true, nullable = false)
    private Long empresaId;

    @Column(name = "proveedor", length = 50)
    private String proveedor = "ninguno"; 

    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "api_secret", length = 255)
    private String apiSecret;

    @Column(name = "rut_emisor", length = 20)
    private String rutEmisor;

    @Column(name = "ambiente", length = 20)
    private String ambiente = "produccion";

    @Column(name = "emitir_boleta_efectivo")
    private boolean emitirBoletaEfectivo = true;

    @Column(name = "emitir_boleta_tarjeta")
    private boolean emitirBoletaTarjeta = false;

    @Column(name = "emitir_boleta_transferencia")
    private boolean emitirBoletaTransferencia = true;

    @Column(name = "activo")
    private boolean activo = true;
}