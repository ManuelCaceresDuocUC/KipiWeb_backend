# Panel del dueño (backend)

API privada `/api/owner/**` que alimenta la app de escritorio **KIPIPOS Panel**. No usa el login de las empresas:
se autentica con un token secreto único (`OWNER_API_TOKEN`). Sin esa variable, el panel está desactivado (responde 404).

## 1. Activarlo en Render
1. Genera un token largo (en tu PC): `openssl rand -hex 32` (o cualquier cadena aleatoria de 40+ caracteres).
2. Render → servicio del backend → **Environment** → `OWNER_API_TOKEN` = ese valor. Guarda y espera el redeploy.
3. Guarda el token en tu gestor de contraseñas. Es la llave de todas las empresas: no lo compartas ni lo subas a GitHub.
4. Intentos fallidos: 10 cada 15 min por IP, luego responde 429.

## 2. Qué expone
| Ruta | Qué hace |
|---|---|
| `GET /resumen` | Conteo por estado, ventas de hoy, errores 24 h, estado de respaldos, datos del servidor |
| `GET /empresas` | Todas las empresas con estado, vigencia, ventas 30 días, última venta, usuarios, correo admin |
| `GET /empresas/{id}` | Detalle, usuarios (sin contraseñas), últimos eventos de auditoría |
| `POST /empresas/{id}/suspender` `{motivo}` | Deja la cuenta en **solo lectura** (bloqueo manual) |
| `POST /empresas/{id}/reactivar` | Quita el bloqueo manual |
| `POST /empresas/{id}/sincronizar` | Consulta Flow y actualiza el estado ahora |
| `GET /salud` | Estado en vivo (JVM, base de datos, pool, errores, Flow, S3, correo) y lista de problemas con código estable |
| `GET /respaldos` | Lista `respaldos-bd/` en S3 y avisa si el último tiene más de 36 h |
| `GET /errores?nivel=` | Últimos 300 avisos/errores del servidor (en memoria; se pierden al reiniciar) |

## 3. Bloqueo manual
- Se guarda aparte del estado de Flow (columnas nuevas `bloqueo_manual`, `bloqueo_motivo`, `bloqueo_desde`; se crean solas al desplegar).
  La conciliación con Flow **no lo revierte**: solo lo quitas tú con "Reactivar".
- El cliente ve su cuenta como suspendida (solo lectura, con mensaje de contacto). Puede consultar, no vender ni modificar.
- Cada acción queda en la auditoría de esa empresa como "KIPIPOS (dueño)".

## 4. Respaldos en el panel
El usuario de S3 que usa el backend necesita además el permiso **`s3:ListBucket`** sobre el bucket (hoy tiene lectura/escritura de objetos).
Si falta, el panel muestra el aviso "falta el permiso s3:ListBucket" en la pestaña Respaldos.

## 5. Archivos
- `security/OwnerSecurityConfig.java`, `security/OwnerTokenFilter.java` (cadena de seguridad propia, antes de la general)
- `controller/OwnerController.java`, `service/OwnerPanelService.java`, `service/ErroresRecientes.java`
- Cambios: `Empresa` (bloqueo manual), `SuscripcionService.calcular`, `ComprobanteService.listar`, `JwtAuthFilter`, `application.properties`

## 6. Estado en vivo y avisos
La app consulta `/salud` cada 20 s (desde su proceso principal, aunque la ventana esté cerrada) y avisa con una notificación de escritorio
cuando aparece un problema, cuando persiste un problema crítico (recordatorio cada 30 min) y cuando se resuelve.

| Código | Nivel | Cuándo |
|---|---|---|
| `SERVIDOR_CAIDO` | Crítico | 2 consultas seguidas sin respuesta (lo detecta la app) |
| `BD_CAIDA` / `BD_LENTA` | Crítico / Aviso | `select 1` falla / tarda ≥ 1,5 s |
| `POOL_SATURADO` | Aviso | consultas esperando conexión a la base |
| `MEMORIA_ALTA` / `CPU_ALTA` | Aviso | ≥ 85 % de heap / ≥ 90 % de CPU |
| `ERRORES_PICO` | Aviso | ≥ 5 errores en 5 min |
| `FLOW_FALLA` | Crítico | no responde `plans/get` (revisión cada 5 min en segundo plano) |
| `S3_FALLA` / `RESPALDO_ATRASADO` | Aviso / Crítico | S3 inaccesible / último respaldo con más de 36 h |
| `RESPUESTA_LENTA` | Aviso | la API tarda ≥ 4 s (lo mide la app) |

Límites: solo avisa mientras la app esté corriendo en tu PC (apagado el PC, no hay avisos). Los umbrales están como constantes en
`SaludServidorService.java`. Nota: al consultar cada 20 s, un servidor de Render con "sleep por inactividad" no llega a dormirse.
