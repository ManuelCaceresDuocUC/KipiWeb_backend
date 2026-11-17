# Procedimiento de incidentes y vulneraciones de seguridad

Documento interno de KIPI (LOS CACERES SPA). Cumple con lo que exige la Ley 21.719 de datos personales y con lo prometido a los clientes en el Anexo de Encargo (`/encargo-datos`): **avisar al cliente sin dilaciones indebidas y a más tardar en 48 horas** desde que KIPI conoce la vulneración.

> Revisar este procedimiento con el abogado antes del 1 de diciembre de 2026 y cada vez que cambie la infraestructura.

## 1. Qué es un incidente
Cualquier hecho que comprometa la confidencialidad, integridad o disponibilidad de datos: acceso no autorizado a una cuenta o a la base, filtración de credenciales (JWT_SECRET, DTE_ENCRYPTION_KEY, OWNER_API_TOKEN, claves de AWS o de la base), pérdida o robo de respaldos, correo enviado a una persona equivocada, un equipo con sesión abierta perdido, o un error del sistema que muestre datos de una empresa a otra.

## 2. Quién decide
- **Responsable del incidente:** el dueño de KIPI (Manuel Cáceres). Decide la gravedad, aprueba el aviso a clientes y contacta al abogado.
- Si no está disponible, el aviso a clientes igual se envía dentro de las 48 horas; no se espera a tener toda la información.

## 3. Pasos y plazos
| Plazo desde que se detecta | Qué hacer |
|---|---|
| Inmediato | **Contener**: rotar claves o tokens comprometidos, cerrar sesiones (cambiar `JWT_SECRET` cierra todas), suspender el servicio si hace falta, revocar accesos de AWS/Railway/Render. Guardar evidencia (logs de Render, panel del dueño > Errores). |
| Hora 0 a 4 | **Registrar** el incidente en `docs/REGISTRO_INCIDENTES.md` (plantilla abajo) y evaluar alcance: qué empresas, qué datos, cuántas personas. |
| Hasta 48 h | **Avisar a cada empresa afectada** por correo al administrador (plantilla abajo). Es obligatorio aunque falten datos: se completa después. |
| Sin dilaciones | Si KIPI es responsable de los datos afectados (cuentas de clientes), evaluar aviso a la **Agencia de Protección de Datos Personales** y a las personas, en especial si hay datos sensibles, de menores de 14 años o de deudas y obligaciones comerciales (el fiado lo es). Consultar al abogado. |
| Hasta 15 días | Cerrar el incidente: causa raíz, corrección, mejoras, y completar el registro. |

Si algún cliente fuera un servicio esencial u operador de importancia vital (Ley 21.663), la alerta temprana al CSIRT es de 3 horas: preguntar al abogado antes de aceptar un cliente de ese tipo.

## 4. Plantilla de aviso a clientes (correo al administrador)
**Asunto:** Aviso de incidente de seguridad en KIPI

> Hola. Te escribimos para informarte de un incidente de seguridad que puede afectar datos de tu empresa en KIPI.
> - **Qué pasó y cuándo:** …
> - **Qué datos podrían estar afectados y de cuántas personas:** …
> - **Posibles consecuencias:** …
> - **Qué hicimos para contenerlo:** …
> - **Qué te recomendamos hacer:** … (por ejemplo, cambiar contraseñas; avisar a tus clientes si hay datos de deudas del fiado)
> - **Contacto:** kipisofts@gmail.com, +56 9 9381 5620
> Como responsable de esos datos, tú decides si debes avisar a la Agencia de Protección de Datos Personales y a las personas afectadas; te ayudaremos con la información que necesites.

## 5. Registro de incidentes
Todo incidente, aunque no haya afectado datos personales, se anota en `docs/REGISTRO_INCIDENTES.md` con: fecha de detección, quién lo detectó, descripción, empresas y datos afectados, causa, medidas, fecha de aviso a clientes, decisión sobre la Agencia, y fecha de cierre. El registro se conserva al menos 5 años.

## 6. Qué ayuda a detectar
Panel del dueño (estado del servidor, errores recientes, respaldos), registro de actividad de cada empresa, avisos del servidor (correo sin configurar, base lenta, etc.), y reportes de clientes por correo o WhatsApp.
