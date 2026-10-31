-- ============================================================================
-- ETAPA 3A · Ajustar los datos que ya existían
-- ============================================================================
-- CUÁNDO: UNA VEZ, después del primer deploy del backend 3A (Hibernate crea las tablas
--         pos_ventas_pagos, movimientos_caja, cliente_movimientos y las columnas nuevas).
-- Es seguro repetirlo: cada paso solo toca filas que aún no fueron ajustadas.
-- ============================================================================

START TRANSACTION;

-- 1) FIADO: desde esta versión viene APAGADO por empresa. Se enciende solo para las empresas
--    que ya trabajaban con crédito (algún cliente con límite o con deuda), para no cortarles el servicio.
UPDATE empresas
SET permite_fiado = 1
WHERE permite_fiado IS NULL
  AND id IN (SELECT DISTINCT empresa_id FROM pos_clientes
             WHERE empresa_id IS NOT NULL AND (COALESCE(limite_credito, 0) > 0 OR COALESCE(deuda_actual, 0) > 0));

-- 2) Estado de las ventas antiguas
UPDATE pos_ventas SET estado = 'COMPLETADA' WHERE estado IS NULL;

-- 3) Cada venta antigua pasa a tener su línea de pago (un solo medio por el total)
INSERT INTO pos_ventas_pagos (venta_id, metodo, monto, confirmado, confirmado_por, fecha_confirmacion)
SELECT v.id, UPPER(v.metodo_pago), ROUND(v.total), 1, NULL, v.fecha_hora
FROM pos_ventas v
WHERE UPPER(v.metodo_pago) IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA', 'CREDITO')
  AND NOT EXISTS (SELECT 1 FROM pos_ventas_pagos pg WHERE pg.venta_id = v.id);

COMMIT;

-- ---- VERIFICACIÓN -------------------------------------------------------------
-- (a) Ventas sin línea de pago: debería ser 0 (o solo ventas con un método fuera de los 4 conocidos)
SELECT COUNT(*) AS ventas_sin_pago FROM pos_ventas v
WHERE NOT EXISTS (SELECT 1 FROM pos_ventas_pagos pg WHERE pg.venta_id = v.id);

-- (b) Empresas con fiado activo
SELECT id, razon_social, permite_fiado FROM empresas ORDER BY id;

-- (c) Clientes con deuda MAYOR a su límite (de antes de que el límite se exigiera): revísalos
SELECT id, empresa_id, nombre, limite_credito, deuda_actual FROM pos_clientes
WHERE COALESCE(deuda_actual, 0) > COALESCE(limite_credito, 0);
