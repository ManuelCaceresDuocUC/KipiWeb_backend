# Etapa 7 – Backend: contacto, verificación de correo, recuperación de clave, consentimiento

Copia sobre `kipiweb_backend` (mismas rutas). `application.properties` del ZIP es el completo con 3 bloques nuevos al final. Las tablas nuevas (`pos_verificaciones_correo`, `pos_tokens_recuperacion`) y columnas de Empresa (`terminos_*`) las crea Hibernate solo. **Sin compilar con Maven** (solo revisé sintaxis); si Render falla, pásame el log.

## Variables en Render
| Variable | Qué es |
|---|---|
| `TURNSTILE_SECRET` | Clave SECRETA de Cloudflare Turnstile. Sin ella el captcha no se verifica (el resto de protecciones sigue). |
| `LEGAL_TERMINOS_VERSION` | `2026-11-01` (debe ser igual a `LEGAL.version` del frontend). Si cambias los términos, cambia ambos. |
| `CONTACTO_DESTINO` | Opcional. Por defecto los mensajes llegan a `MAIL_USERNAME`. |

## Qué hace
- `POST /api/auth/contacto`: mensaje a tu correo (con Reply-To del consultante) + copia de confirmación al consultante. Límite 4/hora/IP, campo trampa y captcha.
- Registro: **exige** correo verificado con código de 6 dígitos (vence en 10 min, 5 intentos, 4 códigos/hora/correo) y **casilla de términos** con la versión vigente. Se guarda en la empresa: versión aceptada, fecha/hora e IP (`terminos_*`). Máx. 5 registros/hora/IP.
- Recuperación: `POST /api/auth/recuperar` (siempre responde igual, no revela si el usuario existe) y `/api/auth/restablecer`. Enlace de 30 min, un solo uso, solo se guarda su hash.
- Empleados: `PUT /api/usuarios/{id}/contrasena` y `/correo` (solo ADMIN, solo usuarios de su empresa). Empleados sin correo real siguen con `usuario@rut.local` y se recuperan vía admin.
- Limpieza diaria de códigos/enlaces vencidos.

## ⚠ Deploy
Despliega backend y frontend juntos: el frontend anterior no envía el token de verificación y el registro del backend nuevo lo exige.

## Límites conocidos
- Cambiar la contraseña no cierra sesiones ya abiertas (el JWT dura 10 h).
- Los límites por IP viven en memoria (válido para una sola instancia en Render).
- No existe aún pantalla para crear usuarios nuevos después del registro (solo se crean al registrar la empresa).
- La IP se toma de `X-Forwarded-For`; detrás de Render es el valor esperado.
