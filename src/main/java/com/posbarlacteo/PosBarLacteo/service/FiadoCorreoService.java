package com.posbarlacteo.PosBarLacteo.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.model.Cliente;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;
import com.posbarlacteo.PosBarLacteo.repository.ClienteRepository;

import jakarta.annotation.PreDestroy;

/**
 * Comprobante automático por correo cuando se anota un fiado: el cliente se entera de lo que quedó a su nombre,
 * con el detalle y lo que debe en total. Es una transparencia más para el cliente; nunca interrumpe la venta:
 * si no hay correo, el servicio de correo está apagado o falla el envío, solo queda un aviso en el log.
 */
@Service
public class FiadoCorreoService {

    private static final Logger log = LoggerFactory.getLogger(FiadoCorreoService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MailService mailService;
    private final ClienteRepository clienteRepository;
    /** Un solo hilo: los correos salen en orden y sin frenar la caja. */
    private final ExecutorService cola = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "correo-fiado");
        t.setDaemon(true);
        return t;
    });

    public FiadoCorreoService(MailService mailService, ClienteRepository clienteRepository) {
        this.mailService = mailService;
        this.clienteRepository = clienteRepository;
    }

    @PreDestroy
    void cerrar() {
        cola.shutdown();
    }

    /**
     * Arma el correo con los datos de la venta (en el hilo de la petición, donde se pueden leer sus relaciones) y
     * lo envía en segundo plano.
     *
     * @return true si el correo quedó en cola de envío; false si no corresponde o no se puede enviar
     */
    public boolean enviarComprobante(Venta venta, long montoCredito, String valePdfBase64, String numeroVale) {
        try {
            Cliente cliente = venta.getCliente();
            if (cliente == null || !mailService.habilitado()) return false;
            String destino = cliente.getEmail() == null ? "" : cliente.getEmail().trim();
            if (destino.isEmpty()) return false;
            destino = MailService.validarDestino(destino);

            Long empresaId = venta.getEmpresa().getId();
            String empresa = venta.getEmpresa().getRazonSocial() != null ? venta.getEmpresa().getRazonSocial() : "tu comercio";
            Double saldo = clienteRepository.saldoActual(cliente.getId(), empresaId);
            LocalDateTime cuando = venta.getOcurridaEn() != null ? venta.getOcurridaEn() : venta.getFechaHora();

            StringBuilder m = new StringBuilder();
            m.append("Hola ").append(cliente.getNombre()).append(",\n\n")
             .append("Se registró una compra a crédito (fiado) a tu nombre en ").append(empresa).append(".\n\n");
            if (cuando != null) m.append("Fecha de la compra: ").append(cuando.format(FECHA)).append('\n');
            if (numeroVale != null) m.append("Vale N°: ").append(numeroVale).append('\n');
            m.append("\nDetalle:\n");
            if (venta.getDetalles() != null) {
                for (VentaDetalle d : venta.getDetalles()) {
                    double cant = d.getCantidad() == null ? 0 : d.getCantidad();
                    double precio = d.getPrecioUnitario() == null ? 0 : d.getPrecioUnitario();
                    m.append(" - ").append(cantidad(cant)).append(" x ").append(d.getProducto().getDescripcion())
                     .append("  ").append(clp(precio)).append(" c/u = ").append(clp(cant * precio)).append('\n');
                }
            }
            m.append("\nMonto a crédito de esta compra: ").append(clp(montoCredito)).append('\n');
            if (saldo != null) m.append("Total que adeudas hoy en ").append(empresa).append(": ").append(clp(saldo)).append('\n');
            m.append("\nSi no reconoces esta compra, comunícate con el local lo antes posible.\n\n")
             .append("Saludos,\n").append(empresa).append("\n(Enviado automáticamente mediante KIPI)");

            final String para = destino;
            final String asunto = "Comprobante de compra a crédito - " + empresa;
            final String cuerpo = m.toString();
            final byte[] pdf = decodificar(valePdfBase64);
            final String remitente = empresa;

            cola.submit(() -> {
                try {
                    if (pdf != null) {
                        mailService.enviarConAdjunto(para, remitente, asunto, cuerpo, "comprobante-credito.pdf", pdf);
                    } else {
                        mailService.enviarTexto(para, asunto, cuerpo, null);
                    }
                } catch (RuntimeException e) {
                    log.warn("No se pudo enviar el comprobante de fiado de la venta (cliente {}): {}", cliente.getId(), e.getMessage());
                }
            });
            return true;
        } catch (RuntimeException e) {
            log.warn("No se pudo preparar el comprobante de fiado: {}", e.getMessage());
            return false;
        }
    }

    private static byte[] decodificar(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String cantidad(double c) {
        return c == Math.rint(c) ? String.valueOf((long) c) : String.format(Locale.ROOT, "%.2f", c);
    }

    private static String clp(double v) {
        return "$" + String.format(Locale.GERMANY, "%,d", Math.round(v));
    }
}
