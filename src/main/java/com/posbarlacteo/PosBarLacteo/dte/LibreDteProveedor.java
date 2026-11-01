package com.posbarlacteo.PosBarLacteo.dte;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * LibreDTE (https://libredte.cl). BETA: construido desde su documentación pública, SIN probar con una cuenta real.
 *
 * Autenticación (documentada): HTTP Basic. El campo "API Key" de KIPIPOS acepta cualquiera de los dos datos que
 * LibreDTE muestra en el perfil: el "API hash" de 32 caracteres (se arma Basic X:hash) o el valor "API KEY" ya en base64.
 * Flujo de emisión (documentado): 1) POST /api/dte/documentos/emitir (normaliza y crea el DTE temporal),
 * 2) POST /api/dte/documentos/generar (genera el documento real y asigna folio),
 * 3) GET /api/dte/dte_emitidos/pdf/{dte}/{folio}/{emisor}?papelContinuo=80 (PDF para impresora térmica).
 * Ambiente: parámetro _contribuyente_certificacion (1 = certificación/pruebas, 0 = producción).
 * No documentado y por validar en la primera prueba: forma exacta de la respuesta de "generar", los precios con/sin IVA
 * en boletas, y la nota de crédito (se usa el mismo flujo con TipoDTE 61 y bloque Referencia estándar del SII).
 */
@Component
public class LibreDteProveedor implements DteProveedor {

    private static final String PROVEEDOR = "LibreDTE";
    private static final String BASE = "https://libredte.cl";
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() { };

    private final RestTemplate http = ClienteHttpDte.nuevo();

    @Override
    public String codigo() {
        return "libredte";
    }

    @Override
    public String nombre() {
        return "LibreDTE (beta)";
    }

    @Override
    public boolean verificado() {
        return false;
    }

    private static String autorizacion(CredencialesDte c) {
        String k = c.apiKey().trim();
        if (k.matches("[A-Za-z0-9]{32}")) { // "API hash" → Basic X:hash
            return "Basic " + Base64.getEncoder().encodeToString(("X:" + k).getBytes(StandardCharsets.UTF_8));
        }
        return "Basic " + k;                // "API KEY" ya viene en base64
    }

