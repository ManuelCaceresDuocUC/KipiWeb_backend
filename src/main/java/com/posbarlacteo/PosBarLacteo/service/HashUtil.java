package com.posbarlacteo.PosBarLacteo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Utilidades para códigos y tokens de un solo uso: aleatorios criptográficos y hash SHA-256. */
final class HashUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private HashUtil() { }

    static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String codigo6() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    static String tokenAleatorio() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Comparación en tiempo constante. */
    static boolean iguales(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
