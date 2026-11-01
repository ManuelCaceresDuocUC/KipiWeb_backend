# KIPIPOS · Etapa 3B (backend) — Tickets 80 mm, correo y comprobantes (S3)

**Estado de verificación:** NO fue compilado en mi entorno (sin acceso a Maven Central). Solo revisión de sintaxis con javac: sin errores de sintaxis; los errores restantes son por dependencias que faltan allí (Spring, Lombok, AWS SDK, OpenPDF). **No pude probar** la subida a S3, el envío por Gmail ni la generación real de los PDF. Compila tú: `./mvnw -DskipTests compile` y pásame lo que salga.

## Qué hace
- **Tickets 80 mm.** El alto de la página se ajusta al contenido (antes era fijo de 1200 pt y salía papel en blanco). El ticket muestra el desglose cuando el pago es mixto.
- **Vale de crédito y comprobante de abono** ahora se generan en memoria y se devuelven en base64 (`valePdf` en la venta, `comprobantePdf` en el abono). Se elimina la impresión desde el servidor (en Render no hay impresora) y los archivos en disco.
- **Reimprimir:** `GET /api/ventas/{id}/ticket` y `GET /api/ventas/{id}/vale`.
- **Correo (Gmail):** `POST /api/ventas/{id}/ticket/enviar` y `POST /api/clientes/{id}/estado-cuenta/enviar` (envía el estado de cuenta en PDF; si no se indica correo usa el del cliente).
- **Comprobante de transferencia:** `POST /api/ventas/{id}/comprobante` (multipart, `file` y `pagoId` opcional) y `GET /api/ventas/{id}/comprobantes` (URL firmada de 10 min). Valida por el contenido real del archivo (JPG, PNG, WEBP, PDF; máx. 5 MB), guarda en S3 con cifrado y nunca expone el bucket. Al reemplazar un comprobante se borra el anterior. La clave del objeto no se envía al navegador (solo `tieneComprobante`).
- No hay cambios de tablas: las columnas `comprobante_*` de `pos_ventas_pagos` ya se crearon en la 3A. **No hay SQL.**

## Variables de entorno nuevas (Render → Environment)
| Variable | Valor |
|---|---|
| `S3_BUCKET` | nombre del bucket |
| `S3_REGION` | región del bucket (ej. `sa-east-1`) |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | claves del usuario IAM (solo permisos sobre ese bucket) |
| `MAIL_USERNAME` | la cuenta Gmail de la app |
| `MAIL_PASSWORD` | la "contraseña de aplicación" de 16 letras (no la clave normal) |

Opcionales: `S3_ENDPOINT` (solo si usas un servicio compatible como R2), `MAIL_HOST`, `MAIL_PORT`.
Sin estas variables la app arranca igual: subir comprobantes o enviar correos responde un mensaje claro de "no configurado".

## Ojo
- **Reimprimir ticket** entrega el comprobante interno de la venta, no la boleta electrónica del proveedor DTE (eso se revisa en la etapa 4).
- Gmail limita el envío (unos 500 correos/día en cuentas normales). Para volumen alto habrá que pasar a un servicio de correo transaccional.
- El bucket debe ser **privado** (bloquear todo el acceso público).
- Si cambias de región o bucket, los comprobantes ya subidos no se mueven solos.
