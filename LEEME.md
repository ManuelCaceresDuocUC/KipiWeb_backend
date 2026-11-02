# Etapa 9b – Backend
- La anulación devuelve el PDF de la nota de crédito (POST /ventas/{id}/anular y /nota-credito/reintentar).
- Nuevo GET /ventas/{id}/nota-credito (solo ADMIN): copia guardada del PDF.
- SimpleFacturaProveedor: incluye el detalle ("errors") en los rechazos. Si ya aplicaste el ZIP anterior de etapa 9, estos archivos lo reemplazan.
No compilado con Maven.
