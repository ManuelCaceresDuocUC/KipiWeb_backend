# Medidas de seguridad y privacidad de KIPI (evidencia)

Documento interno. La Ley 21.719 pide poder **demostrar** que las medidas existen y funcionan. Esta página las lista con dónde verificarlas. Actualizar cuando cambie algo.

## Datos y accesos
| Medida | Detalle | Dónde verificar |
|---|---|---|
| Aislamiento por empresa | Todo endpoint toma la empresa del token, nunca del cliente | `controller/*`, pruebas de `CHECKLIST_PRUEBA_E2E.md` |
| Roles | ADMIN y vendedor; acciones sensibles solo ADMIN | `security/SecurityConfig.java` |
| Contraseñas | Hash BCrypt | `security/PasswordConfig.java` |
| Sesión | JWT con vencimiento (`JWT_EXPIRATION_HOURS`, 10 h por defecto); un admin puede cerrar sesiones de un usuario | `service/JwtService.java` |
| Límites de intentos | Login, registro, recuperación de clave, contacto, panel del dueño | `service/LimiteSolicitudes` y sus usos |
| Verificación de correo | Código en el registro | `VerificacionCorreoService` |
| Registro de actividad | Acciones sensibles con usuario, fecha e IP; el administrador lo consulta | `service/AuditoriaService.java` |
| Credenciales de facturación | AES-256-GCM, clave maestra solo en `DTE_ENCRYPTION_KEY`; no se devuelven a la pantalla | `util/CifradoClaves.java`, `model/ClaveCifradaConverter.java` |
| Tarjetas | KIPI nunca ve ni guarda la tarjeta: se inscribe en Flow | `FlowClient`, política de privacidad |

## Infraestructura
| Medida | Detalle | Dónde verificar |
|---|---|---|
| Cifrado en tránsito | HTTPS en Vercel y Render | Navegador |
| Respaldos | Diarios, comprimidos y cifrados (AES-256) a S3; alerta si el último tiene más de 36 h | `.github/workflows/respaldo-bd.yml`, `docs/RESPALDOS_BD.md`, panel del dueño |
| Retención de respaldos | Regla de ciclo de vida en S3 que borra `respaldos-bd/` a los 30 días (debe estar configurada) | Consola de AWS S3 > Management |
| Monitoreo | Panel del dueño: memoria, CPU, base de datos, Flow, S3, errores, alertas | `docs/PANEL_DUENO.md` |
| Panel del dueño web | Token del backend solo en el servidor del panel; contraseña propia; sesión firmada `httpOnly` | repositorio `panel_kipiweb` |
| Acceso a infraestructura | Cuentas con contraseña única y segundo factor en GitHub, Render, Railway, Vercel, AWS y Flow | **Verificar y anotar fecha** |

## Derechos y datos de personas
- Exportar y eliminar datos de un cliente final: ver `DERECHOS_TITULARES.md`.
- Incidentes: ver `PROCEDIMIENTO_BRECHAS.md` y `REGISTRO_INCIDENTES.md`.
- Subencargados y países: `src/config/subencargados.ts` del frontend (se muestran en la política y en el Anexo).

## Pendientes conocidos (anotar fecha al cerrarlos)
- [ ] Activar la regla de ciclo de vida de 30 días en `respaldos-bd/`.
- [ ] Probar una restauración de respaldo y anotar la fecha.
- [ ] Confirmar segundo factor en todas las cuentas de infraestructura.
- [ ] Revisión por abogado de Términos, Política y Anexo de Encargo.
- [ ] Confirmar con el abogado el mecanismo de transferencias internacionales.
