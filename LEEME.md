# Etapa 9 – Backend: proveedor SimpleFactura (BETA)
Copia los archivos respetando las rutas. Sin SQL ni variables nuevas. El proveedor se registra solo (@Component).
- API Key = correo del usuario de SimpleFactura; API Secret = su contraseña (se guardan cifradas).
- Usa razón social, giro, dirección y comuna del registro de la empresa (si falta alguno, la boleta queda PENDIENTE con un mensaje claro).
- Construido desde el código del SDK oficial PHP de SimpleFactura; el SDK NO trae ejemplo de boleta (tipo 39): se asumió /invoiceV2 con IndMntNeto=2. Sin probar contra su API ni compilado con Maven.
- Sucursal fija "Casa Matriz"; nota de crédito con motivo "Otros".
- Cambiar de proveedor en la configuración exige ingresar las credenciales nuevamente.
Primera prueba: cuenta DEMO (demo.simplefactura.cl; credenciales públicas en documentacion.simplefactura.cl) en ambiente sandbox.
