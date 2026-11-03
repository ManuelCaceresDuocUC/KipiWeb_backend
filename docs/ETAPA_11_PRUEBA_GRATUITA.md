# Etapa 11: prueba gratuita de 30 días sin tarjeta, activación y baja

## Cómo funciona

1. **Registro:** la empresa nace en estado `PRUEBA` con `prueba_hasta` = registro + 30 días. No se toca Flow ni se pide tarjeta.
2. **Durante la prueba:** uso normal. Desde los 10 días finales aparece un aviso con el botón "Activar suscripción". En Administración > "Tu suscripción" siempre se ve cuánto queda.
3. **Avisos por correo** a los administradores (una vez cada uno): faltan ≤10 días, faltan ≤3 días y "la prueba terminó".
4. **Al vencer:** la cuenta queda en **solo lectura** (consulta todo; vender o modificar responde 402). No se borra nada ni se cobra.
5. **Activar:** el administrador pulsa "Activar suscripción", inscribe su tarjeta en Flow y, al volver, se crea la suscripción y la cuenta pasa a `ACTIVA`. **El cobro parte ese día.** Si activa antes de que termine la prueba, se le pide confirmar y no se acumulan los días restantes.
6. **Rescate:** si el cliente inscribe la tarjeta pero se corta el retorno (cierra la pestaña, error de red), tiene el enlace "Revisar ahora" en Administración > "Tu suscripción", que activa la cuenta al instante. Si no lo usa, el proceso de conciliación (cada 6 horas) crea la suscripción pasados 10 minutos desde que inició la inscripción.
7. **Baja (solo en prueba o con la prueba vencida):** Administración > "Dar de baja mi cuenta", escribiendo el RUT. La cuenta pasa a `BAJA` (nadie entra), se cancela el cobro en Flow si existiera y se manda un correo. A los **30 días** un proceso borra todos los datos de la empresa. Quien ya paga se da de baja escribiendo a soporte.

Estados de `empresas.estado`: `PRUEBA`, `ACTIVA`, `EN_MORA`, `SUSPENDIDA`, `BAJA` y el antiguo `PENDIENTE` (registros viejos que pagaban al registrarse). `PRUEBA_VENCIDA` no se guarda: se calcula por fecha.

## Qué debes configurar ANTES de desplegar

### 1. Flow: el plan no debe tener días de prueba
Si el plan "KIPI" de Flow tiene período de prueba, el cliente tendría 30 días gratis en KIPI y además esos días en Flow. Deja los días de prueba del plan en **0**. Si Flow no te deja editarlo, crea un plan nuevo con el mismo monto ($25.000 mensual, sin prueba) y pon su identificador en la variable `FLOW_PLAN_ID` de Render.

### 2. Variables de Render (todas opcionales; estos son los valores por defecto)
| Variable | Por defecto | Para qué |
|---|---|---|
| `LEGAL_TERMINOS_VERSION` | `2026-11-03` | **Debes ponerla igual a `LEGAL.version` del frontend**, o el registro se rechaza |
| `FLOW_PLAN_ID` | `KIPI` | Plan de Flow al activar (sin días de prueba) |
| `SUSCRIPCION_PRUEBA_DIAS` | `30` | Días de prueba. Si lo cambias, cambia también `DIAS_PRUEBA` en `config/planes.ts` |
| `SUSCRIPCION_BAJA_DIAS` | `30` | Días entre la baja y el borrado. Si lo cambias, cambia `diasEliminacionTrasBaja` en `config/legal.ts` |
| `SUSCRIPCION_BORRADO_HABILITADO` | `true` | `false` = nunca borra, solo acumula bajas |
| `SUSCRIPCION_AVISOS_HABILITADO` | `true` | `false` = no envía los correos de fin de prueba |

Los correos requieren `MAIL_USERNAME` y `MAIL_PASSWORD`; sin ellos no se envía nada (y los avisos se reintentan cuando haya correo).

### 3. Columnas nuevas
Se crean solas al arrancar (`ddl-auto=update`): `empresas.prueba_hasta`, `prueba_aviso`, `baja_solicitada_en`, `flow_inscripcion_en`.

### 4. Empresas que ya existen
Las que están `ACTIVA` o sin estado no cambian. Una empresa de prueba que quedó `PENDIENTE` (registro viejo sin pagar) sigue sin acceso: bórrala a mano o cámbiale el estado a `PRUEBA` y ponle `prueba_hasta`.

## Cómo probar con fechas (solo en una empresa de prueba)

Reemplaza `<ID>` por el id de la empresa. La caché dura hasta 60 segundos.

```sql
-- Faltan 9 días (primer aviso + aviso azul). Reinicia las marcas de aviso antes de cada prueba.
UPDATE empresas SET estado='PRUEBA', prueba_hasta = NOW() + INTERVAL 9 DAY, prueba_aviso = 0 WHERE id = <ID>;

-- Faltan 2 días (segundo aviso)
UPDATE empresas SET estado='PRUEBA', prueba_hasta = NOW() + INTERVAL 2 DAY, prueba_aviso = 0 WHERE id = <ID>;

-- Prueba vencida (solo lectura + tercer aviso)
UPDATE empresas SET estado='PRUEBA', prueba_hasta = NOW() - INTERVAL 1 DAY, prueba_aviso = 0 WHERE id = <ID>;

-- Borrado definitivo: la empresa debe estar en BAJA con la solicitud hace más de 30 días
UPDATE empresas SET baja_solicitada_en = NOW() - INTERVAL 31 DAY WHERE id = <ID> AND estado = 'BAJA';
```

Los correos y el borrado los hace un proceso que corre cada 6 horas (el primero, unos minutos después de arrancar el servidor). Para no esperar, reinicia el servicio en Render o define `SUSCRIPCION_AVISOS_INTERVALO=PT1M` y `SUSCRIPCION_BORRADO_INTERVALO=PT1M` durante la prueba (y quítalas después).

⚠ **El borrado es irreversible.** Pruébalo primero con una empresa de prueba que tenga datos (ventas, productos, clientes, un comprobante) y revisa los logs de Render: debe decir "eliminada definitivamente" y no mostrar errores. Hasta que lo hayas probado, puedes poner `SUSCRIPCION_BORRADO_HABILITADO=false`. Haz un respaldo de la base antes de la primera eliminación real.

## Qué NO cubre esta etapa
- Las cuentas con la prueba vencida que no activan ni se dan de baja quedan en solo lectura indefinidamente. Los Términos dicen que podrás darlas de baja tras avisar con 15 días; ese proceso no está automatizado.
- La baja por el portal solo existe para quien no paga. Quien ya tiene suscripción cancela por correo.
- No se prorratea: activar antes de tiempo no devuelve ni suma días de prueba.
