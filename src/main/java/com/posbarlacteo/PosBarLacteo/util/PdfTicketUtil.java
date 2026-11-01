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
    private static final float ALTO_MAXIMO = 14400f; // límite práctico de página PDF
    private static final float MARGEN_LATERAL = 8f;
    private static final float MARGEN_VERTICAL = 10f;

    @FunctionalInterface
    public interface Contenido {
        void escribir(Document documento) throws Exception;
    }

    private PdfTicketUtil() {
    }

    public static byte[] generar(Contenido contenido) throws Exception {
        float alto = medir(contenido);
        return render(contenido, alto);
    }

    public static String generarBase64(Contenido contenido) throws Exception {
        return Base64.getEncoder().encodeToString(generar(contenido));
    }

    private static float medir(Contenido contenido) throws Exception {
        ByteArrayOutputStream descarte = new ByteArrayOutputStream();
        Document doc = new Document(new Rectangle(ANCHO, ALTO_MAXIMO), MARGEN_LATERAL, MARGEN_LATERAL,
                MARGEN_VERTICAL, MARGEN_VERTICAL);
        PdfWriter writer = PdfWriter.getInstance(doc, descarte);
        doc.open();
        contenido.escribir(doc);
        float yFinal = writer.getVerticalPosition(true); // posición medida desde abajo de la página
        doc.close();
        float usado = (ALTO_MAXIMO - yFinal) + MARGEN_VERTICAL;
        return Math.min(ALTO_MAXIMO, Math.max(120f, usado + 6f));
    }

    private static byte[] render(Contenido contenido, float alto) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(new Rectangle(ANCHO, alto), MARGEN_LATERAL, MARGEN_LATERAL,
                MARGEN_VERTICAL, MARGEN_VERTICAL);
        PdfWriter.getInstance(doc, salida);
        doc.open();
        contenido.escribir(doc);
        doc.close();
        return salida.toByteArray();
    }
}
