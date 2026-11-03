package com.posbarlacteo.PosBarLacteo.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.util.RutUtils;

/**
 * Baja de la cuenta pedida por el propio cliente DURANTE O AL TERMINAR LA PRUEBA (sin suscripción de pago).
 * Quien ya paga cancela escribiendo a soporte: así no se pierde por accidente un período pagado ni se borran datos de un
 * cliente de pago con un clic. Se hace en dos tiempos:
 *  1) solicitarBaja: la cuenta pasa a BAJA (sin acceso), se cancela la suscripción en Flow y se avisa por correo.
 *     Durante el plazo de gracia (suscripcion.baja-dias, 30 por defecto) se puede revertir a mano si fue un error.
 *  2) eliminarDefinitivo: vencido el plazo, un proceso programado borra TODOS los datos de la empresa.
 */
@Service
public class BajaCuentaService {

    private static final Logger log = LoggerFactory.getLogger(BajaCuentaService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmpresaRepository empresaRepository;
    private final SuscripcionService suscripcionService;
    private final AuditoriaService auditoria;
    private final MailService mail;
    private final CorreosEmpresa correos;
    private final ComprobanteService comprobantes;
    private final FlowClient flow;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final long bajaDias;

    public BajaCuentaService(EmpresaRepository empresaRepository, SuscripcionService suscripcionService,
                             AuditoriaService auditoria, MailService mail, CorreosEmpresa correos,
                             ComprobanteService comprobantes, FlowClient flow, JdbcTemplate jdbc, TransactionTemplate tx,
                             @Value("${suscripcion.baja-dias:30}") long bajaDias) {
        this.empresaRepository = empresaRepository;
        this.suscripcionService = suscripcionService;
        this.auditoria = auditoria;
        this.mail = mail;
        this.correos = correos;
        this.comprobantes = comprobantes;
        this.flow = flow;
        this.jdbc = jdbc;
        this.tx = tx;
        this.bajaDias = bajaDias;
    }

    public long bajaDias() {
        return bajaDias;
    }

    // ------------------------------------------------------------------------------------------
    // 1) Solicitud de baja
    // ------------------------------------------------------------------------------------------

    /** Da de baja la cuenta. Exige escribir el RUT de la empresa. Devuelve la fecha en que se borrarán los datos. */
    public LocalDateTime solicitarBaja(AuthPrincipal p, String rutConfirmacion) {
        Empresa e = empresaRepository.findById(p.empresaId()).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));

        if (rutConfirmacion == null || !RutUtils.esValido(rutConfirmacion)
                || !RutUtils.normalizar(rutConfirmacion).equals(e.getRutEmpresa())) {
            throw new NegocioException("El RUT ingresado no coincide con el de tu empresa.");
        }
        if (SuscripcionService.BAJA.equals(e.getEstado())) {
            throw new NegocioException("Tu cuenta ya está dada de baja.");
        }
        if (!SuscripcionService.PRUEBA.equals(e.getEstado())) {
            throw new NegocioException("Para cancelar una suscripción activa y cerrar tu cuenta, escríbenos a kipisofts@gmail.com y lo hacemos contigo.");
        }

        // Primero Flow: si no se puede cancelar el cobro, NO se da de baja (seguiría cobrándose una cuenta cerrada).
        suscripcionService.cancelarSuscripcionFlow(e);

        e.setEstado(SuscripcionService.BAJA);
        e.setActivo(false);
        e.setBajaSolicitadaEn(LocalDateTime.now());
        empresaRepository.save(e);
        suscripcionService.invalidar(e.getId());

