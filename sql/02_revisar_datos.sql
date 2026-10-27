-- ETAPA 1 · Revisiones de datos (solo lectura salvo que descomentes el UPDATE).

-- A) Clientes sin empresa (los creaba el POS por un bug; desde ahora siempre se asigna).
SELECT COUNT(*) AS clientes_sin_empresa FROM pos_clientes WHERE empresa_id IS NULL;

-- Si tienes UNA sola empresa en producción, puedes asignarlos (cambia 1 por el id real):
-- UPDATE pos_clientes SET empresa_id = 1 WHERE empresa_id IS NULL;
-- Si hay varias empresas, no adivines: revísalos a mano antes de asignarlos.

-- B) Nombres de usuario repetidos entre empresas (el login ahora los resuelve por contraseña,
--    pero conviene que sean únicos).
SELECT usuario, COUNT(*) AS veces FROM pos_usuarios GROUP BY usuario HAVING COUNT(*) > 1;

-- C) Roles guardados con otros nombres (ahora se normalizan a 'admin' / 'vendedor' al iniciar sesión).
SELECT rol, COUNT(*) FROM pos_usuarios GROUP BY rol;

-- D) Contraseñas aún en texto plano (se migran solas a BCrypt en el primer login correcto).
SELECT COUNT(*) AS con_texto_plano FROM pos_usuarios
WHERE contrasena IS NOT NULL AND contrasena NOT LIKE '$2%';
