# Etapa 10 – Backend
Copia los archivos respetando las rutas. Hibernate crea solo la tabla `auditoria_eventos` y la columna `usuarios.sesion_desde` (no hay SQL).
- Sesiones revocables: al restablecer la contraseña (admin o enlace por correo) o cambiar rol/sucursal, las sesiones abiertas de ese usuario se cierran (401 con mensaje claro). Desactivar ya cortaba el acceso.
- Registro de actividad: GET /api/admin/auditoria (solo ADMIN). Registra creación/edición/activación de usuarios, cambios de contraseña y correo, anulación de ventas y cambios de configuración de facturación. Nunca guarda contraseñas ni API Keys.
- Chequeo de salud: GET /api/auth/salud (público) para el monitor de disponibilidad.
- docs/CHECKLIST_PRUEBA_E2E.md y docs/OPERACION.md (respaldos y monitoreo).
Límites: las sesiones revocadas pueden tardar hasta ~30 s en cortarse (caché). Los límites de intentos siguen en memoria: valen mientras Render corra una sola instancia.
No compilado con Maven (solo javac sin dependencias).
