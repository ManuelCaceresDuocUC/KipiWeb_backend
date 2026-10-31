# KIPIPOS · Etapa 3A (backend) — Ventas y caja

**Estado de verificación:** NO fue compilado en mi entorno (sin acceso a Maven Central). Solo revisión de sintaxis con javac: sin errores de sintaxis; los errores restantes son por dependencias que faltan en mi entorno (Spring, Lombok). Compila tú: `./mvnw -DskipTests compile` y pásame lo que salga.

**Si tuviste que corregir algo a mano en los archivos de la etapa 2**, no copies encima los que se repiten (`InventarioService`, `MovimientoInventario`, `Empresa`, `MovimientoInventarioRepository`): aplica `etapa3a.patch` o pásame tus versiones.

## Qué hace
- **Pagos mixtos.** Nueva tabla `pos_ventas_pagos`: una venta puede pagarse con varios medios (efectivo, tarjeta, transferencia, crédito). La suma debe ser igual al total o el servidor rechaza la venta. Endpoint nuevo `POST /api/pagos/venta` (los antiguos `/efectivo`, `/cobrar`, `/credito` siguen funcionando). `pos_ventas.metodo_pago` queda como el medio único o `MIXTO`.
- **Transferencia.** El cajero debe confirmar que el dinero llegó. Lleva N° de referencia opcional. El total del turno tiene columna propia (`ventas_transferencia`) y no entra al cajón. Los comprobantes adjuntos llegan en la 3B.
- **Fiado por empresa** (`empresas.permite_fiado`, apagado por defecto) **con límite estricto por cliente**: se valida en la base con un UPDATE atómico, así dos ventas simultáneas no pueden pasarse del límite. Límite $0 = sin crédito. `PATCH /api/clientes/{id}` (admin) cambia el límite.
- **Estado de cuenta del cliente** (`cliente_movimientos`): cada cargo, abono, anulación y ajuste con su saldo. `GET /api/clientes/{id}/movimientos`. Los abonos también aceptan transferencia y son atómicos.
- **Anulación de ventas** (solo admin): `POST /api/ventas/{id}/anular` con motivo. Repone exactamente el stock que descontó, revierte la deuda del cliente y, si el turno sigue abierto, corrige sus totales. Si el turno ya estaba cerrado NO lo modifica y avisa para que registres el retiro del efectivo devuelto. No emite nota de crédito: eso viene con la etapa 4 (DTE).
- **Auditoría de caja** (`movimientos_caja`): ingresos, retiros, abonos y anulaciones quedan con motivo, usuario y hora. Ahora el motivo es obligatorio. `GET /api/caja/movimientos` (turno actual) y `GET /api/caja/historial/{turnoId}/movimientos` (admin).
- **Idempotencia:** el frontend manda una clave por intento de cobro (`pos_ventas.idempotency_key`, única por empresa). Si se corta la red y reintentan, devuelve la venta ya registrada en vez de cobrar doble.
- Los totales del turno se suman con un UPDATE atómico (ya no se pisan entre sí) y el dashboard excluye las ventas anuladas.
- Cuando hay pago mixto, la boleta electrónica se emite si ALGUNO de los medios usados está configurado para emitir (el fiado nunca emite).

## Orden de despliegue
1. Despliega el backend (Hibernate crea tablas y columnas).
2. Corre `sql/05_etapa3a_datos_existentes.sql` en Railway: enciende el fiado solo en las empresas que ya lo usaban, y crea la línea de pago de las ventas antiguas. Repetible.
3. Despliega el frontend (zip aparte). Si el frontend nuevo sale antes que el backend, el cobro falla (endpoint nuevo).

## Ojo
- **Fiado apagado por defecto.** Se activa en Administración → Gestión de Cartera.
- Revisa la consulta (c) del SQL: clientes que ya deben más que su límite. Podrán pagar, pero no comprar más a crédito hasta bajar la deuda.
- Ventas anteriores a la etapa 2 no tienen historial de stock: al anularlas no reponen inventario.
- Los vales de crédito se siguen intentando imprimir desde el servidor (no hay impresora en Render; falla sin romper la venta). La impresión real llega en la 3B.
- `etapa3a.patch` es el diff completo respecto a la etapa 2.
