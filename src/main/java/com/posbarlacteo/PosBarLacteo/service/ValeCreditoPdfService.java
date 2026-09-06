package com.posbarlacteo.PosBarLacteo.service;

import java.awt.print.PrinterJob;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.springframework.stereotype.Service;

import com.itextpdf.text.Document;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfWriter;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValeCreditoPdfService {

    public String generarGuardarYImprimirVale(Venta venta, String local) {
        String rutaLocalPath = null;

        // Itera dos veces: true = Copia Local (con firma), false = Copia Cliente (sin firma)
        for (boolean esCopiaLocal : new boolean[]{true, false}) {
            Document documento = null;
            FileOutputStream fos = null;

            try {
                String nombreLocalLimpio = (local != null && !local.isBlank()) 
                        ? local.replaceAll("[^a-zA-Z0-9_-]", "_").toUpperCase() 
                        : "DEFAULT";

                String userHome = System.getProperty("user.home");
                Path rutaDirectorio = Paths.get(userHome, "PosBarLacteo", nombreLocalLimpio, "pendientes");
                Files.createDirectories(rutaDirectorio); 
                
                String sufijo = esCopiaLocal ? "_LOCAL" : "_CLIENTE";
                String nombreArchivo = "VALE_CREDITO_" + venta.getId() + sufijo + ".pdf";
                File archivoPdf = new File(rutaDirectorio.toFile(), nombreArchivo);

                Rectangle pageSize = new Rectangle(226, PageSize.A4.getHeight());
                documento = new Document(pageSize, 5, 5, 10, 10);
                fos = new FileOutputStream(archivoPdf);
                
                PdfWriter.getInstance(documento, fos);
                documento.open();

                Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
                Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
                Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
                DecimalFormat df = new DecimalFormat("#,##0");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

                documento.add(new Paragraph("==========================", bold));
                documento.add(new Paragraph("*** VALE DE CREDITO ***", titulo));
                documento.add(new Paragraph(esCopiaLocal ? "--- COPIA LOCAL ---" : "--- COPIA CLIENTE ---", bold));
                documento.add(new Paragraph("==========================", bold));
                documento.add(new Paragraph("Local: " + (local != null ? local : "DEFAULT"), normal));
                documento.add(new Paragraph("Fecha: " + (venta.getFechaHora() != null ? venta.getFechaHora().format(formatter) : LocalDateTime.now().format(formatter)), normal));
                documento.add(new Paragraph("Venta ID: " + venta.getId(), normal));
                
                if (venta.getCliente() != null) {
                    String clienteNombre = venta.getCliente().getNombre() != null ? venta.getCliente().getNombre().toUpperCase() : "N/A";
                    documento.add(new Paragraph("Cliente: " + clienteNombre, bold));
                    documento.add(new Paragraph("RUT: " + (venta.getCliente().getRut() != null ? venta.getCliente().getRut() : "N/A"), normal));
                }
                documento.add(new Paragraph("--------------------------------", normal));

                documento.add(new Paragraph("DETALLE:", bold));
                if (venta.getDetalles() != null) {
                    for (VentaDetalle detalle : venta.getDetalles()) {
                        Double precioObtenido = detalle.getPrecioUnitario();
                        if (precioObtenido == null && detalle.getProducto() != null) {
                            precioObtenido = detalle.getProducto().getPrecio();
                        }
                        
                        double precioUnitario = (precioObtenido != null) ? precioObtenido : 0.0;
                        double subtotal = detalle.getCantidad() * precioUnitario;
                        String descProducto = (detalle.getProducto() != null && detalle.getProducto().getDescripcion() != null) 
                                ? detalle.getProducto().getDescripcion() 
                                : "Producto";

                        String linea = detalle.getCantidad() + "x " + descProducto 
                                     + " ($" + df.format(precioUnitario) + ") = $" + df.format(subtotal);
                                    
                        documento.add(new Paragraph(linea, normal));
                    }
                }
                documento.add(new Paragraph("--------------------------------", normal));
                documento.add(new Paragraph("TOTAL ADEUDADO: $" + df.format(venta.getTotal()), titulo));
                documento.add(new Paragraph(" ", normal));
                
                if (esCopiaLocal) {
                    documento.add(new Paragraph(" ", normal));
                    documento.add(new Paragraph("__________________________", bold));
                    documento.add(new Paragraph("Firma del Cliente", normal));
                    documento.add(new Paragraph("Acepto la deuda detallada", normal));
                } else {
                    documento.add(new Paragraph("Por favor conserve este", normal));
                    documento.add(new Paragraph("comprobante para sus registros.", normal));
                }
                
                documento.add(new Paragraph(".", normal)); 

                documento.close();
                fos.close();

                log.info("✅ Vale de crédito {} guardado en: {}", sufijo, archivoPdf.getAbsolutePath());
                imprimirTicket(archivoPdf);
                
                if (esCopiaLocal) {
                    rutaLocalPath = archivoPdf.getAbsolutePath();
                }

            } catch (Exception e) {
                log.error("❌ Error al generar vale de crédito: ", e);
                if (documento != null && documento.isOpen()) documento.close();
                try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            }
        }
        
        return rutaLocalPath;
    }

    public String generarGuardarYImprimirAbono(
            String clienteNombre,
            String clienteRut,
            double montoAbonado,
            double saldoAnterior,
            double saldoRestante,
            String metodoPago,
            String local
    ) {
        String rutaLocalPath = null;

        // Itera dos veces: true = Copia Local (con firma), false = Copia Cliente (sin firma)
        for (boolean esCopiaLocal : new boolean[]{true, false}) {
            Document documento = null;
            FileOutputStream fos = null;

            try {
                String nombreSeguro = (clienteNombre != null && !clienteNombre.isBlank()) ? clienteNombre.toUpperCase() : "CLIENTE";
                String rutSeguro = (clienteRut != null && !clienteRut.isBlank()) ? clienteRut : "N/A";
                String metodoPagoSeguro = (metodoPago != null && !metodoPago.isBlank()) ? metodoPago.toUpperCase() : "EFECTIVO";
                String nombreLocalLimpio = (local != null && !local.isBlank()) 
                        ? local.replaceAll("[^a-zA-Z0-9_-]", "_").toUpperCase() 
                        : "DEFAULT";

                String userHome = System.getProperty("user.home");
                Path rutaDirectorio = Paths.get(userHome, "PosBarLacteo", nombreLocalLimpio, "abonos");
                Files.createDirectories(rutaDirectorio);

                String sufijo = esCopiaLocal ? "_LOCAL" : "_CLIENTE";
                String nombreArchivo = "ABONO_" + System.currentTimeMillis() + sufijo + ".pdf";
                File archivoPdf = new File(rutaDirectorio.toFile(), nombreArchivo);

                Rectangle pageSize = new Rectangle(226, PageSize.A4.getHeight());
                documento = new Document(pageSize, 5, 5, 10, 10);
                fos = new FileOutputStream(archivoPdf);

                PdfWriter.getInstance(documento, fos);
                documento.open();

                Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
                Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
                Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
                DecimalFormat df = new DecimalFormat("#,##0");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

                documento.add(new Paragraph("==========================", bold));
                documento.add(new Paragraph("*** COMPROBANTE DE ABONO ***", titulo));
                documento.add(new Paragraph(esCopiaLocal ? "--- COPIA LOCAL ---" : "--- COPIA CLIENTE ---", bold));
                documento.add(new Paragraph("==========================", bold));
                documento.add(new Paragraph("Local: " + (local != null ? local : "DEFAULT"), normal));
                documento.add(new Paragraph("Fecha: " + LocalDateTime.now().format(formatter), normal));
                documento.add(new Paragraph("--------------------------------", normal));

                documento.add(new Paragraph("Cliente: " + nombreSeguro, bold));
                documento.add(new Paragraph("RUT: " + rutSeguro, normal));
                documento.add(new Paragraph("Metodo Pago: " + metodoPagoSeguro, normal));
                documento.add(new Paragraph("--------------------------------", normal));

                documento.add(new Paragraph("Saldo Anterior: $" + df.format(saldoAnterior), normal));
                documento.add(new Paragraph("MONTO ABONADO: $" + df.format(montoAbonado), titulo));
                documento.add(new Paragraph("Saldo Restante: $" + df.format(saldoRestante), bold));
                documento.add(new Paragraph("--------------------------------", normal));
                documento.add(new Paragraph(" ", normal));

                if (esCopiaLocal) {
                    documento.add(new Paragraph(" ", normal));
                    documento.add(new Paragraph("__________________________", bold));
                    documento.add(new Paragraph("Firma del Cliente", normal));
                    documento.add(new Paragraph("Comprobante de Pago de Deuda", normal));
                } else {
                    documento.add(new Paragraph("Gracias por su pago.", normal));
                }

                documento.add(new Paragraph(".", normal));

                documento.close();
                fos.close();

                log.info("✅ Comprobante de abono {} guardado en: {}", sufijo, archivoPdf.getAbsolutePath());
                imprimirTicket(archivoPdf);
                
                if (esCopiaLocal) {
                    rutaLocalPath = archivoPdf.getAbsolutePath();
                }

            } catch (Exception e) {
                log.error("❌ Error al generar comprobante de abono: ", e);
                if (documento != null && documento.isOpen()) documento.close();
                try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            }
        }
        
        return rutaLocalPath;
    }

    private void imprimirTicket(File archivoPdf) {
        try (PDDocument document = PDDocument.load(archivoPdf)) {
            PrintService myPrintService = PrintServiceLookup.lookupDefaultPrintService();

            if (myPrintService != null) {
                log.info("🖨️ Enviando ticket a la impresora: {}", myPrintService.getName());
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPageable(new PDFPageable(document));
                job.setPrintService(myPrintService);
                job.print();
                log.info("🖨️ Ticket impreso correctamente.");
            } else {
                log.warn("⚠️ No se encontró una impresora por defecto instalada en el sistema.");
            }
        } catch (Exception e) {
            log.error("❌ Error al intentar imprimir el comprobante: ", e);
        }
    }
}