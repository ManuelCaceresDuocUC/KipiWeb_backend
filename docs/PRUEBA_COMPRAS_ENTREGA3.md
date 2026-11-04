# Entrega 3 · Compras y proveedores

## Antes de desplegar
- Backend: tablas nuevas (se crean solas): `pos_proveedores`, `pos_compras`, `pos_compra_items`, `pos_compra_pagos`.
- La foto/PDF del documento usa el mismo bucket S3 privado de los comprobantes de transferencia. Si en Render no están las variables `app.s3.*`, la compra funciona igual pero no se pueden adjuntar documentos (la pantalla lo indica).
- Frontend: sin dependencias nuevas. Aparece "Compras" en el menú (solo administradores).

## Pruebas
1. Compras → Proveedores → "+ Nuevo proveedor" (probar nombre repetido: debe rechazarlo).
2. Nueva compra: elegir proveedor, tipo Factura, folio 123, agregar 2 productos con cantidad y costo. Marcar "valores netos": el total sube 19 %.
   - Dejar marcado "Subir el stock" y "Actualizar el costo": en Inventario el stock sube y el costo cambia; en Inventario → Movimientos de stock aparece "Ingreso de mercadería · Compra N°…".
   - Registrar de nuevo la misma factura (mismo proveedor, tipo y folio): debe rechazarla.
3. Compra a crédito (sin marcar "Ya la pagué", con vencimiento): aparece "Por pagar"; registrar un pago parcial y luego el saldo → pasa a "Pagada". Probar pagar más que el saldo: debe rechazarlo.
4. Poner un vencimiento pasado: debe verse "Vencida" y sumarse al total "Vencido" de arriba.
5. Adjuntar una foto grande del celular: se reduce y se puede abrir con "Ver documento".
6. Anular una compra que subió stock: el stock baja lo mismo. Si ya vendiste ese producto y no alcanza el stock, avisa y no anula.
7. Con un usuario vendedor: no ve "Compras" y la API responde 403.

## Alcance y límites
- El costo del producto queda como el valor unitario ingresado (si marcas "netos", sin IVA). Cómo tratar el IVA de compras es tema de tu contador.
- Los pagos a proveedores NO descuentan de la caja del turno (son un control aparte).
- Anular una compra no devuelve el costo anterior de los productos.
- Máx. 200 líneas por compra, 500 compras por consulta.
