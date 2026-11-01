# Etapa 5 – Backend: suscripción Flow, bloqueo por estado, RUT abandonado

Copia las carpetas sobre `kipiweb_backend` (mismas rutas). **Ojo: `application.properties` del ZIP es el completo de tu repo, con 4 líneas nuevas al final**; si tienes cambios locales, solo pega las líneas `suscripcion.*`.

## Qué hace
- **Conciliación con Flow** cada 6 h (y 3 min tras arrancar): consulta cada suscripción y actualiza estado (ACTIVA / EN_MORA / SUSPENDIDA).
- **Factura vencida** → EN_MORA, 7 días de gracia con uso normal + aviso; luego SUSPENDIDA = **solo lectura** (se puede entrar, ver y pagar; ventas/cambios responden 402). Cerrar un turno de caja abierto sigue permitido.
- **Cancelada en Flow** → acceso hasta el fin del período ya pagado.
- **Si Flow falla, nunca se cambia un estado.** Freno de seguridad: si ≥3 empresas (y >30 %) pasarían a suspendidas de golpe, no se aplica nada y queda un log ERROR.
- **Registro abandonado**: un RUT PENDIENTE sin pagar por más de 30 min se descarta y puede registrarse de nuevo (antes quedaba bloqueado para siempre).
- **Corrección**: el retorno de Flow ahora verifica que la empresa exista ANTES de crear la suscripción (evita cobrar sin cuenta).
- Endpoints: `GET /api/empresa/suscripcion` y `POST /api/empresa/suscripcion/sincronizar` ("Ya pagué", máx. 1 cada 30 s).

## Variables opcionales (Render)
`SUSCRIPCION_GRACIA_DIAS` (7), `SUSCRIPCION_SYNC_INTERVALO` (PT6H), `SUSCRIPCION_SYNC_INICIAL` (PT3M), `SUSCRIPCION_SYNC_HABILITADO` (true; ponla en false para apagar el job).

## Base de datos
`ddl-auto=update` crea solas las columnas nuevas en `empresas`. No hay SQL.

## ⚠ Sin compilar
No pude compilar con Maven en mi entorno (sin acceso a Maven Central); solo revisé sintaxis. Si el build en Render falla, pásame el log.

## Qué probar en sandbox de Flow
1. Registrar empresa, pagar → ACTIVA.  2. Cobro fallido → EN_MORA con banner; forzar `mora_desde` a hace 8 días → solo lectura (402 al vender).  3. Pagar y "Ya pagué" → vuelve a normal.  4. Cancelar en Flow → sigue hasta `acceso_hasta`.  5. Registrar un RUT, no pagar, esperar 30 min (o bajar `fecha_registro` en la BD) y registrar de nuevo.

## Límites conocidos
- Flow no documenta el payload del callback del plan, por eso no hay webhook: se concilia por consulta (6 h + botón).
- Estado 3 de suscripción y respuesta de `customer/getSubscriptions` no están documentados; el código es defensivo y ante duda no cambia nada.
- Reactivar una suscripción cancelada del todo requiere inscribir tarjeta de nuevo (no incluido).
