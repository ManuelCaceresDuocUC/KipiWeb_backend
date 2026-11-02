package com.posbarlacteo.PosBarLacteo.dte;

import java.util.ArrayList;
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
 * OpenFactura (Haulmer). Producción: https://api.haulmer.com  ·  Pruebas: https://dev-api.haulmer.com
 * Emisión: POST /v2/dte/document con la API Key en el encabezado "apikey".
 * (Antes producción apuntaba a /v2/dte/issue, ruta que la documentación no lista.)
 */
@Component
public class OpenFacturaProveedor implements DteProveedor {

    private static final String PROVEEDOR = "OpenFactura";
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() { };

    private final RestTemplate http = ClienteHttpDte.nuevo();

    @Override
    public String codigo() {
        return "openfactura";
    }

    @Override
    public String nombre() {
        return "OpenFactura (Haulmer)";
    }

    @Override
    public boolean verificado() {
        return true; // la boleta funciona en sandbox; la nota de crédito aún no se ha probado
    }

    private String base(CredencialesDte c) {
        return c.sandbox() ? "https://dev-api.haulmer.com" : "https://api.haulmer.com";
    }

    private HttpHeaders cabeceras(CredencialesDte c) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("apikey", c.apiKey());
        return h;
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
        ResponseEntity<Map<String, Object>> resp;
        try {
            resp = enviar(tipoDte, s, c, true);
        } catch (HttpStatusCodeException h) {
            // Si el proveedor objeta justamente el pedido de FOLIO (400 = nada se emitió), se repite sin él.
            if (h.getStatusCode().value() == 400 && h.getResponseBodyAsString().toUpperCase().contains("FOLIO")) {
                try {
                    resp = enviar(tipoDte, s, c, false);
                } catch (RuntimeException e2) {
                    throw ClienteHttpDte.traducir(e2, PROVEEDOR, true);
                }
            } else {
                throw ClienteHttpDte.traducir(h, PROVEEDOR, true);
            }
        } catch (RuntimeException e) {
            throw ClienteHttpDte.traducir(e, PROVEEDOR, true);
        }

        Map<String, Object> r = resp.getBody();
        if (!resp.getStatusCode().is2xxSuccessful() || r == null) {
            throw new DteException("Respuesta inesperada de " + PROVEEDOR + ": HTTP " + resp.getStatusCode().value(), true);
        }

