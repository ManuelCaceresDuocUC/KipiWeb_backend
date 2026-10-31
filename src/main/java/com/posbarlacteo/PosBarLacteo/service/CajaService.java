package com.posbarlacteo.PosBarLacteo.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.dto.CierreCajaDTO;
import com.posbarlacteo.PosBarLacteo.dto.MovimientoCajaDTO;
import com.posbarlacteo.PosBarLacteo.dto.ResumenCajaDTO;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.MovimientoCaja;
import com.posbarlacteo.PosBarLacteo.model.TurnoCaja;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.repository.MovimientoCajaRepository;
import com.posbarlacteo.PosBarLacteo.repository.TurnoCajaRepository;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/** Toda operación se hace sobre el turno del usuario del token; el cliente nunca indica de quién es la caja. */
@Service
public class CajaService {

    private static final BigDecimal MAX_MONTO = new BigDecimal("999999999");

    private final TurnoCajaRepository turnoCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;

    public CajaService(TurnoCajaRepository turnoCajaRepository, UsuarioRepository usuarioRepository,
                       MovimientoCajaRepository movimientoCajaRepository) {
        this.turnoCajaRepository = turnoCajaRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
    }

    public boolean tieneCajaAbierta(AuthPrincipal p) {
        return turnoCajaRepository.findByCajeroIdAndEstado(p.usuarioId(), "ABIERTA").isPresent();
    }

    @Transactional
    public void abrirCaja(AuthPrincipal p, BigDecimal montoInicial) {
        validarMonto(montoInicial, true, "El monto inicial");
        if (tieneCajaAbierta(p)) {
            throw new NegocioException("Ya tienes una caja abierta.");
        }

        Usuario usuario = usuarioRepository.findById(p.usuarioId())
                .orElseThrow(() -> new NegocioException("Usuario no encontrado."));
        if (usuario.getEmpresa() == null || !usuario.getEmpresa().getId().equals(p.empresaId())) {
            throw new NegocioException("Sesión inconsistente. Vuelve a iniciar sesión.");
        }
        if (usuario.getSucursal() == null) {
            throw new NegocioException("Tu usuario no tiene una sucursal asignada.");
        }

        TurnoCaja turno = new TurnoCaja();
        turno.setCajeroId(p.usuarioId());
        turno.setEmpresa(usuario.getEmpresa());
        turno.setSucursal(usuario.getSucursal());
        turno.setFechaApertura(LocalDateTime.now());
        turno.setMontoApertura(montoInicial);
        turno.setEstado("ABIERTA");
        turnoCajaRepository.save(turno);
    }

    /**
     * Cierra el turno. El único dato que aporta el cajero es el efectivo contado (totalRealFisico);
     * el total esperado y la diferencia los calcula el servidor con lo acumulado en el turno.
     */
    @Transactional
    public void cerrarCaja(AuthPrincipal p, CierreCajaDTO cierre) {
        validarMonto(cierre.getTotalRealFisico(), true, "El efectivo contado");
        TurnoCaja turno = turnoAbierto(p, "No hay ninguna caja abierta para cerrar.");

        BigDecimal esperado = efectivoEsperado(turno);

        turno.setEstado("CERRADA");
        turno.setFechaCierre(LocalDateTime.now());
        turno.setTotalSistema(esperado);
        turno.setTotalRealFisico(cierre.getTotalRealFisico());
        turno.setDiferencia(cierre.getTotalRealFisico().subtract(esperado));
        turnoCajaRepository.save(turno);
    }

    @Transactional
    public void registrarMovimiento(AuthPrincipal p, MovimientoCajaDTO mov) {
        if (mov.getMonto() == null || mov.getMonto() <= 0 || mov.getMonto().isNaN() || mov.getMonto().isInfinite()) {
            throw new NegocioException("El monto debe ser mayor a cero.");
        }
        String motivo = mov.getMotivo() == null ? "" : mov.getMotivo().trim();
        if (motivo.length() < 3) {
            throw new NegocioException("Indica el motivo del movimiento.");
        }
        if (motivo.length() > 255) {
            throw new NegocioException("El motivo no puede superar los 255 caracteres.");
        }
        TurnoCaja turno = turnoAbierto(p, "No hay ninguna caja abierta para registrar movimientos.");
        BigDecimal monto = BigDecimal.valueOf(mov.getMonto());
        validarMonto(monto, false, "El monto");

        String tipo;
        if ("ingreso".equalsIgnoreCase(mov.getTipo())) {
            turno.setIngresosExtra(nz(turno.getIngresosExtra()).add(monto));
            tipo = MovimientoCaja.INGRESO;
        } else if ("retiro".equalsIgnoreCase(mov.getTipo())) {
            BigDecimal enCaja = efectivoEsperado(turno);
            if (monto.compareTo(enCaja) > 0) {
                throw new NegocioException("No puedes retirar más efectivo del que hay en caja.");
            }
            turno.setRetiros(nz(turno.getRetiros()).add(monto));
            tipo = MovimientoCaja.RETIRO;
        } else {
            throw new NegocioException("Tipo de movimiento inválido. Debe ser 'ingreso' o 'retiro'.");
        }
        turnoCajaRepository.save(turno);
        auditar(turno, p, tipo, "EFECTIVO", monto, motivo, null, null);
    }

