package com.posbarlacteo.PosBarLacteo.util;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CSV para Excel en Chile: separador ";", coma decimal, UTF-8 con BOM (para que respete tildes y ñ) y saltos CRLF.
 * Los textos que empiezan con = + - @ se neutralizan para que Excel no los ejecute como fórmula.
 */
public final class CsvUtil {

    public static final String BOM = "﻿";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvUtil() {
    }

    /** Agrega una fila; cada celda ya viene como texto (usa t(), n(), f()). */
    public static void fila(StringBuilder sb, String... celdas) {
        for (int i = 0; i < celdas.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(escapar(celdas[i]));
        }
        sb.append("\r\n");
    }

    /** Texto libre (nombres, motivos...): neutraliza fórmulas. */
    public static String t(String s) {
        if (s == null) return "";
        String x = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (!x.isEmpty() && "=+-@".indexOf(x.charAt(0)) >= 0) {
            return "'" + x;
        }
        return x;
    }

    /** Número: entero sin decimales; con decimales, coma decimal y sin ceros sobrantes. */
    public static String n(Double d) {
        if (d == null || d.isNaN() || d.isInfinite()) return "";
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) Math.rint(d));
        return new BigDecimal(d).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',');
    }

    public static String n(Long l) {
        return l == null ? "" : String.valueOf(l);
    }

    public static String f(LocalDateTime d) {
        return d == null ? "" : FECHA.format(d);
    }

    public static String si(Boolean b) {
        return Boolean.TRUE.equals(b) ? "SI" : "NO";
    }

    private static String escapar(String s) {
        if (s == null) return "";
        if (s.indexOf(';') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
