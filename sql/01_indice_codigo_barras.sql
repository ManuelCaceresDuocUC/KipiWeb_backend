-- ETAPA 1 · Código de barras único POR EMPRESA (antes era único global y bloqueaba a otros clientes).
-- Ejecutar ANTES de desplegar el backend nuevo. Hibernate (ddl-auto=update) crea solo la nueva restricción
-- uk_producto_codigo_empresa(codigo_barras, empresa_id), pero NO borra el índice viejo.

-- 1) Descubrir el nombre del índice único antiguo:
SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'pos_productos'
  AND COLUMN_NAME = 'codigo_barras';

-- 2) Borrar SOLO el índice con NON_UNIQUE = 0 que contenga únicamente codigo_barras
--    (reemplaza el nombre por el que devolvió la consulta; NO borres uk_producto_codigo_empresa):
-- ALTER TABLE pos_productos DROP INDEX <NOMBRE_DEL_INDICE_VIEJO>;
