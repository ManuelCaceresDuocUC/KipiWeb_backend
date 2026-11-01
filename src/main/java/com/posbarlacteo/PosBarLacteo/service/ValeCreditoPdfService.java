package com.posbarlacteo.PosBarLacteo.service;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;
import com.posbarlacteo.PosBarLacteo.util.PdfTicketUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Vale de crédito y comprobante de abono en formato ticket 80 mm. Se generan EN MEMORIA y se devuelven en
 * base64: la impresión la hace el navegador del local (el servidor no tiene impresora). Cada PDF trae las
 * dos copias (local con firma y cliente) separadas por una línea de corte.
 */
@Slf4j
@Service
public class ValeCreditoPdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private final Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private final Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);

    /** @return PDF en base64, o null si no se pudo generar (la venta ya está registrada, no se interrumpe). */
    public String generarValeBase64(Venta venta, String local) {
        try {
            return PdfTicketUtil.generarBase64(doc -> {
                escribirVale(doc, venta, local, true);
                corte(doc);
                escribirVale(doc, venta, local, false);
            });
        } catch (Exception e) {
            log.error("No se pudo generar el vale de crédito de la venta {}: {}", venta.getId(), e.getMessage(), e);
            return null;
        }
    }

    public String generarAbonoBase64(String clienteNombre, String clienteRut, double montoAbonado,
                                     double saldoAnterior, double saldoRestante, String metodoPago, String local) {
        try {
            return PdfTicketUtil.generarBase64(doc -> {
                escribirAbono(doc, clienteNombre, clienteRut, montoAbonado, saldoAnterior, saldoRestante, metodoPago, local, true);
                corte(doc);
                escribirAbono(doc, clienteNombre, clienteRut, montoAbonado, saldoAnterior, saldoRestante, metodoPago, local, false);
            });
        } catch (Exception e) {
            log.error("No se pudo generar el comprobante de abono: {}", e.getMessage(), e);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------

    private void escribirVale(Document doc, Venta venta, String local, boolean copiaLocal) throws Exception {
        DecimalFormat df = new DecimalFormat("#,##0");
        centrado(doc, "*** VALE DE CRÉDITO ***", titulo);
        centrado(doc, copiaLocal ? "--- COPIA LOCAL ---" : "--- COPIA CLIENTE ---", bold);
        doc.add(new Paragraph("Local: " + (local != null ? local : "-"), normal));
        doc.add(new Paragraph("Fecha: " + (venta.getFechaHora() != null ? venta.getFechaHora() : LocalDateTime.now()).format(FECHA), normal));
        doc.add(new Paragraph("Venta N°: " + venta.getId(), normal));

        if (venta.getCliente() != null) {
            String nombre = venta.getCliente().getNombre() != null ? venta.getCliente().getNombre().toUpperCase() : "N/A";
            doc.add(new Paragraph("Cliente: " + nombre, bold));
            doc.add(new Paragraph("RUT: " + (venta.getCliente().getRut() != null ? venta.getCliente().getRut() : "N/A"), normal));
        }
        doc.add(new Paragraph("--------------------------------", normal));
        doc.add(new Paragraph("DETALLE:", bold));
        if (venta.getDetalles() != null) {
            for (VentaDetalle d : venta.getDetalles()) {
                Double precio = d.getPrecioUnitario();
                if (precio == null && d.getProducto() != null) {
                    precio = d.getProducto().getPrecio();
                }
                double pu = precio != null ? precio : 0.0;
                double cant = d.getCantidad() != null ? d.getCantidad() : 0;
                String desc = d.getProducto() != null && d.getProducto().getDescripcion() != null
                        ? d.getProducto().getDescripcion() : "Producto";
                doc.add(new Paragraph(cant(cant) + "x " + desc + " ($" + df.format(pu) + ") = $" + df.format(cant * pu), normal));
            }
        }
        doc.add(new Paragraph("--------------------------------", normal));

        // Con pago mixto solo la parte a crédito queda adeudada
        double adeudado = venta.getTotal() != null ? venta.getTotal() : 0.0;
        if (venta.getPagos() != null) {
            long credito = venta.getPagos().stream()
                    .filter(p -> "CREDITO".equals(p.getMetodo())).mapToLong(p -> p.getMonto() != null ? p.getMonto() : 0L).sum();
            if (credito > 0) {
                adeudado = credito;
            }
        }
        doc.add(new Paragraph("TOTAL ADEUDADO: $" + df.format(adeudado), titulo));
        pie(doc, copiaLocal, "Acepto la deuda detallada", "Conserve este comprobante.");
    }

    private void escribirAbono(Document doc, String nombre, String rut, double abonado, double anterior,
                               double restante, String metodo, String local, boolean copiaLocal) throws Exception {
        DecimalFormat df = new DecimalFormat("#,##0");
        centrado(doc, "*** COMPROBANTE DE ABONO ***", titulo);
        centrado(doc, copiaLocal ? "--- COPIA LOCAL ---" : "--- COPIA CLIENTE ---", bold);
        doc.add(new Paragraph("Local: " + (local != null ? local : "-"), normal));
        doc.add(new Paragraph("Fecha: " + LocalDateTime.now().format(FECHA), normal));
        doc.add(new Paragraph("--------------------------------", normal));
        doc.add(new Paragraph("Cliente: " + (nombre != null && !nombre.isBlank() ? nombre.toUpperCase() : "CLIENTE"), bold));
        doc.add(new Paragraph("RUT: " + (rut != null && !rut.isBlank() ? rut : "N/A"), normal));
        doc.add(new Paragraph("Método de pago: " + (metodo != null && !metodo.isBlank() ? metodo.toUpperCase() : "EFECTIVO"), normal));
        doc.add(new Paragraph("--------------------------------", normal));
        doc.add(new Paragraph("Saldo anterior: $" + df.format(anterior), normal));
        doc.add(new Paragraph("MONTO ABONADO: $" + df.format(abonado), titulo));
        doc.add(new Paragraph("Saldo restante: $" + df.format(restante), bold));
        doc.add(new Paragraph("--------------------------------", normal));
        pie(doc, copiaLocal, "Comprobante de pago de deuda", "Gracias por su pago.");
    }

    private void pie(Document doc, boolean copiaLocal, String textoFirma, String textoCliente) throws Exception {
        if (copiaLocal) {
            doc.add(new Paragraph("\n\n__________________________", bold));
            doc.add(new Paragraph("Firma del cliente", normal));
            doc.add(new Paragraph(textoFirma, normal));
        } else {
            doc.add(new Paragraph(textoCliente, normal));
        }
    }

    private void corte(Document doc) throws Exception {
        centrado(doc, "\n- - - - - - - cortar aquí - - - - - - -\n", normal);
    }

    private void centrado(Document doc, String texto, Font fuente) throws Exception {
        Paragraph p = new Paragraph(texto, fuente);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    private static String cant(double c) {
        return c % 1 == 0 ? String.valueOf((long) c) : String.valueOf(c);
    }

    /** Utilidad para pruebas/inspección. */
    static String aBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
