START TRANSACTION;

-- 1) Fiado: solo en empresas que ya trabajaban con crédito
UPDATE empresas
SET permite_fiado = 1
WHERE id > 0
  AND permite_fiado IS NULL
  AND id IN (SELECT DISTINCT empresa_id FROM pos_clientes
             WHERE empresa_id IS NOT NULL
               AND (COALESCE(limite_credito, 0) > 0 OR COALESCE(deuda_actual, 0) > 0));

-- 2) Estado de las ventas antiguas
UPDATE pos_ventas SET estado = 'COMPLETADA' WHERE id > 0 AND estado IS NULL;

-- 3) Línea de pago para cada venta antigua
INSERT INTO pos_ventas_pagos (venta_id, metodo, monto, confirmado, confirmado_por, fecha_confirmacion)
SELECT v.id, UPPER(v.metodo_pago), ROUND(v.total), 1, NULL, v.fecha_hora
FROM pos_ventas v
WHERE UPPER(v.metodo_pago) IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA', 'CREDITO')
  AND NOT EXISTS (SELECT 1 FROM pos_ventas_pagos pg WHERE pg.venta_id = v.id);

COMMIT;