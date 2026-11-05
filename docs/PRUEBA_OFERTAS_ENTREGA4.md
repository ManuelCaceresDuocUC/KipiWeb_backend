# Entrega 4 · Ofertas y sugerencias de precio

## Antes de desplegar
- Sin tablas ni variables nuevas. Aparece "Ofertas" en el menú (solo administradores).
- Recuerda recargar sin caché después de desplegar (Ctrl+Shift+R).

## Cómo decide cada sección (período configurable: 15 a 90 días)
- **Sin movimiento:** stock > 0 y cero ventas en el período. Sugiere −10 %, redondeado a $10, solo si queda sobre costo + 5 %.
- **Margen bajo:** (precio − costo) / costo menor al mínimo (20 % por defecto, editable; se recuerda en el navegador). Sugiere el precio que alcanza el mínimo, redondeado hacia arriba a $10.
- **Más vendidos:** los 10 con más ingresos del período; avisa si el stock dura menos de 7 días.
- **Stock alto:** stock para más de 90 días de venta al ritmo actual (y al menos 10 unidades). Sugiere −5 % con el mismo piso.
- El stock se suma entre todas las sucursales. Las ventas anuladas no cuentan.

## Pruebas
1. Con ventas de los últimos días, abrir Ofertas: "Más vendidos" muestra los productos que vendiste.
2. Producto con stock y sin ventas en 30 días → aparece en "Sin movimiento" con precio sugerido.
3. Producto con costo (de una compra) y precio muy cercano → aparece en "Margen bajo". Cambiar el margen mínimo y ver cómo cambia la lista.
4. "Aplicar precio": confirma, el precio cambia en Inventario y en Caja, y la sugerencia desaparece. Queda registrado en la auditoría (PRECIO_SUGERIDO).
5. Con un usuario vendedor: no ve "Ofertas" y la API responde 403.

## Límites
- Solo sugiere; no hay ofertas con fecha de término automática (al terminar una oferta hay que volver a poner el precio normal a mano).
- No hay fecha de vencimiento por producto, por lo que no detecta "por vencer".
- Si el costo de un producto incluye o no IVA depende de cómo lo ingresas en las compras; el margen se calcula con el costo guardado.
