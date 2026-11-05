# Entrega 5 · Cierre de caja por correo

## Antes de desplegar
- Sin tablas nuevas. Necesita que el correo del servidor esté configurado (`spring.mail.*`, igual que los otros correos de KIPIPOS).
- Opcional: `caja.correo-cierre-habilitado=false` apaga el envío automático (por defecto está activo).

## Qué hace
- Al cerrar un turno, cada administrador de la empresa (con correo real) recibe un resumen: sucursal, cajero, horario, N° de ventas y total, ventas por método (efectivo, tarjeta, transferencia, crédito), ingresos extra, abonos, retiros, y la cuadratura (fondo, esperado, contado, diferencia). La diferencia también va en el asunto.
- Se envía después de guardar el cierre y en segundo plano: si el correo falla, el cierre queda guardado igual y solo se anota en el log.
- Se omiten los correos ficticios "@...local" de empleados sin correo.
- Administración → Cuadraturas: botón "Enviarme resumen" en cada turno cerrado para reenviarlo a tu propio correo.

## Pruebas
1. Con 2 administradores con correo real, abrir caja, hacer 2 ventas (una en efectivo y otra con tarjeta), cerrar con el efectivo contado: ambos reciben el correo. Revisar spam la primera vez.
2. Contar $1.000 de menos: el asunto dice "FALTAN $1.000".
3. Anular una venta antes de cerrar: el correo indica "(1 anuladas)" y no la suma al total.
4. Administración → Cuadraturas → "Enviarme resumen" de un turno antiguo: llega solo a ti.
5. Apagar el correo del servidor (o sin variables): cerrar la caja igual funciona.
6. Un vendedor no puede llamar al reenvío (403).

## Pendiente de esta línea
- Soporte de facturas (33/34) en los proveedores DTE: sigue sin implementarse; requiere una cuenta real del proveedor para validar.
