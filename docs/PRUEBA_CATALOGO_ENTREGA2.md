# Entrega 2 · Catálogo (importar, códigos internos, etiquetas, exportar)

## Antes de compilar
- Frontend: `npm install jsbarcode` (única dependencia nueva; genera los códigos de barras de las etiquetas).
- Backend: sin columnas nuevas ni variables nuevas.

## Pruebas
1. **Importar** (Inventario → Importar Excel/CSV)
   - Descargar la plantilla, completarla con 3 productos (uno sin código, uno con coma decimal en stock, uno a granel).
   - Probar también un .xlsx con otra hoja y un CSV guardado desde Excel ("CSV (delimitado por comas)").
   - Pulsar "Revisar sin guardar": no debe aparecer nada nuevo en Inventario. Luego "Importar".
   - Repetir la importación: los productos deben quedar "actualizados", no duplicados.
   - Con "Reemplazar el stock" marcado, el stock cambia y en Movimientos aparece un AJUSTE.
2. **Códigos internos**
   - Crear un producto sin código: queda con un EAN-13 que empieza con 29.
   - "Asignar códigos": rellena los que no tienen código; con la casilla marcada también cambia los PROV-….
3. **Etiquetas**: elegir productos y copias, formato térmico o A4, Imprimir. Escanear una etiqueta en Ventas debe encontrar el producto.
4. **Exportar** (Administración → Exportar datos): descargar cada tipo, abrir en Excel (tildes y columnas bien). El CSV de productos se puede volver a importar.

## Límites conocidos
- Solo .xlsx/.csv (no .xls antiguo). Máx. 15 MB / 20.000 filas por archivo.
- Exportar ventas: máx. 1 año y 20.000 ventas por descarga.
- Las etiquetas se imprimen con el cuadro de impresión del navegador: en la térmica hay que elegir el tamaño de papel de la etiqueta y márgenes "ninguno".