        Long folio = aLong(r.get("FOLIO"));
        if (folio == null) {
            folio = aLong(r.get("folio"));
        }
        String pdf = primero(r, "80MM", "80mm", "PDF", "pdf");
        if (pdf == null && folio == null) {
            // Respondió 200 pero no se reconoce nada: puede haber emitido. No se reintenta solo.
            throw new DteException(PROVEEDOR + " respondió OK pero sin folio ni PDF. Verifica en su portal si el documento salió. Claves recibidas: " + r.keySet(), true);
        }
        return new ResultadoDte(folio, pdf);
    }

    private ResponseEntity<Map<String, Object>> enviar(int tipoDte, SolicitudDte s, CredencialesDte c, boolean pedirFolio) {
        Map<String, Object> body = new LinkedHashMap<>();
        // 80MM = ticket para impresora térmica; PDF = respaldo; FOLIO = número del documento para guardarlo
        body.put("response", pedirFolio ? List.of("PDF", "80MM", "FOLIO") : List.of("PDF", "80MM"));
        body.put("dte", armarDte(tipoDte, s, c));
        return http.exchange(base(c) + "/v2/dte/document", HttpMethod.POST, new HttpEntity<>(body, cabeceras(c)), MAPA);
    }

    private Map<String, Object> armarDte(int tipoDte, SolicitudDte s, CredencialesDte c) {
        Map<String, Object> dte = new LinkedHashMap<>();
        Map<String, Object> encabezado = new LinkedHashMap<>();

        Map<String, Object> idDoc = new LinkedHashMap<>();
        idDoc.put("TipoDTE", tipoDte);                  // 39 boleta · 61 nota de crédito
        idDoc.put("Folio", 0);                          // 0 = el proveedor asigna el folio
        idDoc.put("FchEmis", s.fecha().toString());
        if (tipoDte == 39) {
            idDoc.put("IndServicio", 3);                // 3 = ventas y servicios
        }
        encabezado.put("IdDoc", idDoc);

        Map<String, Object> emisor = new LinkedHashMap<>();
        emisor.put("RUTEmisor", c.rutEmisor());
        if (tipoDte == 61) {
            // La nota de crédito (a diferencia de la boleta) exige los datos del emisor: el esquema del SII valida
            // RznSoc, GiroEmis, DirOrigen y CmnaOrigen. Se toman del registro de la empresa en KIPIPOS.
            DatosEmisor e = c.emisor();
            if (e == null || !e.completo()) {
                throw new DteException("La nota de crédito necesita la razón social, el giro, la dirección y la comuna de tu empresa, y alguno falta en tu registro. Contáctanos para completarlos.");
            }
            if (!e.tieneActeco()) {
                throw new DteException("La nota de crédito necesita el código de actividad económica (Acteco) de tu empresa. Ingrésalo en Facturación Electrónica (6 dígitos, lo encuentras en tu Mi SII > Actividades económicas).");
            }
            // El esquema valida el ORDEN de los campos: RUT, razón social, giro, actividad, dirección, comuna
            emisor.put("RznSoc", corto(e.razonSocial(), 100));
            emisor.put("GiroEmis", corto(e.giro(), 80));
            emisor.put("Acteco", e.acteco());
            emisor.put("DirOrigen", corto(e.direccion(), 70));
            emisor.put("CmnaOrigen", corto(e.comuna(), 20));
        }
        encabezado.put("Emisor", emisor);

        Map<String, Object> receptor = new LinkedHashMap<>();
        receptor.put("RUTRecep", "66666666-6");
        receptor.put("RznSocRecep", "Persona Natural");
        if (tipoDte == 61) {
            // La nota de crédito (a diferencia de la boleta) exige datos del receptor
            receptor.put("GiroRecep", "Particular");
            receptor.put("DirRecep", "Sin dirección");
            receptor.put("CmnaRecep", "Sin comuna");
        }
        encabezado.put("Receptor", receptor);

        long neto = Math.round(s.total() / 1.19);
        Map<String, Object> totales = new LinkedHashMap<>();
        totales.put("MntNeto", neto);
        if (tipoDte == 61) {
            totales.put("TasaIVA", 19);
        }
        totales.put("IVA", s.total() - neto);
        totales.put("MntTotal", s.total());
        encabezado.put("Totales", totales);
        dte.put("Encabezado", encabezado);

        List<Map<String, Object>> detalle = new ArrayList<>();
        int n = 1;
        for (LineaDte l : s.lineas()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("NroLinDet", n++);
            d.put("NmbItem", l.nombre());
            d.put("QtyItem", l.cantidad());
            d.put("PrcItem", l.precioUnitario());
            d.put("MontoItem", l.monto());
            detalle.add(d);
        }
        dte.put("Detalle", detalle);

        if (tipoDte == 61) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("NroLinRef", 1);
            ref.put("TpoDocRef", 39);                   // se anula una boleta electrónica
            ref.put("FolioRef", s.folioReferencia());
            ref.put("FchRef", (s.fechaReferencia() != null ? s.fechaReferencia() : s.fecha()).toString());
            ref.put("CodRef", 1);                       // 1 = anula el documento de referencia
            ref.put("RazonRef", s.razon() != null ? s.razon() : "Anula boleta");
            dte.put("Referencia", List.of(ref));
        }
        return dte;
    }

    /**
     * Consulta de la organización asociada a la API Key (GET /v2/dte/organization). Si la ruta no existiera para
     * tu cuenta, el resultado es "inconcluso" y no afirma que la llave sea buena ni mala.
     */
    @Override
    public ResultadoPrueba probarConexion(CredencialesDte c) {
        try {
            ResponseEntity<String> r = http.exchange(base(c) + "/v2/dte/organization", HttpMethod.GET,
                    new HttpEntity<>(cabeceras(c)), String.class);
            if (r.getStatusCode().is2xxSuccessful()) {
                return ResultadoPrueba.ok("OpenFactura aceptó la API Key (" + (c.sandbox() ? "ambiente de pruebas" : "producción") + ").");
            }
            return ResultadoPrueba.inconclusa("OpenFactura respondió HTTP " + r.getStatusCode().value() + ". No se pudo confirmar la llave.");
        } catch (HttpStatusCodeException e) {
            int s = e.getStatusCode().value();
            if (s == 401 || s == 403) {
                return ResultadoPrueba.rechazada("OpenFactura rechazó la API Key (HTTP " + s + "). Revisa que sea la del ambiente elegido ("
                        + (c.sandbox() ? "pruebas" : "producción") + ").");
            }
            return ResultadoPrueba.inconclusa("OpenFactura respondió HTTP " + s + " a la consulta de prueba. No se pudo confirmar la llave; "
                    + "la forma segura de validarla es emitir una boleta de prueba en el ambiente sandbox.");
        } catch (RuntimeException e) {
            return ResultadoPrueba.inconclusa("No se pudo conectar con OpenFactura: " + ClienteHttpDte.recortar(e.getMessage()));
        }
    }

    private static String primero(Map<String, Object> m, String... claves) {
        for (String k : claves) {
            Object v = m.get(k);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private static String corto(String v, int max) {
        String t = v.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static Long aLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
