# Etapa 8 – Backend (gestión de usuarios)
Copia los archivos respetando las rutas. No requiere SQL: Hibernate agrega la columna `usuarios.activo` (los usuarios existentes quedan NULL = activos).
Nuevo: POST /api/usuarios, PUT /api/usuarios/{id} (rol/sucursal), PATCH /api/usuarios/{id}/estado. Solo ADMIN.
Protecciones: no puedes editarte ni desactivarte a ti mismo ni al último admin activo. Un usuario desactivado pierde acceso en ~30 s.
Límites: cambiar contraseña o rol no invalida JWT ya emitidos (10 h). No compilado con Maven (solo javac sin dependencias).
