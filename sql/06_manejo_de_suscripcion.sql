UPDATE empresas SET mora_desde = NOW() - INTERVAL 2 DAY WHERE id = 5;

UPDATE empresas SET mora_desde = NOW() - INTERVAL 10 DAY WHERE id = 5;

UPDATE empresas
SET suscripcion_cancelada = 1, acceso_hasta = NOW() - INTERVAL 1 DAY
WHERE id = 5;

UPDATE empresas
SET estado = 'ACTIVA', mora_desde = NULL, acceso_hasta = NULL, suscripcion_cancelada = 0
WHERE id = 5;

#manejo de prueba
UPDATE empresas SET estado='PRUEBA', prueba_hasta = NOW() + INTERVAL 9 DAY, prueba_aviso = 0 WHERE id = 6;

UPDATE empresas SET estado='PRUEBA', prueba_hasta = NOW() + INTERVAL 2 DAY, prueba_aviso = 0 WHERE id = 6;

UPDATE empresas SET estado='PRUEBA', prueba_hasta = NOW() - INTERVAL 1 DAY, prueba_aviso = 0 WHERE id = 6;

#reactivar cuenta de baja
UPDATE empresas
SET estado = 'PRUEBA',
    activo = 1,
    baja_solicitada_en = NULL,
    prueba_hasta = NOW() - INTERVAL 1 DAY,
    prueba_aviso = 3
WHERE id = 6 AND estado = 'BAJA';