    /**
     * Registra un abono de cliente en el turno abierto. Efectivo: entra al cajón (abonosCredito).
     * Tarjeta y transferencia: no entran al cajón, se suman a su propio total del turno.
     */
    @Transactional
    public void registrarAbono(AuthPrincipal p, BigDecimal monto, String metodoPago, Long clienteId, String nombreCliente) {
        validarMonto(monto, false, "El monto del abono");
        TurnoCaja turno = turnoAbierto(p, "Abre la caja antes de registrar abonos.");
        String metodo = metodoPago == null ? "" : metodoPago.trim().toUpperCase();
        if ("TARJETA".equals(metodo)) {
            turno.setVentasTarjeta(nz(turno.getVentasTarjeta()).add(monto));
        } else if ("TRANSFERENCIA".equals(metodo)) {
            turno.setVentasTransferencia(nz(turno.getVentasTransferencia()).add(monto));
        } else if ("EFECTIVO".equals(metodo)) {
            turno.setAbonosCredito(nz(turno.getAbonosCredito()).add(monto));
        } else {
            throw new NegocioException("Método de pago del abono no soportado: " + metodoPago);
        }
        turnoCajaRepository.save(turno);
        auditar(turno, p, MovimientoCaja.ABONO, metodo, monto,
                "Abono de " + (nombreCliente != null ? nombreCliente : "cliente"), null, clienteId);
    }

    /** Movimientos manuales del turno abierto del usuario (más recientes primero). */
    @Transactional(readOnly = true)
    public List<MovimientoCaja> movimientosTurnoActual(AuthPrincipal p) {
        TurnoCaja turno = turnoAbierto(p, "No hay ninguna caja abierta.");
        return movimientoCajaRepository.porTurno(turno.getId(), p.empresaId());
    }

    /** Movimientos de cualquier turno de la empresa (solo administrador, ver SecurityConfig). */
    @Transactional(readOnly = true)
    public List<MovimientoCaja> movimientosDeTurno(AuthPrincipal p, Long turnoId) {
        turnoCajaRepository.findByIdAndEmpresaId(turnoId, p.empresaId())
                .orElseThrow(() -> new NegocioException("Turno no encontrado."));
        return movimientoCajaRepository.porTurno(turnoId, p.empresaId());
    }

    private void auditar(TurnoCaja turno, AuthPrincipal p, String tipo, String metodo, BigDecimal monto,
                         String motivo, Long ventaId, Long clienteId) {
        MovimientoCaja m = new MovimientoCaja();
        m.setTurno(turno);
        m.setEmpresa(turno.getEmpresa());
        m.setTipo(tipo);
        m.setMetodoPago(metodo);
        m.setMonto(monto);
        m.setMotivo(motivo);
        m.setUsuarioNombre(p.usuario());
        m.setVentaId(ventaId);
        m.setClienteId(clienteId);
        movimientoCajaRepository.save(m);
    }

    @Transactional(readOnly = true)
    public List<TurnoCaja> obtenerHistorial(AuthPrincipal p) {
        return turnoCajaRepository.findByEmpresaIdOrderByIdDesc(p.empresaId());
    }

    @Transactional(readOnly = true)
    public ResumenCajaDTO obtenerResumen(AuthPrincipal p) {
        TurnoCaja t = turnoAbierto(p, "No hay ninguna caja abierta.");
        ResumenCajaDTO resumen = new ResumenCajaDTO(
                nz(t.getMontoApertura()).doubleValue(),
                nz(t.getVentasEfectivo()).doubleValue(),
                nz(t.getVentasTarjeta()).doubleValue(),
                nz(t.getVentasCredito()).doubleValue(),
                nz(t.getIngresosExtra()).doubleValue(),
                nz(t.getAbonosCredito()).doubleValue(),
                nz(t.getRetiros()).doubleValue(),
                efectivoEsperado(t).doubleValue());
        resumen.setVentasTransferencia(nz(t.getVentasTransferencia()).doubleValue());
        return resumen;
    }

    /** Efectivo físico que debería haber: fondo + ventas en efectivo + ingresos + abonos en efectivo − retiros. */
    private BigDecimal efectivoEsperado(TurnoCaja t) {
        return nz(t.getMontoApertura())
                .add(nz(t.getVentasEfectivo()))
                .add(nz(t.getIngresosExtra()))
                .add(nz(t.getAbonosCredito()))
                .subtract(nz(t.getRetiros()));
    }

    private TurnoCaja turnoAbierto(AuthPrincipal p, String mensaje) {
        return turnoCajaRepository.findByCajeroIdAndEstado(p.usuarioId(), "ABIERTA")
                .orElseThrow(() -> new NegocioException(mensaje));
    }

    private void validarMonto(BigDecimal monto, boolean permiteCero, String nombre) {
        if (monto == null) {
            throw new NegocioException(nombre + " es obligatorio.");
        }
        int cmp = monto.signum();
        if (cmp < 0 || (cmp == 0 && !permiteCero) || monto.compareTo(MAX_MONTO) > 0) {
            throw new NegocioException(nombre + " no es válido.");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
