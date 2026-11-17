# Cómo atender los derechos de las personas (Ley 21.719)

Documento interno. KIPI es **encargado** de los datos de los clientes finales de cada comercio y **responsable** de los datos de las cuentas de sus clientes.

**Plazos:** respuesta en **30 días corridos** (prorrogables una vez por otros 30 si es complejo); bloqueo temporal en **2 días hábiles**. Rectificación, supresión y oposición son gratuitas; el acceso, gratis al menos una vez por trimestre.

## A. Una persona escribe a KIPI sobre datos que tiene un comercio (cliente final del fiado)
1. Anotar la fecha de recepción y pedir que acredite su identidad.
2. **No responder la solicitud directamente**: reenviarla el mismo día al administrador del comercio (el responsable) y avisar a la persona que lo hicimos.
3. El comercio la atiende desde KIPI (ver C). Si el comercio no responde, ayudarlo; el plazo corre desde la recepción.

## B. Una persona escribe a KIPI sobre sus propios datos de cuenta (administrador o usuario de un cliente)
- **Acceso / portabilidad:** entregar un archivo con sus datos de cuenta (nombre de usuario, correo, rol, sucursal, fechas de aceptación de términos y registro de actividad que le corresponda).
- **Rectificación:** el administrador de la empresa puede corregir los datos de los usuarios; si no puede, hacerlo a petición.
- **Supresión:** si deja de ser usuario, se desactiva y se anonimiza su nombre y correo; no se borra lo que hay que conservar por ley (cobros, aceptación de términos). Si es el único administrador, la cuenta de la empresa debe darse de baja o traspasarse.
- **Oposición y bloqueo temporal:** desactivar el usuario o el envío de correos mientras se resuelve.
- Responder en 30 días y dejar constancia (fecha de solicitud, de respuesta y qué se hizo).

## C. Qué puede hacer el comercio desde KIPI con un cliente final
Pantalla **Estado de cuenta del cliente** (solo administradores):
- **Descargar sus datos:** genera un archivo JSON con nombre, RUT, teléfono, correo, límite, deuda y todos sus movimientos. Se registra en la auditoría como `CLIENTE_EXPORTADO`. Los documentos tributarios están en el proveedor de facturación del comercio.
- **Eliminar sus datos personales:** borra nombre, RUT, teléfono y correo, y deja el cliente como "Cliente eliminado N° …". Las ventas y movimientos se conservan sin identidad. No se permite si tiene deuda pendiente: primero hay que cobrarla o ajustarla. Se registra como `CLIENTE_ANONIMIZADO`.
- **Rectificar:** editar teléfono y correo (PATCH `/api/clientes/{id}`); el nombre y el RUT se corrigen contactando a soporte.
- **Bloqueo / oposición:** quitar el correo del cliente evita los comprobantes por correo.

## D. Lo que hay que conservar aunque pidan borrar
Documentos tributarios emitidos (obligación tributaria) y registros de cobro de KIPI. Informar a la persona qué se conserva y por qué.

## E. Registro
Anotar cada solicitud (fecha, quién, qué derecho, respuesta, fecha de respuesta) en una planilla interna. Si la persona reclama ante la Agencia, esa constancia es la defensa.
