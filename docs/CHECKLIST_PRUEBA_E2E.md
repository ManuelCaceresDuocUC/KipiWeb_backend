# Checklist de prueba completa (antes del primer cliente)

Haz el recorrido en una ventana de incógnito, con una empresa de prueba. Marca cada paso. Si algo falla, anota el mensaje exacto y la hora.
Las pruebas de fechas (fin de prueba, mora, baja) se hacen cambiando fechas en la base de la empresa de prueba: ver `docs/ETAPA_11_PRUEBA_GRATUITA.md`.

## A. Registro (prueba gratuita sin tarjeta)
- [ ] Desde la portada, "Crear cuenta" abre el registro sin pantalla en blanco.
- [ ] La portada, /planes y el registro dicen "30 días gratis, sin tarjeta" y el precio ($25.000/mes) coincide con el plan de Flow.
- [ ] El registro exige aceptar términos y política (y falla si no se aceptan).
- [ ] Llega el código de verificación al correo y se acepta; un código incorrecto 5 veces lo bloquea.
- [ ] Turnstile aparece y el formulario no se envía sin resolverlo.
- [ ] Al terminar, NO se redirige a Flow: aparece "Tu cuenta está lista" y se puede iniciar sesión de inmediato.
- [ ] La empresa queda en estado PRUEBA con `prueba_hasta` a 30 días.
- [ ] Un RUT o correo ya registrado muestra un mensaje claro, no un error genérico.

## B. Primer ingreso
- [ ] Inicio de sesión con el administrador creado.
- [ ] Contraseña olvidada: llega el enlace, vence a los 30 min y sirve una sola vez.
- [ ] Tras restablecer la contraseña, una sesión abierta en otro navegador se cierra (mensaje "Tu sesión se cerró...").

## C. Equipo
- [ ] Crear un vendedor con sucursal y sin correo; iniciar sesión con él.
- [ ] Como administrador, cambiar su rol/sucursal: el vendedor es expulsado y al volver entra con lo nuevo.
- [ ] Desactivar al vendedor: pierde el acceso en menos de un minuto; el login muestra "desactivado".
- [ ] No se puede desactivar ni cambiar el rol del último administrador activo ni el propio.
- [ ] Cada acción anterior aparece en Administración > Registro de actividad.

## D. Facturación electrónica (SimpleFactura u otro)
- [ ] Probar conexión OK con las credenciales del proveedor.
- [ ] Venta en efectivo: sale la boleta con folio y PDF 80 mm, y se imprime.
- [ ] Venta con tarjeta: respeta la regla configurada (no emite si está desactivada).
- [ ] Anular la venta: se emite y se imprime la nota de crédito; "Reimprimir nota de crédito" funciona.
- [ ] Con credenciales incorrectas la venta igual se guarda y queda para reintentar en Movimientos.
- [ ] Reintentar desde Movimientos emite la boleta pendiente sin duplicarla.
- [ ] Con OpenFactura sin Acteco, la configuración muestra el aviso amarillo.

## E. Operación de caja e inventario
- [ ] Abrir caja, vender, cerrar caja: la cuadratura coincide.
- [ ] El stock baja con la venta y vuelve al anularla.
- [ ] Venta a crédito (fiado): sube la deuda del cliente; anular la revierte.

## F. Prueba gratuita: avisos, vencimiento y activación
- [ ] En PRUEBA con más de 10 días restantes: no aparece aviso, pero Administración > "Tu suscripción" muestra los días que quedan.
- [ ] Con `prueba_hasta` a 9 días: aparece el aviso azul con el botón "Activar suscripción" y llega el correo del primer aviso (una sola vez).
- [ ] Con `prueba_hasta` a 2 días: llega el segundo aviso (una sola vez).
- [ ] Con `prueba_hasta` vencida: aparece el aviso rojo; se puede consultar todo, pero vender o guardar cambios responde 402 con mensaje claro; se puede cerrar la caja abierta; llega el tercer aviso.
- [ ] Un vendedor (no administrador) con la prueba vencida ve "Pídele a tu administrador que active la suscripción".
- [ ] "Activar suscripción" lleva a Flow; se inscribe la tarjeta de prueba y se vuelve a la cuenta (no al login) con la suscripción ACTIVA y el acceso completo.
- [ ] Activar con días de prueba restantes pide confirmación y avisa que el cobro parte hoy.
- [ ] En Flow, la suscripción creada NO tiene días de prueba y el primer cobro se genera al activar.
- [ ] Cerrar la pestaña de Flow después de inscribir la tarjeta: volver a Administración y pulsar "Revisar ahora" activa la cuenta al instante. Si no se pulsa, el rescate automático la activa en la siguiente conciliación (hasta 6 h).

## G. Baja de la cuenta (usar SOLO una empresa de prueba)
- [ ] En PRUEBA, "Dar de baja mi cuenta" exige escribir el RUT; con un RUT distinto rechaza.
- [ ] Al confirmar: se cierra la sesión, llega el correo con la fecha de eliminación, y el login responde "cuenta dada de baja".
- [ ] Una empresa con suscripción ACTIVA no puede darse de baja desde el portal (indica escribir a soporte).
- [ ] Para probar el borrado: poner `baja_solicitada_en` 31 días atrás; tras el siguiente ciclo del proceso la empresa y todos sus datos desaparecen (ventas, productos, clientes, usuarios, sucursales), sin error en los logs.

## H. Suscripción de pago: mora
- [ ] Simular mora con `mora_desde` (no con `estado`): dentro de los 7 días de gracia hay aviso ámbar y se puede vender; pasada la gracia queda en solo lectura.
- [ ] Al regularizar el pago vuelve el acceso completo (puede tardar hasta la próxima conciliación de 6 h; "Ya pagué" lo acelera).

## I. Público y legal
- [ ] Footer: Términos, Privacidad y Contacto abren sin recargar.
- [ ] El formulario de contacto envía correo al consultante y al correo de la aplicación.
- [ ] Datos de la SpA completos en los términos y la política (sin textos [COMPLETAR]).
- [ ] Los Términos describen la prueba de 30 días sin tarjeta, el solo lectura al vencer y la baja con eliminación.
- [ ] `LEGAL_TERMINOS_VERSION` en Render coincide con `LEGAL.version` del frontend (si no, el registro se rechaza).

## J. Móvil y navegadores
- [ ] Portada, registro y POS se ven bien en celular (ancho 360–390 px).
- [ ] Probado en Chrome y en un segundo navegador.