    private static HttpHeaders cabeceras(CredencialesDte c, MediaType contenido) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, autorizacion(c));
        h.setContentType(contenido);
        h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PDF));
        return h;
    }

    private static String ambiente(CredencialesDte c) {
        return "_contribuyente_certificacion=" + (c.sandbox() ? 1 : 0);
    }

    @Override
    public ResultadoDte emitirBoleta(SolicitudDte s, CredencialesDte c) {
        return emitir(39, s, c);
    }

    @Override
    public ResultadoDte emitirNotaCredito(SolicitudDte s, CredencialesDte c) {
        if (s.folioReferencia() == null) {
            throw new DteException("No se conoce el folio de la boleta que se debe anular.");
        }
        return emitir(61, s, c);
    }

    private ResultadoDte emitir(int tipoDte, SolicitudDte s, CredencialesDte c) {
        Map<String, Object> temporal;
        try {
            // Paso 1: DTE temporal. Aquí todavía no existe el documento: un fallo es seguro de reintentar.
            ResponseEntity<Map<String, Object>> r1 = http.exchange(
                    BASE + "/api/dte/documentos/emitir?normalizar=1&formato=json&" + ambiente(c), HttpMethod.POST,
                    new HttpEntity<>(armarDte(tipoDte, s, c), cabeceras(c, MediaType.APPLICATION_JSON)), MAPA);
            temporal = r1.getBody();
        } catch (RuntimeException e) {
            throw ClienteHttpDte.traducir(e, PROVEEDOR, false);
        }
        if (temporal == null || temporal.isEmpty()) {
            throw new DteException(PROVEEDOR + " no devolvió el documento temporal.");
        }

        Map<String, Object> generado;
        try {
            // Paso 2: genera el documento real (aquí se asigna el folio): fallo ambiguo si no hubo respuesta
            ResponseEntity<Map<String, Object>> r2 = http.exchange(
                    BASE + "/api/dte/documentos/generar?getXML=0&links=0&email=0&" + ambiente(c), HttpMethod.POST,
                    new HttpEntity<>(temporal, cabeceras(c, MediaType.APPLICATION_JSON)), MAPA);
            generado = r2.getBody();
        } catch (RuntimeException e) {
            throw ClienteHttpDte.traducir(e, PROVEEDOR, true);
        }
        if (generado == null) {
            throw new DteException(PROVEEDOR + " respondió sin contenido al generar el documento. Verifica en su portal si salió.", true);
        }

        Long folio = aLong(generado.get("folio"));
        Long dte = aLong(generado.get("dte"));
        Long emisor = aLong(generado.get("emisor"));
        if (folio == null) {
            throw new DteException(PROVEEDOR + " respondió OK pero no se encontró el folio. Verifica en su portal si el documento salió. Claves recibidas: "
                    + generado.keySet(), true);
        }

        // Paso 3: PDF 80 mm. Si falla, el documento YA salió: se informa el folio sin PDF (no es error).
        String pdf = null;
        if (dte != null && emisor != null) {
            try {
                ResponseEntity<byte[]> r3 = http.exchange(
                        BASE + "/api/dte/dte_emitidos/pdf/" + dte + "/" + folio + "/" + emisor + "?formato=estandar&papelContinuo=80&" + ambiente(c),
                        HttpMethod.GET, new HttpEntity<>(cabeceras(c, MediaType.APPLICATION_JSON)), byte[].class);
                if (r3.getBody() != null && r3.getBody().length > 0) {
                    pdf = Base64.getEncoder().encodeToString(r3.getBody());
                }
            } catch (RuntimeException ignorado) {
                pdf = null;
            }
        }
        return new ResultadoDte(folio, pdf);
    }

    private Map<String, Object> armarDte(int tipoDte, SolicitudDte s, CredencialesDte c) {
        Map<String, Object> dte = new LinkedHashMap<>();
        Map<String, Object> encabezado = new LinkedHashMap<>();

        Map<String, Object> idDoc = new LinkedHashMap<>();
        idDoc.put("TipoDTE", tipoDte);
        idDoc.put("FchEmis", s.fecha().toString());
        encabezado.put("IdDoc", idDoc);

        Map<String, Object> emisor = new LinkedHashMap<>();
        emisor.put("RUTEmisor", c.rutEmisor());
        encabezado.put("Emisor", emisor);

        Map<String, Object> receptor = new LinkedHashMap<>();
        receptor.put("RUTRecep", "66666666-6");
        receptor.put("RznSocRecep", "Persona Natural");
        if (tipoDte == 61) {
            receptor.put("GiroRecep", "Particular");
            receptor.put("DirRecep", "Sin dirección");
            receptor.put("CmnaRecep", "Sin comuna");
        }
        encabezado.put("Receptor", receptor);
        dte.put("Encabezado", encabezado);

        // Con "normalizar=1" LibreDTE calcula neto, IVA y total: solo se envían las líneas
        List<Map<String, Object>> detalle = new ArrayList<>();
        for (LineaDte l : s.lineas()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("NmbItem", l.nombre());
            d.put("QtyItem", l.cantidad());
            d.put("PrcItem", l.precioUnitario());
            detalle.add(d);
        }
        dte.put("Detalle", detalle);

        if (tipoDte == 61) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("TpoDocRef", 39);
            ref.put("FolioRef", s.folioReferencia());
            ref.put("FchRef", (s.fechaReferencia() != null ? s.fechaReferencia() : s.fecha()).toString());
            ref.put("CodRef", 1);
            ref.put("RazonRef", s.razon() != null ? s.razon() : "Anula boleta");
            dte.put("Referencia", List.of(ref));
        }
        return dte;
    }

    /** Consulta el perfil del usuario con las credenciales. Si la ruta no coincide, el resultado es "inconcluso". */
    @Override
    public ResultadoPrueba probarConexion(CredencialesDte c) {
        try {
            http.exchange(BASE + "/api/usuarios/perfil", HttpMethod.GET,
                    new HttpEntity<>(cabeceras(c, MediaType.APPLICATION_JSON)), String.class);
            return ResultadoPrueba.ok("LibreDTE aceptó las credenciales.");
        } catch (HttpStatusCodeException e) {
            int s = e.getStatusCode().value();
            if (s == 401 || s == 403) {
                return ResultadoPrueba.rechazada("LibreDTE rechazó las credenciales (HTTP " + s + "). Usa el \"API hash\" o la \"API KEY\" de tu perfil.");
            }
            return ResultadoPrueba.inconclusa("LibreDTE respondió HTTP " + s + " a la consulta de prueba. No se pudo confirmar; "
                    + "emite una boleta en ambiente de pruebas para validar la integración.");
        } catch (RuntimeException e) {
            return ResultadoPrueba.inconclusa("No se pudo conectar con LibreDTE: " + ClienteHttpDte.recortar(e.getMessage()));
        }
    }

    private static Long aLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String t) {
            try {
                return Long.parseLong(t.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
