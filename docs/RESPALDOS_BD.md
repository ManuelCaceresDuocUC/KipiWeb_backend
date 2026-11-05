# Respaldos de la base de datos (copia propia)

Railway (plan Hobby) no permite crear respaldos nuevos, así que KIPIPOS hace su propia copia:
cada noche GitHub Actions se conecta a la base, genera una copia completa, la **cifra** y la sube a tu S3
(carpeta `respaldos-bd/`), fuera de Railway. Costo extra: $0 (solo el espacio en S3, que es mínimo).

## 1. Dejar la base accesible para el respaldo
En Railway → servicio MySQL → **Settings → Networking → TCP Proxy** (actívalo). Railway te da un host y un puerto
públicos (por ejemplo `xxxx.proxy.rlwy.net:12345`). Usuario, contraseña y nombre de la base están en la pestaña **Variables**
(`MYSQLUSER`, `MYSQLPASSWORD`, `MYSQLDATABASE`).
La contraseña de Railway es larga y aleatoria: no la cambies por una simple, porque ahora la base es alcanzable desde internet.

## 2. Crear los secretos en GitHub
Repositorio del backend → **Settings → Secrets and variables → Actions → New repository secret**:

| Secreto | Valor |
|---|---|
| `DB_HOST` | host del TCP Proxy (sin puerto) |
| `DB_PORT` | puerto del TCP Proxy |
| `DB_USER` | `MYSQLUSER` |
| `DB_PASSWORD` | `MYSQLPASSWORD` |
| `DB_NAME` | `MYSQLDATABASE` |
| `BACKUP_PASSPHRASE` | una frase larga que inventas tú (mínimo 20 caracteres) |
| `S3_BUCKET`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` | los mismos que ya usa el backend (o mejor, de un usuario aparte, ver §5) |
| `S3_ENDPOINT` | solo si tu S3 no es el de Amazon (si es AWS, no lo crees) |

**Guarda `BACKUP_PASSPHRASE` también en un gestor de contraseñas o en papel.** Sin ella los respaldos no se pueden abrir,
y GitHub no te la muestra de nuevo.

## 3. Primera prueba
Sube el archivo `.github/workflows/respaldo-bd.yml` al repositorio, entra a **Actions → Respaldo diario de la base de datos → Run workflow**.
Debe terminar en verde y mostrar "Tablas respaldadas: N" y la ruta en S3. Revisa que el archivo esté en el bucket.
Si algo falla, GitHub te envía un correo (por defecto, al dueño del repositorio).

## 4. Cuánto tiempo se guardan
Crea una regla de ciclo de vida en el bucket (S3 → Management → Lifecycle rules) para la carpeta `respaldos-bd/`:
borrar los archivos con más de **30 días** (o 60, si prefieres). Así no se acumulan.

## 5. Recomendaciones de seguridad
- Usa un usuario de S3 solo para respaldos, con permiso de escribir y leer únicamente en `respaldos-bd/*`.
- Los respaldos contienen datos de tus clientes (ventas, correos, deudas): por eso salen **cifrados**. No los guardes sin cifrar en otro lado.
- Si alguna vez cambias la contraseña de la base en Railway, actualiza el secreto `DB_PASSWORD`.

## 6. Cómo restaurar (pruébalo al menos una vez)
1. Baja el archivo `.sql.gz.enc` que quieras desde S3.
2. Descifra y descomprime (en tu PC con OpenSSL y el cliente de MySQL):
   ```bash
   export BACKUP_PASSPHRASE='tu frase'
   openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in kipipos-2026-11-05_0630.sql.gz.enc -pass env:BACKUP_PASSPHRASE | gunzip > restaurar.sql
   ```
3. **Restaura primero en una base nueva** (nunca encima de la de producción sin revisar):
   ```bash
   mysql --host=HOST --port=PUERTO --user=USUARIO -p -e "CREATE DATABASE kipipos_prueba CHARACTER SET utf8mb4"
   mysql --host=HOST --port=PUERTO --user=USUARIO -p kipipos_prueba < restaurar.sql
   ```
4. Revisa que estén las tablas y datos recientes (por ejemplo, la última venta). Si todo está bien, y solo si es necesario,
   apunta el backend a esa base (variable `SPRING_DATASOURCE_URL` en Render) o restaura sobre la original.

## 7. Qué NO cubre
- Los archivos subidos a S3 (comprobantes de transferencia, facturas de compras) no se respaldan aquí; viven en S3.
  Activa el **versionado** del bucket si quieres protegerlos de borrados accidentales.
- La pérdida de hasta un día de datos (la copia es nocturna). Para recuperación al minuto se necesita Railway Pro.
