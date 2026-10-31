package com.posbarlacteo.PosBarLacteo.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import com.posbarlacteo.PosBarLacteo.dto.PagoRequest;
import com.posbarlacteo.PosBarLacteo.model.ConfiguracionDte; // Entidad/Modelo de config DTE
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.repository.ConfiguracionDteRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DteService {

    private static final Logger log = LoggerFactory.getLogger(DteService.class);

    private final ConfiguracionDteRepository configDteRepository;
    private final VoucherService voucherService; // Tu servicio de voucher local existente
    private final OpenFacturaService openFacturaService; // Servicio de integración API OpenFactura

    public DteService(ConfiguracionDteRepository configDteRepository,
                      VoucherService voucherService,
                      OpenFacturaService openFacturaService) {
        this.configDteRepository = configDteRepository;
        this.voucherService = voucherService;
        this.openFacturaService = openFacturaService;
    }

    private String generarVoucherLocal(Venta venta, PagoRequest request) {
    try {
            return voucherService.generarVoucherBase64(venta, request.getItems());
        } catch (Exception e) {
            log.error("Error generando voucher local para venta ID {}: {}", venta.getId(), e.getMessage(), e);
            return "";
        }
    }

    /**
     * Determina si emite un DTE oficial con el proveedor o genera el voucher informativo local.
     */
    public String obtenerDocumentoVenta(Venta venta, PagoRequest request, String metodoPago) {
        return obtenerDocumentoVenta(venta, request, List.of(metodoPago));
    }

    /**
     * Con pago mixto se emite el documento por el total si ALGUNO de los medios usados está configurado
     * para emitir; el fiado (crédito) nunca emite en el momento de la venta.
     */
    public String obtenerDocumentoVenta(Venta venta, PagoRequest request, Collection<String> metodosPago) {
        try {
            // 1. Buscar la configuración DTE del cliente por su empresaId
            ConfiguracionDte config = configDteRepository.findByEmpresaId(request.getEmpresaId())
                    .orElse(null);

            // 2. Si no tiene configuración o está deshabilitado -> Voucher Local Informativo
            if (config == null || "NINGUNO".equalsIgnoreCase(config.getProveedor()) || !config.isActivo()) {
                return generarVoucherLocal(venta, request);
            }

            // 3. Verificar si aplica emitir DTE según el medio de pago (Evita doble IVA en tarjetas)
            boolean debeEmitirDte = false;
            for (String metodoPago : metodosPago) {
                if (("EFECTIVO".equalsIgnoreCase(metodoPago) && config.isEmitirBoletaEfectivo())
                        || ("TARJETA".equalsIgnoreCase(metodoPago) && config.isEmitirBoletaTarjeta())
                        || ("TRANSFERENCIA".equalsIgnoreCase(metodoPago) && config.isEmitirBoletaTransferencia())) {
                    debeEmitirDte = true;
                }
            }

            // 4. Si no aplica DTE para este medio de pago -> Voucher Local Informativo
            if (!debeEmitirDte) {
                return generarVoucherLocal(venta, request);
            }

            // 5. Emitir DTE según el proveedor configurado
            switch (config.getProveedor().toUpperCase()) {
                case "OPENFACTURA":
                    return openFacturaService.emitirBoletaOpenFactura(venta, request, config);

                // Aquí puedes agregar más proveedores fácilmente en el futuro:
                // case "LIBREDTE":
                //     return libreDteService.emitirBoleta(venta, request, config);

                default:
                    return generarVoucherLocal(venta, request);
            }

        } catch (Exception e) {
            log.error("Error procesando DTE para venta ID {}: {}", venta.getId(), e.getMessage(), e);
            // La venta ya quedó registrada; se informa claro para que no se repita el cobro.
            throw new com.posbarlacteo.PosBarLacteo.exception.NegocioException(
                    "La venta N° " + venta.getId() + " se registró, pero no se pudo emitir el documento tributario: "
                    + e.getMessage() + ". No repitas el cobro; revisa la configuración DTE.");
        }
    }
}