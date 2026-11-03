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
