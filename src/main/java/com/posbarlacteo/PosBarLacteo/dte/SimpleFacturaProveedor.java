package com.posbarlacteo.PosBarLacteo.dte;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * SimpleFactura (https://www.simplefactura.cl, ChileSystems). BETA: construido desde el código de su SDK oficial
 * (github.com/lpinedozav/SDKSimpleFacturaPHP), SIN probar contra su API.
 *
 * Autenticación (SDK): POST /token con {email, password} → {accessToken, expiresAt}; JWT de ~24 h, header Bearer.
 *   En KIPIPOS: "API Key" = correo del usuario de SimpleFactura, "API Secret" = su contraseña.
 * Emisión (SDK): POST /invoiceV2/{sucursal} con {Documento:{Encabezado,Detalle,Referencia}} →
 *   {status, message, data:{tipoDTE, rutEmisor, rutReceptor, folio, fechaEmision, total}}.
 * Nota de crédito (SDK): POST /invoiceCreditDebitNotesV2/{sucursal}/{motivo} (mismo cuerpo, con Referencia).
 * PDF (SDK): POST /dte/pdf con {credenciales:{rutEmisor,nombreSucursal}, dteReferenciadoExterno:{folio,codigoTipoDte,ambiente}}.
 *
 * Por validar en la primera prueba: el SDK no trae ejemplo de BOLETA (tipo 39): se asume el mismo endpoint /invoiceV2,
 * con precios con IVA incluido (IdDoc.IndMntNeto = 2 según el SII). Tampoco se sabe si exige "Acteco" del emisor.
 */
@Component
public class SimpleFacturaProveedor implements DteProveedor {

    private static final String PROVEEDOR = "SimpleFactura";
    private static final String BASE = "https://api.simplefactura.cl";
    private static final String SUCURSAL_URL = "Casa_Matriz";
    private static final String SUCURSAL_NOMBRE = "Casa Matriz";
    private static final int MOTIVO_NC_OTROS = 6; // ReasonType::Otros
    private static final String RUT_GENERICO = "66666666-6";
    private static final Pattern MENSAJE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]{1,300})");
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() { };

    private record Token(String valor, Instant vence) {
    }

    private final RestTemplate http = ClienteHttpDte.nuevo();
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    @Override
    public String codigo() {
        return "simplefactura";
    }

    @Override
    public String nombre() {
        return "SimpleFactura (beta)";
    }

    @Override
    public boolean verificado() {
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // Autenticación
    // ------------------------------------------------------------------------------------------

    private static String claveCache(CredencialesDte c) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((c.apiKey() + "\0" + c.apiSecret()).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String token(CredencialesDte c) {
        if (c.apiKey() == null || c.apiKey().isBlank() || c.apiSecret() == null || c.apiSecret().isBlank()) {
            throw new DteException("SimpleFactura necesita el correo de tu usuario y su contraseña. Ingrésalos en Facturación Electrónica.");
        }
        String clave = claveCache(c);
        Token t = tokens.get(clave);
        if (t != null && Instant.now().isBefore(t.vence().minusSeconds(120))) {
            return t.valor();
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("email", c.apiKey().trim());
        cuerpo.put("password", c.apiSecret());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        Map<String, Object> r;
        try {
            r = http.exchange(BASE + "/token", HttpMethod.POST, new HttpEntity<>(cuerpo, h), MAPA).getBody();
        } catch (HttpStatusCodeException e) {
            int s = e.getStatusCode().value();
            if (s == 400 || s == 401 || s == 403) {
                throw new DteException("SimpleFactura rechazó el correo o la contraseña (HTTP " + s + "). Revísalos en Facturación Electrónica.");
            }
            throw ClienteHttpDte.traducir(e, PROVEEDOR, false);
        } catch (RuntimeException e) {
            throw ClienteHttpDte.traducir(e, PROVEEDOR, false);
        }
        Object tk = r == null ? null : (r.get("accessToken") != null ? r.get("accessToken") : r.get("AccessToken"));
        if (tk == null || tk.toString().isBlank()) {
            throw new DteException("SimpleFactura respondió sin token de acceso.");
        }
        Instant vence = Instant.now().plusSeconds(20 * 3600L); // el token dura ~24 h; si no se puede leer la fecha, se renueva antes
        Object exp = r.get("expiresAt") != null ? r.get("expiresAt") : r.get("ExpiresAt");
        if (exp != null) {
            try {
                Instant e = Instant.parse(exp.toString().endsWith("Z") ? exp.toString() : exp.toString() + "Z");
                if (e.isBefore(vence)) vence = e;
            } catch (RuntimeException ignorado) {
                // se usa el plazo por defecto
            }
        }
        tokens.put(clave, new Token(tk.toString(), vence));
        return tk.toString();
    }

    private void olvidarToken(CredencialesDte c) {
        tokens.remove(claveCache(c));
    }

    private HttpHeaders cabeceras(CredencialesDte c, MediaType accept) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token(c));
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(accept));
        return h;
    }

    @Override
    public ResultadoPrueba probarConexion(CredencialesDte c) {
        try {
            olvidarToken(c);
            token(c);
            return ResultadoPrueba.ok("SimpleFactura aceptó el correo y la contraseña. Esto no confirma que tu empresa tenga folios ni permiso para emitir: valida con una boleta en ambiente de pruebas.");
        } catch (DteException e) {
            return e.getMessage().contains("rechazó") || e.getMessage().contains("necesita")
                    ? ResultadoPrueba.rechazada(e.getMessage())
                    : ResultadoPrueba.inconclusa(e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Emisión
    // ------------------------------------------------------------------------------------------

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
        if (c.emisor() == null || !c.emisor().completo()) {
            throw new DteException("SimpleFactura exige razón social, giro, dirección y comuna de tu empresa, y alguno falta en tu registro. Contáctanos para completarlos.");
        }
        String url = tipoDte == 61
                ? BASE + "/invoiceCreditDebitNotesV2/" + SUCURSAL_URL + "/" + MOTIVO_NC_OTROS
                : BASE + "/invoiceV2/" + SUCURSAL_URL;
        Map<String, Object> cuerpo = armarSolicitud(tipoDte, s, c);

        Map<String, Object> resp = null;
        for (int intento = 0; intento < 2 && resp == null; intento++) {
            try {
                resp = http.exchange(url, HttpMethod.POST, new HttpEntity<>(cuerpo, cabeceras(c, MediaType.APPLICATION_JSON)), MAPA).getBody();
                if (resp == null) {
                    throw new DteException(PROVEEDOR + " respondió sin contenido. Verifica en su portal si el documento salió.", true);
                }
            } catch (HttpStatusCodeException h) {
                if (h.getStatusCode().value() == 401 && intento == 0) {
                    olvidarToken(c); // token vencido: se pide otro y se reintenta una sola vez (el 401 es previo a emitir)
                    continue;
                }
                throw traducirEmision(h);
            } catch (DteException e) {
                throw e;
            } catch (RuntimeException e) {
                throw ClienteHttpDte.traducir(e, PROVEEDOR, true);
            }
        }
        if (resp == null) {
            throw new DteException(PROVEEDOR + " no aceptó la sesión. Revisa el correo y la contraseña.");
        }

        Long status = aLong(resp.get("status"));
        if (status != null && status != 200) {
            throw new DteException(PROVEEDOR + " rechazó el documento: " + mensaje(resp));
        }
        Object data = resp.get("data");
        Long folio = data instanceof Map<?, ?> d ? aLong(d.get("folio")) : null;
        if (folio == null) {
            throw new DteException(PROVEEDOR + " respondió OK pero no se encontró el folio. Verifica en su portal si el documento salió. Claves recibidas: "
                    + resp.keySet(), true);
        }

        // PDF: si falla, el documento YA salió; se informa el folio sin PDF (no es error)
        String pdf = null;
        try {
            Map<String, Object> cred = new LinkedHashMap<>();
            cred.put("rutEmisor", c.rutEmisor());
            cred.put("nombreSucursal", SUCURSAL_NOMBRE);
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("folio", folio);
            ref.put("codigoTipoDte", tipoDte);
            ref.put("ambiente", c.sandbox() ? 0 : 1);
            Map<String, Object> pedido = new LinkedHashMap<>();
            pedido.put("credenciales", cred);
            pedido.put("dteReferenciadoExterno", ref);
            byte[] bytes = http.exchange(BASE + "/dte/pdf", HttpMethod.POST,
                    new HttpEntity<>(pedido, cabeceras(c, MediaType.ALL)), byte[].class).getBody();
            if (bytes != null && bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P') { // firma "%PDF"
                pdf = Base64.getEncoder().encodeToString(bytes);
            }
        } catch (RuntimeException ignorado) {
            pdf = null;
        }
        return new ResultadoDte(folio, pdf);
    }

    private Map<String, Object> armarSolicitud(int tipoDte, SolicitudDte s, CredencialesDte c) {
        long total = s.total();
        long neto = Math.round(total / 1.19);
        long iva = total - neto;
        boolean boleta = tipoDte == 39;

        Map<String, Object> idDoc = new LinkedHashMap<>();
        idDoc.put("TipoDTE", tipoDte);
        idDoc.put("FchEmis", s.fecha().toString());
        if (boleta) {
            idDoc.put("IndMntNeto", 2); // en boletas los montos del detalle incluyen IVA
        } else {
            idDoc.put("FmaPago", 1);    // contado
        }

        DatosEmisor e = c.emisor();
        Map<String, Object> emisor = new LinkedHashMap<>();
        emisor.put("RUTEmisor", c.rutEmisor());
        emisor.put("RznSoc", e.razonSocial());
        emisor.put("GiroEmis", e.giro());
        emisor.put("DirOrigen", e.direccion());
        emisor.put("CmnaOrigen", e.comuna());
        emisor.put("CiudadOrigen", e.comuna());

        Map<String, Object> receptor = new LinkedHashMap<>();
        receptor.put("RUTRecep", RUT_GENERICO);
        receptor.put("RznSocRecep", "Persona Natural");
        if (!boleta) {
            receptor.put("GiroRecep", "Particular");
            receptor.put("DirRecep", "Sin dirección");
            receptor.put("CmnaRecep", "Sin comuna");
            receptor.put("CiudadRecep", "Sin ciudad");
        }

        Map<String, Object> totales = new LinkedHashMap<>();
        totales.put("MntNeto", neto);
        totales.put("TasaIVA", 19);
        totales.put("IVA", iva);
        totales.put("MntTotal", total);

        Map<String, Object> encabezado = new LinkedHashMap<>();
        encabezado.put("IdDoc", idDoc);
        encabezado.put("Emisor", emisor);
        encabezado.put("Receptor", receptor);
        encabezado.put("Totales", totales);

        // Boleta: líneas con IVA incluido. Nota de crédito: líneas en neto, ajustadas para que sumen exactamente MntNeto
        List<Map<String, Object>> detalle = new ArrayList<>();
        long sumaNeto = 0;
        int n = s.lineas().size();
        for (int i = 0; i < n; i++) {
            LineaDte l = s.lineas().get(i);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("NroLinDet", i + 1);
            d.put("NmbItem", l.nombre());
            d.put("QtyItem", l.cantidad());
            d.put("UnmdItem", "un");
            if (boleta) {
                d.put("PrcItem", l.precioUnitario());
                d.put("MontoItem", l.monto());
            } else {
                long netoLinea = (i == n - 1) ? neto - sumaNeto : Math.round(l.monto() / 1.19);
                sumaNeto += netoLinea;
                double cant = l.cantidad() > 0 ? l.cantidad() : 1;
                d.put("PrcItem", Math.round(netoLinea / cant * 1_000_000d) / 1_000_000d);
                d.put("MontoItem", netoLinea);
            }
            detalle.add(d);
        }

        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("Encabezado", encabezado);
        documento.put("Detalle", detalle);

        if (tipoDte == 61) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("NroLinRef", 1);
            ref.put("TpoDocRef", "39");
            ref.put("FolioRef", String.valueOf(s.folioReferencia()));
            ref.put("FchRef", (s.fechaReferencia() != null ? s.fechaReferencia() : s.fecha()).toString());
            ref.put("CodRef", 1); // anula el documento de referencia
            ref.put("RazonRef", s.razon() != null ? s.razon() : "Anula boleta");
            documento.put("Referencia", List.of(ref));
        }

        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("Documento", documento);
        return raiz;
    }

    // ------------------------------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------------------------------

    private DteException traducirEmision(HttpStatusCodeException h) {
        int st = h.getStatusCode().value();
        if (st >= 400 && st < 500 && st != 401 && st != 403 && st != 429) {
            Matcher m = MENSAJE.matcher(h.getResponseBodyAsString());
            String detalle = m.find() ? m.group(1) : ClienteHttpDte.recortar(h.getResponseBodyAsString());
            return new DteException(PROVEEDOR + " rechazó el documento (HTTP " + st + "): " + detalle);
        }
        return ClienteHttpDte.traducir(h, PROVEEDOR, true);
    }

    private static String mensaje(Map<String, Object> resp) {
        Object m = resp.get("message");
        Object e = resp.get("errors");
        String base = m == null ? "sin detalle" : m.toString();
        return e == null ? base : base + " " + ClienteHttpDte.recortar(e.toString());
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
