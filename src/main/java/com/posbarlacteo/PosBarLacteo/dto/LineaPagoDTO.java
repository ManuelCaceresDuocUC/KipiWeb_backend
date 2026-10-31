package com.posbarlacteo.PosBarLacteo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/** Una forma de pago dentro de la venta: EFECTIVO, TARJETA, TRANSFERENCIA o CREDITO. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LineaPagoDTO {
    private String metodo;
    private Long monto;
    /** Solo tarjeta: GETNET / MERCADOPAGO / TRANSBANK. */
    private String proveedorTarjeta;
    /** N° de operación de la transferencia o del voucher de tarjeta (opcional). */
    private String referencia;
    /** Transferencia: el cajero confirma que el dinero ya llegó a la cuenta. */
    private Boolean confirmado;
}
