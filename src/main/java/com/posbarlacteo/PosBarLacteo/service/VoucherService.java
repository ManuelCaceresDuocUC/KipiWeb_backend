package com.posbarlacteo.PosBarLacteo.service;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;

@Service
public class VoucherService {

    @Autowired(required = false)
    private ProductoRepository productoRepository;

    public String generarVoucherBase64(Venta venta, List<?> items) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Rectangle pageSize = new Rectangle(226, 1200); 
            Document document = new Document(pageSize, 8, 8, 10, 10);
            PdfWriter.getInstance(document, baos);

            document.open();

            // 1. COPIA CLIENTE
            agregarCopiaVoucher(document, venta, items, "COPIA CLIENTE");

            // Separador visible
            Paragraph separador = new Paragraph("\n--------------------------------------------\n\n", 
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8));
            separador.setAlignment(Element.ALIGN_CENTER);
            document.add(separador);

            // 2. COPIA LOCAL / CONTROL INTERNO
            agregarCopiaVoucher(document, venta, items, "COPIA LOCAL / CONTROL INTERNO");

            document.close();

            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    private void agregarCopiaVoucher(Document doc, Venta venta, List<?> items, String tipoCopia) throws Exception {
        Font fontAppName = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font fontEmpresa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font fontSubtitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);

        NumberFormat formatMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CL"));
        DateTimeFormatter formatFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        // --- ENCABEZADO MULTIEMPRESA DINO ---
        Empresa empresa = extraerEmpresa(venta, items);

        Paragraph appTitle = new Paragraph("KIPI", fontAppName);
        appTitle.setAlignment(Element.ALIGN_CENTER);
        doc.add(appTitle);

        String nombreEmpresa = (empresa != null && empresa.getRazonSocial() != null && !empresa.getRazonSocial().isBlank()) 
                ? empresa.getRazonSocial() : "POS BAR LÁCTEO";
        Paragraph empresaTitle = new Paragraph(nombreEmpresa, fontEmpresa);
        empresaTitle.setAlignment(Element.ALIGN_CENTER);
        doc.add(empresaTitle);

        // Datos Fiscales y Dirección de la Empresa
        StringBuilder infoEmpresa = new StringBuilder();

        if (empresa != null && empresa.getRutEmpresa() != null && !empresa.getRutEmpresa().isBlank()) {
            infoEmpresa.append("RUT: ").append(empresa.getRutEmpresa()).append("\n");
        }
        if (empresa != null && empresa.getGiro() != null && !empresa.getGiro().isBlank()) {
            infoEmpresa.append("Giro: ").append(empresa.getGiro()).append("\n");
        }

        String dir = (empresa != null && empresa.getDireccion() != null) ? empresa.getDireccion() : "Arturo Prat 527";
        String comuna = (empresa != null && empresa.getComuna() != null) ? empresa.getComuna() : "Curicó";
        infoEmpresa.append(dir).append(", ").append(comuna);

        Paragraph dirParagraph = new Paragraph(infoEmpresa.toString(), fontBody);
        dirParagraph.setAlignment(Element.ALIGN_CENTER);
        doc.add(dirParagraph);

        Paragraph avisoLegal = new Paragraph("\n*** COMPROBANTE DE VENTA ***\nNO VÁLIDO COMO BOLETA FISCAL\n\n", fontSubtitle);
        avisoLegal.setAlignment(Element.ALIGN_CENTER);
        doc.add(avisoLegal);

        // --- DATOS OPERACIÓN ---
        LocalDateTime fechaVenta = (venta != null && venta.getFechaHora() != null) ? venta.getFechaHora() : LocalDateTime.now();
        String metodo = (venta != null && venta.getMetodoPago() != null) ? venta.getMetodoPago() : "EFECTIVO";
        Long ventaId = (venta != null) ? venta.getId() : null;

        doc.add(new Paragraph("Venta N°: " + (ventaId != null ? ventaId : "S/N"), fontBold));
        doc.add(new Paragraph("Fecha: " + fechaVenta.format(formatFecha), fontBody));
        doc.add(new Paragraph("Método Pago: " + metodo, fontBody));
        doc.add(new Paragraph("--------------------------------------------", fontBody));

        // --- TABLA DE PRODUCTOS ---
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 4.5f, 3.0f});

        // Cabecera Tabla
        table.addCell(crearCelda("Cant.", fontBold, Element.ALIGN_LEFT));
        table.addCell(crearCelda("Item", fontBold, Element.ALIGN_LEFT));
        table.addCell(crearCelda("Total", fontBold, Element.ALIGN_RIGHT));

        // Determinar lista a procesar
        List<?> listaAProcesar = (items != null && !items.isEmpty()) ? items : (venta != null ? venta.getDetalles() : null);

        double totalCalculado = 0;
        Double totalVenta = (venta != null) ? venta.getTotal() : null;

        if (listaAProcesar != null && !listaAProcesar.isEmpty()) {
            int totalElementos = listaAProcesar.size();

            for (Object item : listaAProcesar) {
                ItemProcesado ip = extraerDatosItem(item);

                // Reajuste si el subtotal dio 0 pero hay total general en la venta
                if (ip.subtotal <= 0 && totalVenta != null && totalVenta > 0) {
                    ip.subtotal = (totalElementos == 1) ? totalVenta : (totalVenta / totalElementos);
                    if (ip.cantidad > 0) {
                        ip.precioUnitario = ip.subtotal / ip.cantidad;
                    }
                }

                totalCalculado += ip.subtotal;

                table.addCell(crearCelda(ip.getCantidadFormateada(), fontBody, Element.ALIGN_LEFT));
                table.addCell(crearCelda(ip.descripcion, fontBody, Element.ALIGN_LEFT));
                table.addCell(crearCelda(formatMoneda.format(ip.subtotal), fontBody, Element.ALIGN_RIGHT));
            }
        }

        doc.add(table);
        doc.add(new Paragraph("--------------------------------------------", fontBody));

        // --- TOTAL ---
        double montoFinal = (totalVenta != null && totalVenta > 0) ? totalVenta : totalCalculado;

        Paragraph totalPar = new Paragraph("TOTAL: " + formatMoneda.format(montoFinal), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
        totalPar.setAlignment(Element.ALIGN_RIGHT);
        doc.add(totalPar);

        // --- PIE Y COPIA ---
        Paragraph pieCopia = new Paragraph("\n--- " + tipoCopia + " ---", fontSubtitle);
        pieCopia.setAlignment(Element.ALIGN_CENTER);
        doc.add(pieCopia);

        Paragraph gracias = new Paragraph("¡Gracias por su preferencia!\n", fontBody);
        gracias.setAlignment(Element.ALIGN_CENTER);
        doc.add(gracias);
    }

    // --- MÉTODOS DE EXTRACCIÓN Y RESPALDO ---

    private Empresa extraerEmpresa(Venta venta, List<?> items) {
        // 1. Intentar desde la Venta
        if (venta != null) {
            Object empObj = invocarMetodo(venta, "getEmpresa");
            if (empObj instanceof Empresa emp) return emp;

            Object usrObj = invocarMetodo(venta, "getUsuario");
            if (usrObj != null) {
                Object usrEmp = invocarMetodo(usrObj, "getEmpresa");
                if (usrEmp instanceof Empresa emp) return emp;
            }
        }

        // 2. Intentar desde el primer producto de la lista
        List<?> lista = (items != null && !items.isEmpty()) ? items : (venta != null ? venta.getDetalles() : null);
        if (lista != null && !lista.isEmpty()) {
            for (Object item : lista) {
                if (item instanceof VentaDetalle detalle && detalle.getProducto() != null && detalle.getProducto().getEmpresa() != null) {
                    return detalle.getProducto().getEmpresa();
                }
            }
        }
        return null;
    }

    private PdfPCell crearCelda(String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Paragraph(texto, fuente));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(2);
        return celda;
    }

    private static class ItemProcesado {
        String descripcion = null;
        double cantidad = 1.0;
        double precioUnitario = 0.0;
        double subtotal = 0.0;

        String getCantidadFormateada() {
            if (cantidad % 1 == 0) {
                return String.valueOf((long) cantidad);
            }
            return String.valueOf(cantidad);
        }
    }

    private ItemProcesado extraerDatosItem(Object item) {
        ItemProcesado res = new ItemProcesado();
        if (item == null) {
            res.descripcion = "Producto";
            return res;
        }

        Long prodId = null;

        // 1. VentaDetalle
        if (item instanceof VentaDetalle detalle) {
            if (detalle.getProducto() != null) {
                prodId = detalle.getProducto().getId();
                if (detalle.getProducto().getDescripcion() != null) {
                    res.descripcion = detalle.getProducto().getDescripcion();
                }
            }
            if (detalle.getCantidad() != null) res.cantidad = detalle.getCantidad();
            if (detalle.getPrecioUnitario() != null) res.precioUnitario = detalle.getPrecioUnitario();
            res.subtotal = res.cantidad * res.precioUnitario;
        } 
        // 2. MAP (JSON)
        else if (item instanceof Map<?, ?> map) {
            res.descripcion = buscarEnMapString(map, "descripcion", "nombre", "nombreproducto", "productonombre", "title", "titulo", "label", "item", "detalle", "name", "productname");
            if (res.descripcion == null && map.get("producto") instanceof Map<?, ?> prodMap) {
                res.descripcion = buscarEnMapString(prodMap, "descripcion", "nombre", "title", "name");
                prodId = buscarEnMapLong(prodMap, "id", "productoid");
            }
            if (prodId == null) {
                prodId = buscarEnMapLong(map, "productoid", "idproducto", "producto_id", "id");
            }

            double cant = buscarEnMapDouble(map, "cantidad", "cant", "quantity", "qty", "unidades");
            if (cant > 0) res.cantidad = cant;

            res.precioUnitario = buscarEnMapDouble(map, "preciounitario", "precio", "price", "unitprice", "monto", "valor");
            if (res.precioUnitario <= 0 && map.get("producto") instanceof Map<?, ?> prodMap) {
                res.precioUnitario = buscarEnMapDouble(prodMap, "precio", "preciounitario", "price");
            }

            res.subtotal = buscarEnMapDouble(map, "subtotal", "total");
            if (res.subtotal <= 0 && res.precioUnitario > 0) {
                res.subtotal = res.cantidad * res.precioUnitario;
            }
        } 
        // 3. DTO Reflexión
        else {
            res.descripcion = buscarPropiedadString(item, "getDescripcion", "getNombre", "getProductoNombre", "getNombreProducto", "getTitle", "getDetalle");
            Object prod = invocarMetodo(item, "getProducto");
            if (prod != null) {
                if (res.descripcion == null || res.descripcion.isBlank()) {
                    res.descripcion = buscarPropiedadString(prod, "getDescripcion", "getNombre", "getTitle");
                }
                Object pIdObj = invocarMetodo(prod, "getId");
                if (pIdObj instanceof Number num) prodId = num.longValue();
            }
            if (prodId == null) {
                Object pIdObj = invocarMetodo(item, "getProductoId");
                if (pIdObj instanceof Number num) prodId = num.longValue();
            }

            double cant = buscarPropiedadDouble(item, "getCantidad", "getCant", "getQuantity");
            if (cant > 0) res.cantidad = cant;

            double pu = buscarPropiedadDouble(item, "getPrecioUnitario", "getPrecio", "getPrice");
            if (pu > 0) res.precioUnitario = pu;

            double sub = buscarPropiedadDouble(item, "getSubtotal", "getTotal");
            if (sub > 0) res.subtotal = sub;
            else if (res.precioUnitario > 0) res.subtotal = res.cantidad * res.precioUnitario;
        }

        // Búsqueda de respaldo en base de datos si falta la descripción
        if ((res.descripcion == null || res.descripcion.isBlank() || res.descripcion.equalsIgnoreCase("Producto")) 
                && prodId != null && productoRepository != null) {
            try {
                Optional<Producto> pOpt = productoRepository.findById(prodId);
                if (pOpt.isPresent() && pOpt.get().getDescripcion() != null) {
                    res.descripcion = pOpt.get().getDescripcion();
                }
            } catch (Exception ignored) {}
        }

        if (res.descripcion == null || res.descripcion.isBlank()) {
            res.descripcion = "Producto";
        }

        return res;
    }

    private String buscarEnMapString(Map<?, ?> map, String... claves) {
        for (Object k : map.keySet()) {
            if (k == null) continue;
            String keyStr = k.toString().toLowerCase().replace("_", "").replace("-", "");
            for (String c : claves) {
                if (keyStr.equals(c.toLowerCase())) {
                    Object val = map.get(k);
                    if (val != null) return val.toString();
                }
            }
        }
        return null;
    }

    private Long buscarEnMapLong(Map<?, ?> map, String... claves) {
        for (Object k : map.keySet()) {
            if (k == null) continue;
            String keyStr = k.toString().toLowerCase().replace("_", "").replace("-", "");
            for (String c : claves) {
                if (keyStr.equals(c.toLowerCase())) {
                    Object val = map.get(k);
                    if (val instanceof Number num) return num.longValue();
                    if (val != null) {
                        try { return Long.parseLong(val.toString()); } catch (Exception ignored) {}
                    }
                }
            }
        }
        return null;
    }

    private double buscarEnMapDouble(Map<?, ?> map, String... claves) {
        for (Object k : map.keySet()) {
            if (k == null) continue;
            String keyStr = k.toString().toLowerCase().replace("_", "").replace("-", "");
            for (String c : claves) {
                if (keyStr.equals(c.toLowerCase())) {
                    Object val = map.get(k);
                    if (val instanceof Number num) return num.doubleValue();
                    if (val != null) {
                        try { return Double.parseDouble(val.toString()); } catch (Exception ignored) {}
                    }
                }
            }
        }
        return 0.0;
    }

    private Object invocarMetodo(Object obj, String nombreMetodo) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(nombreMetodo);
            return m.invoke(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buscarPropiedadString(Object obj, String... metodos) {
        for (String mName : metodos) {
            Object res = invocarMetodo(obj, mName);
            if (res != null && !res.toString().isBlank()) return res.toString();
        }
        return null;
    }

    private double buscarPropiedadDouble(Object obj, String... metodos) {
        for (String mName : metodos) {
            Object res = invocarMetodo(obj, mName);
            if (res instanceof Number num) return num.doubleValue();
            if (res != null) {
                try { return Double.parseDouble(res.toString()); } catch (Exception ignored) {}
            }
        }
        return 0.0;
    }
}