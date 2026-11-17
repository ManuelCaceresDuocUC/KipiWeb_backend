package com.posbarlacteo.PosBarLacteo.util;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validación y corrección de datos de formularios. Cada método devuelve el valor ya normalizado
 * (sin espacios de más, correo en minúsculas, teléfono con formato, etc.) o lanza {@link NegocioException}
 * con un mensaje apto para mostrar al usuario. El frontend aplica las mismas reglas; esta es la barrera real.
 */
public final class Validaciones {

    private Validaciones() {
    }

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");
    private static final Pattern CORREO = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9._%+'\\-]*[a-z0-9])?@(?:[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?\\.)+[a-z]{2,}$");
    private static final Pattern USUARIO = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");
    private static final Pattern CODIGO = Pattern.compile("^[A-Za-z0-9._\\-/]{1,50}$");
    private static final Set<String> PARTICULAS = Set.of("de", "del", "la", "las", "los", "el", "y", "e", "en", "a", "al", "con", "van", "von");

    public static final double MAX_DINERO = 99_999_999d;
    public static final double MAX_CANTIDAD = 9_999_999d;

    /** Quita caracteres de control y espacios repetidos. Devuelve "" si viene null. */
    public static String limpiar(String valor) {
        if (valor == null) {
            return "";
        }
        return ESPACIOS.matcher(CONTROL.matcher(valor).replaceAll(" ")).replaceAll(" ").trim();
    }

    /** Texto libre obligatorio u opcional, con largo mínimo/máximo. Devuelve null si es opcional y está vacío. */
    public static String texto(String campo, String valor, int min, int max, boolean obligatorio) {
        String v = limpiar(valor);
        if (v.isEmpty()) {
            if (obligatorio) {
                throw new NegocioException(campo + " es obligatorio.");
            }
            return null;
        }
        if (v.length() < min) {
            throw new NegocioException(campo + " debe tener al menos " + min + " caracteres.");
        }
        if (v.length() > max) {
            throw new NegocioException(campo + " no puede superar los " + max + " caracteres.");
        }
        return v;
    }

    /**
     * Nombre propio (persona, comuna, sucursal, calle): si viene todo en minúsculas o todo en mayúsculas lo pasa
     * a "Juan Pérez de la Cruz". Si el usuario ya mezcló mayúsculas (p. ej. "McDonald's", "Ñuñoa") lo respeta.
     */
    public static String nombrePropio(String campo, String valor, int min, int max, boolean obligatorio) {
        String v = texto(campo, valor, min, max, obligatorio);
        if (v == null) {
            return null;
        }
        if (!v.equals(v.toLowerCase(Locale.ROOT)) && !v.equals(v.toUpperCase(Locale.ROOT))) {
            return v;
        }
        String[] palabras = v.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < palabras.length; i++) {
            String w = palabras[i];
            if (i > 0) {
                sb.append(' ');
            }
            if (i > 0 && PARTICULAS.contains(w)) {
                sb.append(w);
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }

    /** RUT chileno con dígito verificador correcto. Devuelve el formato "12.345.678-5". */
    public static String rut(String campo, String valor, boolean obligatorio) {
        String v = limpiar(valor);
        if (v.isEmpty()) {
            if (obligatorio) {
                throw new NegocioException(campo + " es obligatorio.");
            }
            return null;
        }
        if (!RutUtils.esValido(v)) {
            throw new NegocioException(campo + " no es válido. Revisa los números y el dígito verificador.");
        }
        return RutUtils.formatear(v);
    }

    /** Correo en minúsculas y sin espacios, con formato estricto. */
    public static String correo(String campo, String valor, boolean obligatorio) {
        String v = limpiar(valor).replace(" ", "").toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            if (obligatorio) {
                throw new NegocioException(campo + " es obligatorio.");
            }
            return null;
        }
        if (v.length() > 150 || v.contains("..") || !CORREO.matcher(v).matches()) {
            throw new NegocioException(campo + " no es válido (ejemplo: nombre@correo.cl).");
        }
        return v;
    }

