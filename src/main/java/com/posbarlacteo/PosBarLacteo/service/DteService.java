package com.posbarlacteo.PosBarLacteo.service;

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

            if ("EFECTIVO".equalsIgnoreCase(metodoPago) && config.isEmitirBoletaEfectivo()) {
                debeEmitirDte = true;
            } else if ("TARJETA".equalsIgnoreCase(metodoPago) && config.isEmitirBoletaTarjeta()) {
                debeEmitirDte = true;
            } else if ("TRANSFERENCIA".equalsIgnoreCase(metodoPago) && config.isEmitirBoletaTransferencia()) {
                debeEmitirDte = true;
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

        }  catch (Exception e) {
            // Imprimimos el error claramente en la consola
            System.out.println("🚨 ERROR CRÍTICO HAULMER: " + e.getMessage());
            e.printStackTrace();

            log.error("Error procesando DTE para venta ID {}: {}", venta.getId(), e.getMessage(), e);
            
            // 🚨 COMENTAMOS TEMPORALMENTE EL VOUCHER DE RESPALDO
            // return generarVoucherLocal(venta, request);
            
            // Forzamos a que el error suba al frontend
            throw new RuntimeException("Error en Haulmer: " + e.getMessage());
        }
    }
}