# Operación: respaldos y monitoreo

## 1. Monitoreo de disponibilidad (gratis)
El backend expone `GET /api/auth/salud`: responde 200 `{"estado":"ok"}` si el servidor y la base de datos responden, y 503 si la base no está disponible.
1. Crea una cuenta en un monitor externo (por ejemplo UptimeRobot o BetterStack, ambos con plan gratuito).
2. Agrega un monitor HTTP a `https://TU-BACKEND.onrender.com/api/auth/salud` cada 5 minutos.
3. Activa el aviso por correo (y si quieres por WhatsApp/Telegram).
Nota: en el plan gratuito de Render el servicio se duerme tras un rato sin tráfico; un monitor cada 5 minutos lo mantiene despierto, pero para clientes que pagan conviene un plan de Render que no se duerma.

## 2. Respaldos de la base de datos (Railway MySQL)
Los datos de tus clientes están solo en esa base: sin respaldo, un error o un borrado accidental es irrecuperable.
1. Revisa en Railway si tu plan incluye respaldos automáticos del volumen de la base y actívalos.
2. Además, guarda un respaldo propio. Desde tu computador con el cliente de MySQL instalado y los datos de conexión públicos de Railway (Variables del servicio MySQL):
```powershell
mysqldump --host=HOST --port=PUERTO --user=USUARIO --password --single-transaction --routines railway > respaldo-$(Get-Date -Format yyyy-MM-dd).sql
```
3. Guarda el archivo fuera del computador (Drive cifrado o un disco externo). Contiene datos de clientes: no lo subas a un repositorio.
4. Haz una prueba de restauración en una base vacía al menos una vez: un respaldo que nunca se restauró no es una garantía.
Frecuencia sugerida: diario mientras haya movimiento; semanal como mínimo.

## 3. Qué guardar aparte (no está en la base)
- Variables de entorno de Render y Vercel (JWT_SECRET, DTE_ENCRYPTION_KEY, claves de Flow, TURNSTILE_SECRET, correo). Guárdalas en un gestor de contraseñas.
- DTE_ENCRYPTION_KEY es crítica: si se pierde, las API Keys de facturación guardadas dejan de poder leerse y cada cliente debe ingresarlas de nuevo.

## 4. Si algo falla
- Mira primero el monitor y los logs de Render (pestaña Logs).
- Revisa el Registro de actividad (Administración) para saber quién cambió qué.
- Los documentos con error de facturación quedan en estado PENDIENTE/REVISAR: se reintentan desde Movimientos, nunca se pierde la venta.
