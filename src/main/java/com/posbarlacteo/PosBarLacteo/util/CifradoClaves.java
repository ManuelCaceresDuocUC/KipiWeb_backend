package com.posbarlacteo.PosBarLacteo.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado AES-256-GCM de secretos de los clientes (API keys de su proveedor DTE) antes de guardarlos en la base.
 * La clave maestra viene SOLO de la variable de entorno DTE_ENCRYPTION_KEY (32 bytes en base64). Si se pierde,
 * los secretos guardados no se pueden recuperar y cada cliente debe volver a ingresar su API key.
 * Formato guardado: "enc:v1:" + base64(iv[12] + texto cifrado + etiqueta GCM).
 */
public final class CifradoClaves {

    private static final String PREFIJO = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CifradoClaves() {
    }

    private static SecretKeySpec clave() {
        String b64 = System.getenv("DTE_ENCRYPTION_KEY");
        if (b64 == null || b64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(b64.trim());
            if (bytes.length != 32) {
                throw new IllegalStateException("DTE_ENCRYPTION_KEY debe ser de 32 bytes (base64).");
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("DTE_ENCRYPTION_KEY no es base64 válido.");
        }
    }

    public static boolean disponible() {
        try {
            return clave() != null;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static boolean estaCifrado(String valor) {
        return valor != null && valor.startsWith(PREFIJO);
    }

    public static String cifrar(String plano) {
        SecretKeySpec k = clave();
        if (k == null) {
            throw new IllegalStateException("Falta la variable de entorno DTE_ENCRYPTION_KEY en el servidor.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = c.doFinal(plano.getBytes(StandardCharsets.UTF_8));
            byte[] todo = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, todo, 0, iv.length);
            System.arraycopy(cifrado, 0, todo, iv.length, cifrado.length);
            return PREFIJO + Base64.getEncoder().encodeToString(todo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto.", e);
        }
    }

    /** Devuelve el texto plano. Un valor sin prefijo es un secreto antiguo sin cifrar y se devuelve tal cual. */
    public static String descifrar(String valor) {
        if (!estaCifrado(valor)) {
            return valor;
        }
        SecretKeySpec k = clave();
        if (k == null) {
            throw new IllegalStateException("Falta la variable de entorno DTE_ENCRYPTION_KEY: no se pueden leer las claves guardadas.");
        }
        try {
            byte[] todo = Base64.getDecoder().decode(valor.substring(PREFIJO.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, todo, 0, IV_BYTES));
            return new String(c.doFinal(todo, IV_BYTES, todo.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar el secreto (¿cambió DTE_ENCRYPTION_KEY?).");
        }
    }
}
