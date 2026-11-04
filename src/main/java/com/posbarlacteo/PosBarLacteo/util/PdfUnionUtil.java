package com.posbarlacteo.PosBarLacteo.util;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfReader;

/**
 * Une varios PDF (en base64) en uno solo, conservando el tamaño de cada página. Sirve para que el ticket y el vale
 * para firmar salgan en UN solo trabajo de impresión: con dos trabajos seguidos, algunos navegadores ignoran el segundo.
 */
public final class PdfUnionUtil {

    private PdfUnionUtil() {
    }

    /** @return el PDF unido en base64, o null si algún documento no se pudo leer (quien llama usa los originales). */
    public static String unirBase64(List<String> documentosBase64) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document doc = new Document();
            PdfCopy copia = new PdfCopy(doc, salida);
            doc.open();
            int paginas = 0;
            for (String b64 : documentosBase64) {
                if (b64 == null || b64.isBlank()) continue;
                PdfReader lector = new PdfReader(Base64.getDecoder().decode(b64));
                for (int i = 1; i <= lector.getNumberOfPages(); i++) {
                    copia.addPage(copia.getImportedPage(lector, i));
                    paginas++;
                }
                copia.freeReader(lector);
                lector.close();
            }
            doc.close();
            return paginas == 0 ? null : Base64.getEncoder().encodeToString(salida.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