        LocalDateTime borrado = e.getBajaSolicitadaEn().plusDays(bajaDias);
        auditoria.registrar(p, "CUENTA_BAJA", "Dio de baja la cuenta. Eliminación definitiva el " + borrado.format(FECHA));
        avisarBaja(e, borrado);
        log.warn("Empresa {} ({}) dada de baja por {}. Eliminación definitiva: {}", e.getId(), e.getRutEmpresa(), p.usuario(), borrado);
        return borrado;
    }

    private void avisarBaja(Empresa e, LocalDateTime borrado) {
        if (!mail.habilitado()) {
            return;
        }
        String texto = "Hola,\n\nRecibimos la baja de la cuenta de " + e.getRazonSocial() + " en KIPI. "
                + "Ya no tiene acceso y no se te cobrará nada más.\n\n"
                + "Tus datos se eliminarán definitivamente el " + borrado.format(FECHA) + ". "
                + "Si fue un error, escríbenos antes de esa fecha respondiendo este correo y la reactivamos.\n\n"
                + "Gracias por haber probado KIPI.";
        for (String destino : correos.administradores(e.getId())) {
            try {
                mail.enviarTexto(destino, "Tu cuenta de KIPI fue dada de baja", texto, null);
            } catch (RuntimeException ex) {
                log.warn("No se pudo avisar la baja a {}: {}", destino, ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // 2) Eliminación definitiva (la ejecuta BajaCuentaJob pasado el plazo)
    // ------------------------------------------------------------------------------------------

    /** Empresas en BAJA cuyo plazo de gracia ya terminó. */
    public List<Empresa> pendientesDeEliminar() {
        LocalDateTime limite = LocalDateTime.now().minusDays(bajaDias);
        return empresaRepository.findByEstado(SuscripcionService.BAJA).stream()
                .filter(e -> e.getBajaSolicitadaEn() != null && e.getBajaSolicitadaEn().isBefore(limite))
                .toList();
    }

    /**
     * Borra todos los datos de la empresa en UNA transacción (o se borra todo o no se borra nada) y luego, ya
     * confirmada la base, los archivos de S3 y el cliente de Flow (mejor esfuerzo). Solo actúa sobre empresas en BAJA.
     * El orden respeta las claves foráneas: primero lo que depende de otras tablas, al final la empresa.
     */
    public void eliminarDefinitivo(Long empresaId) {
        Empresa e = empresaRepository.findById(empresaId).orElse(null);
        if (e == null || !SuscripcionService.BAJA.equals(e.getEstado())) {
            return; // red de seguridad: jamás se borra algo que no esté en BAJA
        }
        List<String> clavesS3 = jdbc.queryForList(
                "SELECT p.comprobante_key FROM pos_ventas_pagos p JOIN pos_ventas v ON v.id = p.venta_id "
                        + "WHERE v.empresa_id = ? AND p.comprobante_key IS NOT NULL", String.class, empresaId);
        List<String> correosEmpresa = correos.todos(empresaId);
        String clienteFlow = e.getFlowCustomerId();

        tx.executeWithoutResult(status -> {
            Object[] id = { empresaId };
            String ventasDeLaEmpresa = "(SELECT id FROM pos_ventas WHERE empresa_id = ?)";
            String productosDeLaEmpresa = "(SELECT id FROM pos_productos WHERE empresa_id = ?)";

            jdbc.update("DELETE FROM pos_ventas_pagos WHERE venta_id IN " + ventasDeLaEmpresa, id);
            jdbc.update("DELETE FROM pos_ventas_detalles WHERE venta_id IN " + ventasDeLaEmpresa, id);
            jdbc.update("DELETE FROM pos_documentos_dte WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM movimientos_caja WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM cliente_movimientos WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM movimientos_inventario WHERE empresa_id = ?", id);
            jdbc.update("UPDATE pos_productos SET venta_id = NULL WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM pos_ventas WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM turnos_caja WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM pos_recetas WHERE producto_id IN " + productosDeLaEmpresa
                    + " OR insumo_id IN " + productosDeLaEmpresa, empresaId, empresaId);
            jdbc.update("DELETE FROM inventario_sucursal WHERE producto_id IN " + productosDeLaEmpresa
                    + " OR sucursal_id IN (SELECT id FROM sucursales WHERE empresa_id = ?)", empresaId, empresaId);
            jdbc.update("DELETE FROM pos_productos WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM pos_categorias WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM pos_clientes WHERE empresa_id = ?", id);
            // Una nota puede tener solo sucursal (sin empresa): también se borra, o bloquearía la eliminación de la sucursal
            jdbc.update("DELETE FROM notas WHERE empresa_id = ? OR sucursal_id IN (SELECT id FROM sucursales WHERE empresa_id = ?)",
                    empresaId, empresaId);
            jdbc.update("DELETE FROM pos_tokens_recuperacion WHERE usuario_id IN (SELECT id FROM pos_usuarios WHERE empresa_id = ?)", id);
            for (String correo : correosEmpresa) {
                jdbc.update("DELETE FROM pos_verificaciones_correo WHERE correo = ?", correo);
            }
            jdbc.update("DELETE FROM configuracion_dte WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM auditoria_eventos WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM pos_usuarios WHERE empresa_id = ?", id);
            jdbc.update("DELETE FROM sucursales WHERE empresa_id = ?", id);
            int borradas = jdbc.update("DELETE FROM empresas WHERE id = ? AND estado = 'BAJA'", id);
            if (borradas != 1) {
                throw new IllegalStateException("La empresa " + empresaId + " no se pudo eliminar (estado inesperado); se revierte todo.");
            }
        });
        suscripcionService.invalidar(empresaId);

        // Ya confirmada la base: archivos y cliente de pagos (si fallan, solo queda basura externa, no datos de la app)
        for (String clave : clavesS3) {
            try {
                comprobantes.eliminar(clave);
            } catch (RuntimeException ex) {
                log.warn("No se pudo borrar el comprobante {} de la empresa {}: {}", clave, empresaId, ex.getMessage());
            }
        }
        if (clienteFlow != null && !clienteFlow.isBlank()) {
            try {
                Map<String, String> params = new TreeMap<>();
                params.put("customerId", clienteFlow);
                flow.post("/customer/delete", params);
            } catch (RuntimeException ex) {
                log.warn("No se pudo borrar el cliente {} en Flow: {}", clienteFlow, ex.getMessage());
            }
        }
        log.warn("Empresa {} eliminada definitivamente ({} comprobantes de S3).", empresaId, clavesS3.size());
    }
}
