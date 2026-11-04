# Prueba: datos de empresa, habilitación asistida y boleta por pago de deuda

## 1. Datos de la empresa (Administración → "Datos de la empresa")
- Entra como administrador, cambia giro/dirección/comuna y guarda. Debe aparecer "Datos actualizados".
- Recarga: los datos persisten. El nombre en el menú lateral cambia si editaste la razón social.
- El RUT aparece bloqueado. Un vendedor no ve Administración (y el PUT devuelve 403).
- Registro de actividad: aparece EMPRESA_DATOS con el antes/después.

## 2. Boleta al pagar deuda
1. Configuración DTE → activar "Cuando un cliente paga su deuda (fiado)" → guardar (usa ambiente sandbox).
2. Caja → consultar cliente con deuda → abonar parcial en EFECTIVO. Esperado: se imprime boleta + comprobante de abono, aviso "Boleta N° X emitida".
3. Abonar con TARJETA: solo emite si "Ventas con Tarjeta" está activo (regla del medio de pago).
4. Con la casilla desactivada: solo comprobante de abono (como antes).
5. Con la API Key mal puesta: el abono queda registrado y el aviso dice que la boleta no se pudo emitir (queda PENDIENTE; el id del documento es negativo = abono).
6. La deuda, la caja y el estado de cuenta deben quedar igual que antes (el abono NO crea una venta ni mueve reportes de ventas).

## 3. Textos legales
- /terminos: nueva sección "Habilitación asistida" y párrafo de fiado en DTE. /privacidad: nuevo punto de credenciales.
- Version nueva: 2026-11-05. En Render: LEGAL_TERMINOS_VERSION=2026-11-05 (si no coincide, el registro se rechaza).
