package com.posbarlacteo.PosBarLacteo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.posbarlacteo.PosBarLacteo.model.ItemVenta;

import lombok.Data;

/**
 * Solicitud de cobro. El cliente solo aporta items, monto esperado y cliente opcional.
 * empresaId/usuarioId los fija el servidor desde el token (nunca se confía en el cuerpo).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PagoRequest {
    private int monto;
    private List<ItemVenta> items;
    private Long clienteId;

    /** Formas de pago. Si viene vacío, el endpoint antiguo (efectivo/cobrar/credito) arma una sola línea por el total. */
    private List<LineaPagoDTO> pagos;

    /** Clave única por intento de venta: un reintento con la misma clave devuelve la venta ya registrada. */
    private String idempotencyKey;

    /** Venta hecha sin conexión y enviada después: se acepta aunque cambien precios o stock desde que se cobró. */
    private boolean diferida;

    /** Momento real de la venta en el local (ISO-8601). Solo informativo: la fecha oficial es la del envío. */
    private String ocurrioEn;

    // Los completa PagoController desde el token; se usan internamente (DteService / voucher).
    private Long usuarioId;
    private Long empresaId;
}
