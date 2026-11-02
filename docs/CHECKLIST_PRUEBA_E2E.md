# Checklist de prueba completa (antes del primer cliente)

Haz el recorrido en una ventana de incógnito, con una empresa de prueba. Marca cada paso. Si algo falla, anota el mensaje exacto y la hora.

## A. Registro y pago
- [ ] Desde la portada, "Crear cuenta" abre el registro sin pantalla en blanco.
- [ ] El precio mostrado ($25.000/mes) coincide con lo que cobra Flow.
- [ ] El registro exige aceptar términos y política (y falla si no se aceptan).
- [ ] Llega el código de verificación al correo y se acepta; un código incorrecto 5 veces lo bloquea.
- [ ] Turnstile aparece y el formulario no se envía sin resolverlo.
- [ ] El pago en Flow (tarjeta de prueba) termina en la pantalla de éxito y la cuenta queda activa.
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

## E. Operación de caja e inventario
- [ ] Abrir caja, vender, cerrar caja: la cuadratura coincide.
- [ ] El stock baja con la venta y vuelve al anularla.
- [ ] Venta a crédito (fiado): sube la deuda del cliente; anular la revierte.

## F. Suscripción
- [ ] Estado de la suscripción visible para el administrador.
- [ ] Simular mora (cambiar el estado en la base de la empresa de prueba): tras la gracia pasa a solo lectura; las ventas nuevas responden 402 con mensaje claro y se puede cerrar la caja abierta.
- [ ] Al regularizar el pago vuelve el acceso completo (puede tardar hasta la próxima conciliación de 6 h).

## G. Público y legal
- [ ] Footer: Términos, Privacidad y Contacto abren sin recargar.
- [ ] El formulario de contacto envía correo al consultante y al correo de la aplicación.
- [ ] Datos de la SpA completos en los términos y la política (sin textos [COMPLETAR]).

## H. Móvil y navegadores
- [ ] Portada, registro y POS se ven bien en celular (ancho 360–390 px).
- [ ] Probado en Chrome y en un segundo navegador.
