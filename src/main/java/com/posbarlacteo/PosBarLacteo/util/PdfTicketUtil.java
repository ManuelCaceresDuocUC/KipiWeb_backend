package com.posbarlacteo.PosBarLacteo.util;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Tickets para impresora térmica de 80 mm: ancho fijo y alto EXACTO al contenido, para que al imprimir
 * no salga papel en blanco de más. Se escribe el contenido dos veces: la primera sobre una página muy alta
 * para medir cuánto ocupa, la segunda con el alto real.
 */
public final class PdfTicketUtil {

    /** 80 mm en puntos (1 pt = 1/72"). */
    public static final float ANCHO = 226f;
    /** 58 mm en puntos. */
    public static final float ANCHO_58 = 164f;
    private static final float ALTO_MAXIMO = 14400f; // límite práctico de página PDF
    private static final float MARGEN_LATERAL = 8f;
    /** En papel de 58 mm la zona imprimible es de ~48 mm (136 pt), por eso el margen lateral es mayor. */
    private static final float MARGEN_LATERAL_58 = 14f;
    private static final float MARGEN_SUPERIOR = 10f;
    /** Los drivers de impresoras térmicas suelen reservar un margen inferior: sin este espacio, la última línea pasa a una 2.ª hoja. */
    private static final float MARGEN_INFERIOR = 34f;

    @FunctionalInterface
    public interface Contenido {
        void escribir(Document documento) throws Exception;
    }

    private PdfTicketUtil() {
    }

    /** Ancho de página en puntos según el papel de la impresora: 58 mm o (por defecto) 80 mm. */
    public static float anchoPt(Integer anchoMm) {
        return anchoMm != null && anchoMm <= 58 ? ANCHO_58 : ANCHO;
    }

    public static boolean es58(float ancho) {
        return ancho < ANCHO;
    }

    private static float margenLateral(float ancho) {
        return es58(ancho) ? MARGEN_LATERAL_58 : MARGEN_LATERAL;
    }

    public static byte[] generar(Contenido contenido) throws Exception {
        return generar(contenido, ANCHO);
    }

    public static byte[] generar(Contenido contenido, float ancho) throws Exception {
        float alto = medir(contenido, ancho);
        return render(contenido, ancho, alto);
    }

    public static String generarBase64(Contenido contenido) throws Exception {
        return Base64.getEncoder().encodeToString(generar(contenido));
    }

    public static String generarBase64(Contenido contenido, float ancho) throws Exception {
        return Base64.getEncoder().encodeToString(generar(contenido, ancho));
    }

    private static float medir(Contenido contenido, float ancho) throws Exception {
        ByteArrayOutputStream descarte = new ByteArrayOutputStream();
        Document doc = new Document(new Rectangle(ancho, ALTO_MAXIMO), margenLateral(ancho), margenLateral(ancho),
                MARGEN_SUPERIOR, MARGEN_INFERIOR);
        PdfWriter writer = PdfWriter.getInstance(doc, descarte);
        doc.open();
        contenido.escribir(doc);
        float yFinal = writer.getVerticalPosition(true); // posición medida desde abajo de la página
        doc.close();
        float usado = (ALTO_MAXIMO - yFinal) + MARGEN_INFERIOR;
        return Math.min(ALTO_MAXIMO, Math.max(120f, usado + 2f));
    }

    private static byte[] render(Contenido contenido, float ancho, float alto) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(new Rectangle(ancho, alto), margenLateral(ancho), margenLateral(ancho),
                MARGEN_SUPERIOR, MARGEN_INFERIOR);
        PdfWriter.getInstance(doc, salida);
        doc.open();
        contenido.escribir(doc);
        doc.close();
        return salida.toByteArray();
    }
}
