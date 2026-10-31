-- ============================================================================
-- ETAPA 2 · Migrar el stock antiguo (pos_productos.stock) al stock por sucursal
-- ============================================================================
-- CUÁNDO: UNA VEZ, justo después del primer deploy del backend de la etapa 2
--         (Hibernate crea movimientos_inventario y la restricción única al arrancar).
-- Es IDEMPOTENTE: cada producto migrado queda marcado con un movimiento
-- 'MIGRACION_ETAPA2'; si lo corres dos veces, la segunda no hace nada.
-- SUMA el stock antiguo al que ya exista en inventario_sucursal (no lo pisa), así
-- no se pierden las ventas hechas entre el deploy y la migración.
-- Destino: la casa matriz de cada empresa (si no hay, la sucursal activa con menor id).
-- pos_productos.stock NO se modifica (queda como respaldo; ya no se usa).
-- ============================================================================

-- ---- PASO 0 · SIMULACIÓN (solo lectura): qué se va a migrar -----------------
SELECT p.empresa_id, p.id AS producto_id, p.descripcion, p.stock AS stock_antiguo,
       d.sucursal_id AS sucursal_destino,
       COALESCE(i.stock, 0) AS stock_actual_en_destino
FROM pos_productos p
JOIN (
    SELECT s.empresa_id,
           COALESCE(MIN(CASE WHEN s.es_casa_matriz = 1 THEN s.id END), MIN(s.id)) AS sucursal_id
    FROM sucursales s WHERE s.activo = 1 GROUP BY s.empresa_id
) d ON d.empresa_id = p.empresa_id
LEFT JOIN inventario_sucursal i ON i.producto_id = p.id AND i.sucursal_id = d.sucursal_id
WHERE NOT EXISTS (SELECT 1 FROM movimientos_inventario m
                  WHERE m.producto_id = p.id AND m.motivo = 'MIGRACION_ETAPA2')
ORDER BY p.empresa_id, p.id;

-- Empresas SIN sucursal activa (sus productos no se migrarían): debe devolver 0 filas
SELECT e.id AS empresa_sin_sucursal
FROM empresas e
WHERE NOT EXISTS (SELECT 1 FROM sucursales s WHERE s.empresa_id = e.id AND s.activo = 1);

-- ---- PASO 1 · MIGRACIÓN REAL (ejecutar en bloque) ----------------------------
START TRANSACTION;

DROP TABLE IF EXISTS tmp_mig_destino;
CREATE TABLE tmp_mig_destino AS
    SELECT s.empresa_id,
           COALESCE(MIN(CASE WHEN s.es_casa_matriz = 1 THEN s.id END), MIN(s.id)) AS sucursal_id
    FROM sucursales s WHERE s.activo = 1 GROUP BY s.empresa_id;

DROP TABLE IF EXISTS tmp_mig_pend;
CREATE TABLE tmp_mig_pend AS
    SELECT p.id AS producto_id, p.empresa_id, d.sucursal_id, p.stock AS legacy
    FROM pos_productos p
    JOIN tmp_mig_destino d ON d.empresa_id = p.empresa_id
    WHERE NOT EXISTS (SELECT 1 FROM movimientos_inventario m
                      WHERE m.producto_id = p.id AND m.motivo = 'MIGRACION_ETAPA2');

INSERT INTO inventario_sucursal (producto_id, sucursal_id, stock, stock_critico)
    SELECT producto_id, sucursal_id, legacy, 0 FROM tmp_mig_pend
ON DUPLICATE KEY UPDATE stock = stock + VALUES(stock);

INSERT INTO movimientos_inventario
    (empresa_id, sucursal_id, producto_id, tipo, cantidad, stock_resultante, motivo, usuario_nombre, fecha_hora)
    SELECT t.empresa_id, t.sucursal_id, t.producto_id, 'INICIAL', t.legacy, i.stock,
           'MIGRACION_ETAPA2', 'sistema', NOW()
    FROM tmp_mig_pend t
    JOIN inventario_sucursal i ON i.producto_id = t.producto_id AND i.sucursal_id = t.sucursal_id;

DROP TABLE tmp_mig_pend;
DROP TABLE tmp_mig_destino;

COMMIT;

-- ---- PASO 2 · VERIFICACIÓN ---------------------------------------------------
-- (a) Productos sin migrar: debe ser 0
SELECT COUNT(*) AS sin_migrar FROM pos_productos p
WHERE NOT EXISTS (SELECT 1 FROM movimientos_inventario m
                  WHERE m.producto_id = p.id AND m.motivo = 'MIGRACION_ETAPA2');
-- (b) Total antiguo vs total nuevo por empresa (la diferencia = ventas hechas tras el deploy)
SELECT p.empresa_id, SUM(p.stock) AS total_antiguo,
       (SELECT SUM(i.stock) FROM inventario_sucursal i
          JOIN pos_productos p2 ON p2.id = i.producto_id WHERE p2.empresa_id = p.empresa_id) AS total_nuevo
FROM pos_productos p GROUP BY p.empresa_id;
