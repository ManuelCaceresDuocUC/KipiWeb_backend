package com.posbarlacteo.PosBarLacteo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

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

    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "api_key", length = 255)
    private String apiKey;

    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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

    // ---- Solo lectura hacia el frontend: nunca se devuelve la llave real ----

    @JsonProperty(value = "apiKeyConfigurada", access = JsonProperty.Access.READ_ONLY)
    public boolean isApiKeyConfigurada() {
        return apiKey != null && !apiKey.isBlank();
    }

    @JsonProperty(value = "apiKeyEnmascarada", access = JsonProperty.Access.READ_ONLY)
    public String getApiKeyEnmascarada() {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String visible = apiKey.length() > 4 ? apiKey.substring(apiKey.length() - 4) : "";
        return "••••••••" + visible;
    }
}
