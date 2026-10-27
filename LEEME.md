# KIPIPOS · Etapa 1 · Backend (multi-tenant y seguridad)

> **Importante: este código NO fue compilado.** En la sesión donde lo preparé no había acceso a Maven Central,
> así que solo pude revisar sintaxis y referencias a mano. Antes de desplegar, compila en tu equipo:
> `./mvnw -DskipTests compile` (si algo no compila, mándame el error y lo corrijo).

## Cómo aplicarlo
1. Copia el contenido de la carpeta `src/`, `pom.xml`, `Dockerfile` de este ZIP sobre tu repo (mantiene las rutas), **o** usa `git apply etapa1.patch`.
2. Borra lo que dice `ELIMINAR.txt`.

## Orden de despliegue (haz esto en este orden)
1. **Seguridad urgente (independiente del código):** el repo era público y contenía la clave JWT y una contraseña de base de datos en `deploy-backend.yml`. Aunque se borren, quedan en el historial de git.
   - Cambia la contraseña de la base de datos (Railway) y actualiza `SPRING_DATASOURCE_PASSWORD` en Render.
   - Pon el repositorio en **privado**.
2. **Render → Environment**, agrega/revisa:
   | Variable | Valor |
   |---|---|
   | `JWT_SECRET` | **Obligatoria.** Texto aleatorio de 32+ caracteres (`openssl rand -base64 48`). Sin ella el servidor no arranca. |
   | `CORS_EXTRA_ORIGINS` | Dominios extra del frontend separados por coma (p. ej. la URL `*.vercel.app`). `www`/sin `www` de `APP_FRONTEND_URL` ya se aceptan solos. |
   | `APP_FRONTEND_URL` / `APP_BACKEND_URL` | `https://www.kipipos.cl` / URL de Render |
   | `APP_TIMEZONE` | Opcional (por defecto `America/Santiago`) |
   | `JWT_EXPIRATION_HOURS` | Opcional (por defecto 10) |
   | `FLOW_BASE_URL` | Opcional (por defecto sandbox; en producción `https://www.flow.cl/api`) |
3. Ejecuta `sql/01_indice_codigo_barras.sql` (código de barras único por empresa) y revisa `sql/02_revisar_datos.sql`.
4. Despliega **backend y frontend juntos**: cambió la respuesta del login y los tokens antiguos dejan de servir (todos deberán iniciar sesión de nuevo, una sola vez).

## Qué cambió
- **Identidad solo desde el token (JWT):** empresa, usuario y sucursal ya no se leen de parámetros ni del cuerpo. Se acabó el `empresaId=1` por defecto y el acceso a datos de otras empresas cambiando un número.
- **Roles** (`admin` / `vendedor`; "administrador" se normaliza a `admin`):
  - Solo **admin**: `/api/admin/**` (métricas), `/api/dte/**`, historial de cajas, listado de usuarios, reiniciar créditos, y crear/editar/borrar productos, categorías y recetas.
  - **Vendedor**: vender, su propia caja, clientes, ver catálogo, notas, y ver solo sus ventas en Movimientos.
- **Ventas:** el total lo calcula el servidor con los precios de la base. Si difiere del que ve el cajero → error 409 "el total cambió". Se valida que los productos sean de la empresa, estén activos y no sean insumos; la caja debe estar abierta *antes* de tocar stock.
- **Caja:** el cierre calcula el total esperado y la diferencia en el servidor (el cajero solo declara el efectivo contado). Abonos en efectivo suman al cajón; abonos con tarjeta no. Retiros no pueden superar el efectivo en caja.
- **Clientes:** se crean siempre con la empresa del token (corrige el bug de clientes sin empresa); RUT único por empresa.
- **Datos sensibles:** la API Key DTE nunca vuelve al navegador (solo "configurada" + últimos 4); hashes de contraseñas y credenciales de Flow/Haulmer no se serializan.
- **Login:** límite de intentos (8 fallos / 15 min por usuario+IP, en memoria), contraseñas antiguas en texto plano se migran a BCrypt al primer login.
- **Registro:** valida RUT (módulo 11), contraseña ≥ 8, usuarios únicos, y todo *antes* de crear nada en Flow. El retorno de Flow ya no crea una segunda suscripción si se recarga la página.
- **Zona horaria:** la app corre en `America/Santiago` (antes UTC en Render → cierres de día corridos). Ver `sql/03_zona_horaria_OPCIONAL.sql` para datos históricos.
- **Limpieza:** se quitaron jars locales, logs, workflow viejo con secretos, y código muerto (ver `ELIMINAR.txt`).

## Conocido / queda para las siguientes etapas
- Stock sigue siendo global por producto (sin sucursal, sin permitir/impedir negativo por empresa, sin historial): **Etapa 2**.
- Fiado con límite por cliente aplicado en la venta, transferencia con evidencia, pago mixto, anulaciones, impresión 80 mm y correo: **Etapa 3**.
- Si una venta se registra pero falla la emisión del DTE, el mensaje ahora lo dice claro ("no repitas el cobro"), pero el diseño robusto (reintento/estado) llega en la **Etapa 4**.
- Si alguien abandona el pago en Flow, su RUT queda "pendiente" y no puede registrarse de nuevo sin ayuda; se resuelve en la **Etapa 5** junto con la reconciliación de suscripciones.
- "Probar conexión" del DTE todavía solo valida que haya una llave y lo dice honestamente; la prueba real contra el proveedor llega en la Etapa 4.
- El motivo de los movimientos de caja (ingreso/retiro) aún no se guarda en base de datos.
