package com.posbarlacteo.PosBarLacteo.dte;

import java.net.SocketTimeoutException;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/** Utilidades HTTP comunes a los proveedores: tiempos de espera y traducción de errores a mensajes claros. */
final class ClienteHttpDte {

    private ClienteHttpDte() {
    }

    static RestTemplate nuevo() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(8_000);
        f.setReadTimeout(25_000);
        return new RestTemplate(f);
    }

    /** Traduce una falla HTTP. En emisiones, un timeout de lectura deja la duda de si el documento salió. */
    static DteException traducir(RuntimeException e, String proveedor, boolean esEmision) {
        if (e instanceof HttpStatusCodeException h) {
            int s = h.getStatusCode().value();
            String detalle = recortar(h.getResponseBodyAsString());
            if (s == 401 || s == 403) {
                return new DteException("" + proveedor + " rechazó la API Key (HTTP " + s + "). Revísala en Facturación Electrónica.");
            }
            if (s == 429) {
                return new DteException(proveedor + " limitó las solicitudes (HTTP 429). Reintenta en un minuto.");
            }
            if (s >= 500) {
                // 5xx: el proveedor falló; no hay certeza de que no haya alcanzado a emitir
                return new DteException(proveedor + " tuvo un error interno (HTTP " + s + "). " + detalle, esEmision);
            }
            return new DteException(proveedor + " rechazó el documento (HTTP " + s + "): " + detalle);
        }
        if (e instanceof ResourceAccessException r) {
            boolean lecturaAgotada = r.getCause() instanceof SocketTimeoutException
                    && r.getCause().getMessage() != null && r.getCause().getMessage().toLowerCase().contains("read");
            if (lecturaAgotada) {
                return new DteException(proveedor + " no respondió a tiempo. Puede que el documento SÍ se haya emitido: verifícalo en el portal del proveedor.", esEmision);
            }
            return new DteException("No se pudo conectar con " + proveedor + ". Reintenta en unos minutos.");
        }
        return new DteException("Error inesperado al comunicarse con " + proveedor + ": " + e.getMessage());
    }

    static String recortar(String s) {
        if (s == null) return "";
        String limpio = s.replaceAll("\\s+", " ").trim();
        return limpio.length() > 300 ? limpio.substring(0, 300) + "…" : limpio;
    }
}
