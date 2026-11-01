# KIPIPOS · Etapa 4 (backend) — Facturación electrónica multi-proveedor

**Estado de verificación:** NO fue compilado (sin acceso a Maven Central). Solo revisión de sintaxis con javac, sin errores de sintaxis. El cifrado AES-GCM sí lo probé aislado (cifra, descifra, detecta alteraciones, lee valores antiguos sin cifrar). **La emisión contra Haulmer y LibreDTE no pude probarla**: hay que probarla en sandbox (ver "Qué probar").

## ANTES de desplegar (obligatorio)
1. **Borra** `src/main/java/com/posbarlacteo/PosBarLacteo/service/OpenFacturaService.java` (ver ELIMINAR.txt). Si queda, no compila.
2. **Crea la clave de cifrado** y guárdala en Render → Environment como `DTE_ENCRYPTION_KEY`. En PowerShell, línea por línea:
```
$b = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Convert]::ToBase64String($b)
```
   Copia el resultado a Render y **respáldalo en un administrador de contraseñas**. Si se pierde, las API keys guardadas no se pueden recuperar y cada cliente debe volver a ingresarlas. Sin esta variable el servidor arranca, pero se niega a guardar API keys.
3. Despliega el backend. En el log de arranque debe aparecer "Se cifraron las API keys DTE de N configuración(es)": las llaves que ya estaban en texto plano se cifran solas (repetible).

## Qué cambia
- **Strategy por proveedor** (paquete `dte`): interfaz `DteProveedor` + registro. Hoy: `OpenFacturaProveedor` y `LibreDteProveedor` (beta). Para un proveedor nuevo: una clase `@Component` que implemente la interfaz; no se toca nada más.
- **API keys cifradas** (AES-256-GCM) en la base; nunca vuelven al navegador ni a los logs.
- **Prueba de conexión real** contra el proveedor. Si el proveedor no da una respuesta concluyente, lo dice (no afirma que la llave sea buena).
- **Corrección importante:** la URL de producción de Haulmer era `/v2/dte/issue`, que la documentación no lista; ahora es `https://api.haulmer.com/v2/dte/document` (sandbox: `dev-api.haulmer.com`).
- **Si el proveedor falla, la venta NO se rompe:** antes devolvía error aunque la venta ya estaba cobrada. Ahora queda registrada, se imprime el comprobante interno con un aviso, y la boleta queda `PENDIENTE` (reintentable) o `REVISAR` (no se sabe si salió; hay que verificar en el portal antes de reintentar, para no duplicar).
- **Folio y estado guardados** (`pos_documentos_dte` y `pos_ventas.dte_estado/dte_folio`). Una restricción única y un "candado" evitan emitir dos veces la misma boleta aunque se haga doble clic. Copia del PDF de la boleta en S3 (si está configurado).
- **Nota de crédito automática al anular** una venta con boleta emitida. Si falla, la anulación igual queda y la nota queda pendiente de reintento (admin). Si la boleta nunca había salido, se cancela y no se emite nada.
- Endpoints nuevos: `POST /api/ventas/{id}/boleta/reintentar`, `GET /api/ventas/{id}/boleta`, `POST /api/ventas/{id}/nota-credito/reintentar` (admin), `GET /api/dte/proveedores`, `GET /api/dte/pendientes`.
- Tablas/columnas nuevas las crea Hibernate solo. No hay SQL.

## Qué probar (en sandbox, antes de dar esto a un cliente)
1. Config DTE → Haulmer → ambiente **Sandbox** → llave de pruebas → "Probar conexión". Si dice "no se pudo confirmar", no es grave: lo definitivo es el paso 2.
2. Vender en efectivo: debe imprimir la boleta 80 mm y mostrarse en Movimientos con su folio. **Si no aparece folio**, mándame la respuesta de Haulmer (el log del servidor muestra las claves recibidas).
3. Poner una API key mala y vender: la venta debe quedar registrada con aviso, "BOLETA PENDIENTE" en Movimientos. Corregir la llave y "Reintentar boleta".
4. Anular esa venta (admin): debe emitir la nota de crédito. **No verificado:** que Haulmer acepte una nota de crédito (tipo 61) que referencie una *boleta* con receptor genérico. Consulta con tu contador/proveedor si el SII lo exige distinto.
5. LibreDTE: sin probar. Los datos de la respuesta de "generar" y si en boletas los precios van con IVA incluido los validamos en la primera prueba real.

## Ojo
- La emisión de Haulmer ahora pide también `FOLIO` en la respuesta; si el proveedor lo rechazara, se reintenta sin él automáticamente.
- Haulmer limita a 3 solicitudes/segundo y 100/minuto por llave.
- Pago mixto con crédito: la boleta se emite por el total de la venta (decisión de la 3A).
- Venta con boleta emitida cuyo proveedor luego se cambia: la nota de crédito no se emite sola (avisa que debe hacerse en el portal del proveedor original).
- Si la respuesta del proveedor se pierde (corte justo al emitir), el documento queda `REVISAR` a propósito; no se reintenta solo.
