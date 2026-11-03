# Prueba: ventas sin conexión (Paso 2)

Requisito: probar con la versión desplegada o con `npm run build` + `npm run preview` (el service worker no corre en `npm run dev`).

1. Inicia sesión con un vendedor, abre la caja y deja cargar la pantalla de ventas (se guarda el catálogo).
2. DevTools → Network → "Offline". Aparece la barra roja.
3. Vende en efectivo: el modal de cobro muestra "Guardar y enviar después". Se imprime el comprobante interno y aparece la barra amarilla "1 venta guardada…".
4. Repite con tarjeta (confirma la máquina) y con transferencia. Con fiado, el modal NO ofrece guardar.
5. Recarga la página (F5) estando offline: la caja y la cola siguen ahí.
6. Intenta "Cierre de turno": debe avisar que hay ventas sin enviar.
7. Vuelve a "Online": en segundos las ventas se envían solas, el catálogo se actualiza y en Movimientos aparecen con su boleta.
8. En Administración → Registro de actividad aparece `VENTA_DIFERIDA` por cada una (con diferencias de precio si las hubo).

Casos de borde a probar:
- Cambia un precio en Inventario mientras el equipo está offline y vende: la venta debe registrarse al precio con el que se cobró y quedar anotada la diferencia.
- Deja el stock en 0, vende offline y sincroniza: el stock queda negativo (la mercadería ya salió) y la venta se acepta.
- Cierra sesión con ventas pendientes: avisa. Inicia con OTRO usuario: la barra dice que son de otra persona y no las envía.
- Cierra la caja desde otro equipo/admin antes de sincronizar: la venta queda "rechazada" con el motivo; abre la caja y pulsa Reintentar.

## Paso 3: fiado con vale manual y comprobante por correo

Requisito: un cliente con correo, límite de crédito y la empresa con fiado habilitado. Variables de correo (MAIL_USERNAME…) configuradas.

1. Fiado normal (con conexión): al registrarlo, el cliente recibe un correo con el detalle, lo que debe en total y el vale PDF adjunto. Sin correo en la ficha del cliente, no se envía nada.
2. Opciones Operativas → "Imprimir vales de fiado (papel)": imprime N hojas numeradas (`FM-XX-0001`…), cada una con copia local y copia cliente. Funciona sin internet.
3. Con "Offline" activado: en el cobro ya no aparece Crédito/Fiado y la pantalla explica el procedimiento del vale en papel.
4. Con conexión de vuelta: Opciones Operativas → "Registrar fiado desde vale": N° del vale, fecha real, cliente, productos y precios. Al registrar, el cliente recibe el correo con la fecha real de la compra y el N° de vale.
5. Registrar dos veces el mismo N° de vale debe rechazarse ("ya está registrado"). Un vale que supera el cupo del cliente también se rechaza.
6. Administración → Registro de actividad: aparece `FIADO_MANUAL` con N° de vale, cliente, monto y fecha del vale.
7. El movimiento del cliente (estado de cuenta) dice "Venta N° X a crédito (vale manual FM-…)".
