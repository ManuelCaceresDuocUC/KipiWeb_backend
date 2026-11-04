# Prueba — Entrega 1 (Caja)

## Terminales de pago
- Cobrar con tarjeta: ya NO pide Getnet / Mercado Pago / Transbank. Sale solo el aviso "Cobro con tarjeta → Pago aprobado en máquina".
- Las ventas antiguas con terminal siguen mostrándose en Movimientos.

## Comprobante al cobrar (modal de pago)
1. Imprimir (por defecto): igual que antes.
2. Por correo: aparece un campo; si eliges un cliente con correo se rellena. Tras cobrar NO imprime y envía el ticket/boleta. Si el envío falla, avisa y se reenvía desde Movimientos > "Enviar por correo".
3. Sin comprobante: no imprime (la boleta se emite igual si corresponde).
4. Con fiado: el vale para firmar se imprime SIEMPRE, aunque elijas otra opción.
5. Sin internet: el comprobante local se imprime salvo "Sin comprobante" (no hay cómo enviar correo).

## Venta a granel
1. Inventario → editar un producto de venta (no insumo) → unidad KG/GR/LT → marcar "Se vende por peso o medida".
2. Caja: al agregar ese producto (escáner, buscador o cámara) se abre un cuadro para digitar la cantidad (ej. 0,350) con el total en vivo.
3. En el carrito aparece "0,35 kg ✎" para corregir. Los demás productos siguen con − / +.
4. Un producto NO marcado rechaza decimales en el servidor (mensaje claro).
5. Verifica: el total del ticket = suma de las líneas; el stock baja 0,35; la boleta (sandbox) sale con la cantidad decimal.

## Devoluciones y cambios (Movimientos → Visualizar → "Registrar devolución o cambio")
- Elige devolución/cambio, cantidades, motivo. No cambia caja, deuda ni emite documentos.
- No deja devolver más de lo vendido (se descuenta lo ya devuelto).
- Casilla "Reponer el stock": solo administrador (un vendedor no la ve; el servidor la rechaza).
- Queda en el Registro de actividad (DEVOLUCION).
- OJO: si la venta tenía boleta, esto NO emite nota de crédito; consúltalo con tu contador.
