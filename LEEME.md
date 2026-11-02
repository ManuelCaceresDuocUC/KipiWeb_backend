# Arreglo: retorno de Flow (v2)
SecurityConfig.java: la excepcion de CORS para /api/auth/registro-exitoso ahora se elige por ruta exacta (la v1 no tenia efecto porque la regla general "/**" se evaluaba primero). Reemplaza a la v1. No compilado con Maven.
