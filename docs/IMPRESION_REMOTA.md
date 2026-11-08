# Impresión desde el celular (caja de impresión)

El PC que tiene la impresora y KIPIPOS abierto actúa como **caja de impresión**. Un celular (u otro equipo sin impresora)
deja el ticket en una cola del servidor y el PC lo recoge cada ~4 s, lo imprime y abre la gaveta como siempre.

## Configurar
1. **PC de la impresora:** entra a KIPIPOS con tu usuario, Opciones Operativas > "Este equipo" > **ES la caja de impresión**.
   Aparece una etiqueta verde "Caja de impresión activa". Deja la ventana abierta (puede estar detrás de otras, no minimizada).
   Para que imprima sin diálogo, abre Chrome/Edge con `--kiosk-printing` (igual que hoy).
2. **Celular:** entra con un usuario de la **misma sucursal** > Opciones Operativas > "Este equipo" > **Manda a la caja de impresión**.
   "Comprobar caja de impresión" confirma si el PC está conectado.
3. La gaveta se configura en el PC de la caja (modo "Al imprimir" o "Programa local"). El celular no necesita nada.

## Cómo se comporta
- Venta desde el celular: el ticket sale en el PC. Si hay efectivo, siempre sale al menos la copia del local y la gaveta se abre.
- Ingresos, retiros y aperturas sin venta hechos desde el celular también salen (y abren) en el PC.
- Si el PC no está conectado, el celular avisa que el ticket NO se imprimió; se puede reimprimir desde Movimientos.
- Los trabajos que esperan más de 5 minutos se descartan (para que no salgan tickets viejos).
- Venta sin conexión a internet en el celular: no hay cómo avisar al PC; el comprobante interno se imprime en el propio celular.

## Seguridad y límites
- Solo viajan PDF y eventos de caja estructurados (nunca HTML): la caja de impresión no ejecuta contenido de otros equipos.
- La cola está en memoria del servidor (una sola instancia): si el servidor se reinicia, los trabajos en espera se pierden.
- "Reimprimir ticket" desde Movimientos imprime en el equipo donde se pulsa.
- Si dos PC se declaran caja de impresión en la misma sucursal, los trabajos los recoge quien consulte primero.
