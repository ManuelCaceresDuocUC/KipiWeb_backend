package com.posbarlacteo.PosBarLacteo.service;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.posbarlacteo.PosBarLacteo.model.Cliente;
import com.posbarlacteo.PosBarLacteo.model.MovimientoCliente;

/** Estado de cuenta del cliente en PDF tamaño carta/A4 (para enviar por correo). */
@Service
public class EstadoCuentaPdfService {

    public byte[] generar(String empresa, Cliente c, List<MovimientoCliente> movimientos) throws Exception {
        DecimalFormat df = new DecimalFormat("#,##0");
        DateTimeFormatter fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font b = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font n = FontFactory.getFont(FontFactory.HELVETICA, 10);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph("Estado de cuenta", h1));
        doc.add(new Paragraph(empresa == null ? "" : empresa, b));
        doc.add(new Paragraph(" ", n));
        doc.add(new Paragraph("Cliente: " + nz(c.getNombre()), n));
        doc.add(new Paragraph("RUT: " + nz(c.getRut()), n));
        double limite = c.getLimiteCredito() != null ? c.getLimiteCredito() : 0.0;
        double deuda = c.getDeudaActual() != null ? c.getDeudaActual() : 0.0;
        doc.add(new Paragraph("Saldo actual adeudado: $" + df.format(deuda), b));
        doc.add(new Paragraph("Límite de crédito: $" + df.format(limite) + "   |   Disponible: $" + df.format(Math.max(0, limite - deuda)), n));
        doc.add(new Paragraph(" ", n));

        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setWidths(new float[] {2.6f, 1.6f, 1.6f, 1.8f, 2.4f});
        for (String h : new String[] {"Fecha", "Tipo", "Monto", "Saldo", "Detalle"}) {
            PdfPCell cell = new PdfPCell(new Paragraph(h, b));
            cell.setBackgroundColor(new java.awt.Color(235, 235, 235));
            t.addCell(cell);
        }
        for (MovimientoCliente m : movimientos) {
            t.addCell(new Paragraph(m.getFechaHora() != null ? m.getFechaHora().format(fecha) : "", n));
            t.addCell(new Paragraph(nz(m.getTipo()), n));
            Paragraph monto = new Paragraph((m.getMonto() != null && m.getMonto() < 0 ? "-$" : "$")
                    + df.format(Math.abs(m.getMonto() != null ? m.getMonto() : 0)), n);
            monto.setAlignment(Element.ALIGN_RIGHT);
            t.addCell(monto);
            t.addCell(new Paragraph("$" + df.format(m.getSaldoResultante() != null ? m.getSaldoResultante() : 0), n));
            String detalle = m.getVentaId() != null ? "Venta N° " + m.getVentaId() : nz(m.getMotivo());
            t.addCell(new Paragraph(detalle, n));
        }
        doc.add(t);
        doc.close();
        return out.toByteArray();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
