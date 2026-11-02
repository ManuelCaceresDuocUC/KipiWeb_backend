UPDATE empresas SET mora_desde = NOW() - INTERVAL 2 DAY WHERE id = 5;

UPDATE empresas SET mora_desde = NOW() - INTERVAL 10 DAY WHERE id = 5;

UPDATE empresas
SET suscripcion_cancelada = 1, acceso_hasta = NOW() - INTERVAL 1 DAY
WHERE id = 5;

UPDATE empresas
SET estado = 'ACTIVA', mora_desde = NULL, acceso_hasta = NULL, suscripcion_cancelada = 0
WHERE id = 5;