    /**
     * Teléfono chileno: celular (9 dígitos que parten con 9) o fijo (9 dígitos que parten con 2 a 7), con o sin +56.
     * Devuelve "+56 9 1234 5678" (celular), "+56 2 2345 6789" (Santiago) o "+56 32 234 5678" (regiones).
     */
    public static String telefono(String campo, String valor, boolean obligatorio) {
        String v = limpiar(valor);
        if (v.isEmpty()) {
            if (obligatorio) {
                throw new NegocioException(campo + " es obligatorio.");
            }
            return null;
        }
        if (!v.matches("^[0-9+()\\-\\s.]+$")) {
            throw new NegocioException(campo + " solo puede tener números (ejemplo: +56 9 1234 5678).");
        }
        String d = v.replaceAll("\\D", "");
        if (d.startsWith("56") && d.length() == 11) {
            d = d.substring(2);
        }
        if (d.length() == 10 && d.startsWith("0")) {
            d = d.substring(1);
        }
        if (!d.matches("^[2-9]\\d{8}$")) {
            throw new NegocioException(campo + " no es válido. Usa 9 dígitos, por ejemplo +56 9 1234 5678.");
        }
        if (d.startsWith("9")) {
            return "+56 9 " + d.substring(1, 5) + " " + d.substring(5);
        }
        if (d.startsWith("2")) {
            return "+56 2 " + d.substring(1, 5) + " " + d.substring(5);
        }
        return "+56 " + d.substring(0, 2) + " " + d.substring(2, 5) + " " + d.substring(5);
    }

    /** Nombre de usuario: 3 a 50 caracteres, letras, números, punto, guion o guion bajo. */
    public static String usuario(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (!USUARIO.matcher(v).matches()) {
            throw new NegocioException("El usuario debe tener entre 3 y 50 caracteres: letras, números, punto, guion o guion bajo (sin espacios ni tildes).");
        }
        return v;
    }

    /** Contraseña de 8 a 100 caracteres con al menos una letra y un número. No se recorta ni se modifica. */
    public static String contrasena(String valor) {
        if (valor == null || valor.length() < 8 || valor.length() > 100) {
            throw new NegocioException("La contraseña debe tener entre 8 y 100 caracteres.");
        }
        if (!valor.matches(".*\\p{L}.*") || !valor.matches(".*\\d.*")) {
            throw new NegocioException("La contraseña debe incluir al menos una letra y un número.");
        }
        return valor;
    }

    /** Código de barras o código interno: letras, números y . _ - / (sin espacios). Null si viene vacío. */
    public static String codigoProducto(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (!CODIGO.matcher(v).matches()) {
            throw new NegocioException("El código solo puede tener letras, números y los signos . _ - / (máximo 50, sin espacios).");
        }
        return v;
    }

    /** Monto en pesos: finito, no negativo, hasta $99.999.999. Se redondea al peso. */
    public static double dinero(String campo, Double valor, boolean obligatorio) {
        if (valor == null) {
            if (obligatorio) {
                throw new NegocioException(campo + " es obligatorio.");
            }
            return 0d;
        }
        if (valor.isNaN() || valor.isInfinite() || valor < 0) {
            throw new NegocioException(campo + " no es válido.");
        }
        if (valor > MAX_DINERO) {
            throw new NegocioException(campo + " es demasiado alto (máximo $99.999.999).");
        }
        return Math.round(valor);
    }

    /** Cantidad (stock, mínimo): finita, no negativa, hasta 9.999.999, con hasta 3 decimales. */
    public static double cantidad(String campo, Double valor, boolean permitirDecimales) {
        if (valor == null) {
            return 0d;
        }
        if (valor.isNaN() || valor.isInfinite() || valor < 0) {
            throw new NegocioException(campo + " no es válido.");
        }
        if (valor > MAX_CANTIDAD) {
            throw new NegocioException(campo + " es demasiado alto.");
        }
        if (!permitirDecimales && valor != Math.floor(valor)) {
            throw new NegocioException(campo + " debe ser un número entero.");
        }
        return Math.round(valor * 1000d) / 1000d;
    }
}
