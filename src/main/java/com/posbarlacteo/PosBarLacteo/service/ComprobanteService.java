package com.posbarlacteo.PosBarLacteo.service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.ServicioExternoException;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Guarda los comprobantes de transferencia en un bucket S3 PRIVADO. Nunca se expone el bucket: el frontend
 * recibe una URL firmada que vence a los pocos minutos. Compatible con servicios S3 (R2, MinIO) vía S3_ENDPOINT.
 */
@Slf4j
@Service
public class ComprobanteService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final String bucket;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String endpoint;

    private S3Client cliente;
    private S3Presigner firmador;

    public ComprobanteService(@Value("${app.s3.bucket:}") String bucket,
                              @Value("${app.s3.region:sa-east-1}") String region,
                              @Value("${app.s3.access-key:}") String accessKey,
                              @Value("${app.s3.secret-key:}") String secretKey,
                              @Value("${app.s3.endpoint:}") String endpoint) {
        this.bucket = bucket;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.endpoint = endpoint;
    }

    public boolean habilitado() {
        return !bucket.isBlank() && !accessKey.isBlank() && !secretKey.isBlank();
    }

    /** Resultado de una subida. */
    public record Archivo(String key, String nombre, String tipo) { }

    /** Valida (tamaño y tipo REAL por firma del archivo, no por lo que declare el navegador) y sube. */
    public Archivo subir(Long empresaId, Long ventaId, MultipartFile archivo) {
        return subirEn(empresaId, "ventas", ventaId, archivo);
    }

    /** Igual que subir, pero en otra carpeta del bucket (p. ej. "compras" para las facturas de proveedores). */
    public Archivo subirEn(Long empresaId, String carpeta, Long id, MultipartFile archivo) {
        exigirHabilitado();
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("Debes adjuntar un archivo.");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new NegocioException("El archivo supera el máximo de 5 MB.");
        }
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el archivo adjunto.");
        }
        String[] tipo = detectarTipo(datos); // {content-type, extensión}
        if (tipo == null) {
            throw new NegocioException("Formato no permitido. Sube una imagen (JPG, PNG, WEBP) o un PDF.");
        }

        String key = "empresas/" + empresaId + "/" + carpeta + "/" + id + "/" + UUID.randomUUID() + "." + tipo[1];
        try {
            cliente().putObject(PutObjectRequest.builder()
                    .bucket(bucket).key(key).contentType(tipo[0])
                    .serverSideEncryption("AES256")
                    .build(), RequestBody.fromBytes(datos));
        } catch (RuntimeException e) {
            log.error("No se pudo subir el comprobante a S3: {}", e.getMessage(), e);
            throw new ServicioExternoException("No se pudo guardar el comprobante. Intenta nuevamente.");
        }
        return new Archivo(key, nombreSeguro(archivo.getOriginalFilename(), tipo[1]), tipo[0]);
    }

    /** Guarda un documento generado por el sistema (p. ej. el PDF de la boleta). Devuelve la clave o null si no hay S3. */
    public String guardarBytes(String key, byte[] datos, String contentType) {
        if (!habilitado() || datos == null || datos.length == 0) {
            return null;
        }
        try {
            cliente().putObject(PutObjectRequest.builder()
                    .bucket(bucket).key(key).contentType(contentType)
                    .serverSideEncryption("AES256")
                    .build(), RequestBody.fromBytes(datos));
            return key;
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar {} en S3: {}", key, e.getMessage());
            return null;
        }
    }

    public byte[] leer(String key) {
        exigirHabilitado();
        try {
            return cliente().getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        } catch (RuntimeException e) {
            log.error("No se pudo leer {} de S3: {}", key, e.getMessage(), e);
            throw new ServicioExternoException("No se pudo abrir el documento guardado.");
        }
    }

    /** URL temporal (10 min) para ver el comprobante. */
    public String urlTemporal(String key) {
        exigirHabilitado();
        try {
            return firmador().presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10))
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build()).url().toString();
        } catch (RuntimeException e) {
            log.error("No se pudo firmar la URL del comprobante: {}", e.getMessage(), e);
            throw new ServicioExternoException("No se pudo abrir el comprobante.");
        }
    }

    /** Borrado "mejor esfuerzo" (al reemplazar un comprobante). */
    public void eliminar(String key) {
        if (key == null || key.isBlank() || !habilitado()) {
            return;
        }
        try {
            cliente().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar el comprobante {} de S3: {}", key, e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------------

    public record Objeto(String key, long bytes, java.time.Instant fecha) { }

    /** Lista los objetos bajo un prefijo, del más reciente al más antiguo (máx. {@code max}). Requiere permiso s3:ListBucket. */
    public List<Objeto> listar(String prefijo, int max) {
        exigirHabilitado();
        List<Objeto> todos = new ArrayList<>();
        String token = null;
        do {
            ListObjectsV2Request.Builder rb = ListObjectsV2Request.builder().bucket(bucket).prefix(prefijo);
            if (token != null) {
                rb.continuationToken(token);
            }
            ListObjectsV2Response r = cliente().listObjectsV2(rb.build());
            r.contents().forEach(o -> todos.add(new Objeto(o.key(), o.size() == null ? 0 : o.size(), o.lastModified())));
            token = Boolean.TRUE.equals(r.isTruncated()) ? r.nextContinuationToken() : null;
        } while (token != null && todos.size() < 5000);
        todos.sort(Comparator.comparing(Objeto::fecha).reversed());
        return todos.size() > max ? new ArrayList<>(todos.subList(0, max)) : todos;
    }

    private void exigirHabilitado() {
        if (!habilitado()) {
            throw new NegocioException("El almacenamiento de comprobantes no está configurado en el servidor.");
        }
    }

    private synchronized S3Client cliente() {
        if (cliente == null) {
            var b = S3Client.builder()
                    .httpClient(UrlConnectionHttpClient.create())
                    .region(Region.of(region))
                    .credentialsProvider(credenciales());
            if (!endpoint.isBlank()) {
                b.endpointOverride(URI.create(endpoint))
                 .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
            }
            cliente = b.build();
        }
        return cliente;
    }

    private synchronized S3Presigner firmador() {
        if (firmador == null) {
            var b = S3Presigner.builder().region(Region.of(region)).credentialsProvider(credenciales());
            if (!endpoint.isBlank()) {
                b.endpointOverride(URI.create(endpoint))
                 .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
            }
            firmador = b.build();
        }
        return firmador;
    }

    private StaticCredentialsProvider credenciales() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    @PreDestroy
    void cerrar() {
        if (cliente != null) cliente.close();
        if (firmador != null) firmador.close();
    }

    /** Identifica el archivo por sus primeros bytes. */
    static String[] detectarTipo(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return new String[] {"image/jpeg", "jpg"};
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return new String[] {"image/png", "png"};
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return new String[] {"image/webp", "webp"};
        }
        if (d.length >= 5 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F' && d[4] == '-') {
            return new String[] {"application/pdf", "pdf"};
        }
        return null;
    }

    private static String nombreSeguro(String original, String ext) {
        String base = original == null ? "comprobante" : original.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.length() > 80) base = base.substring(base.length() - 80);
        return base.isBlank() ? "comprobante." + ext : base;
    }
}
