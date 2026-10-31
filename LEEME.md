# KIPIPOS · Etapa 2 (backend) — Inventario por sucursal

**Estado de verificación:** NO fue compilado en mi entorno (sin acceso a Maven Central). Solo hice revisión de sintaxis con javac; los errores que quedan allí son de Lombok, que no estaba disponible. Compila tú: `./mvnw -DskipTests compile` y pégame los errores si salen.

## Qué cambia
- **Catálogo por empresa, stock por sucursal.** Tabla `inventario_sucursal` (única por producto+sucursal). `Producto.stock` pasó a ser un campo calculado (no persistente) con el stock de la sucursal consultada; la columna antigua `pos_productos.stock` queda como `stockLegacy` (siempre 0, solo respaldo).
- **Ventas descuentan de la sucursal del usuario**, atómicamente (UPDATE condicional, sin carreras) y en orden fijo de producto para evitar deadlocks. Si falta stock y la empresa no permite negativos → 409/400 "Stock insuficiente de X" y la venta completa se revierte.
- **Stock negativo por empresa:** `empresas.permite_stock_negativo` (nulo = permitido, como hasta hoy). Se cambia desde la pantalla de Inventario.
- **Historial** `movimientos_inventario` (venta, entrada, merma, ajuste, inicial) con usuario, motivo y stock resultante.
- **Endpoints nuevos (solo admin)** `/api/inventario`: `GET /sucursales`, `GET|PUT /configuracion`, `POST /movimientos`, `GET /movimientos`.
- `PUT/PATCH /api/productos` **ignoran `stock`** (solo se cambia vía movimientos). `POST` toma el stock como stock inicial en la sucursal del usuario. Se agrega `costo` opcional; el costo de inventario del dashboard usa `COALESCE(costo, precio)`.
- Un producto nuevo no crea filas en otras sucursales: se crean al primer movimiento/venta (equivale a stock 0).

## Orden de despliegue (importante)
1. Despliega el backend (Hibernate crea tablas/columnas con `ddl-auto=update`).
2. Ejecuta **`sql/04_migrar_stock_a_sucursal.sql`** en Railway (primero el PASO 0 de simulación, luego el PASO 1). Es idempotente y suma, no pisa.
3. Despliega el frontend (zip aparte).
Entre el paso 1 y 2 el stock mostrado será parcial: hazlo seguido.

## Ojo
- Usa `INSERT IGNORE` nativo → **solo MySQL**.
- Si tu `inventario_sucursal` ya tenía filas con stock real del modelo anterior, el PASO 0 muestra `stock_actual_en_destino`: revísalo antes de migrar para no duplicar.
- Aún NO incluye: traslados entre sucursales, creación de sucursales ni gestión de usuarios.
- `etapa2.patch` es el diff completo respecto a la etapa 1.
