package com.posbarlacteo.PosBarLacteo.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.dto.CierreCajaDTO;
import com.posbarlacteo.PosBarLacteo.dto.MovimientoCajaDTO;
import com.posbarlacteo.PosBarLacteo.dto.ResumenCajaDTO;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.model.TurnoCaja;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.TurnoCajaRepository;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;

@Service
public class CajaService {

    @Autowired
    private TurnoCajaRepository turnoCajaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    public boolean tieneCajaAbierta(Long cajeroId) {
        return turnoCajaRepository.findByCajeroIdAndEstado(cajeroId, "ABIERTA").isPresent();
    }

    // ✨ CORRECCIÓN: Solo recibe usuarioId y montoInicial
    public void abrirCaja(Long usuarioId, BigDecimal montoInicial) {
        if (tieneCajaAbierta(usuarioId)) {
            throw new RuntimeException("El usuario ya tiene una caja abierta");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            
        // ✨ CORRECCIÓN AQUÍ: Se obtiene el ID desde el objeto Empresa del Usuario
        Long empresaIdReal = usuario.getEmpresa().getId(); 

        Empresa empresa = empresaRepository.findById(empresaIdReal)
            .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        TurnoCaja turno = new TurnoCaja();
        turno.setCajeroId(usuarioId); 
        turno.setEmpresa(empresa); 
        turno.setFechaApertura(LocalDateTime.now());
        turno.setMontoApertura(montoInicial); 
        turno.setEstado("ABIERTA"); 

        turnoCajaRepository.save(turno);
    }

    public void cerrarCaja(Long cajeroId, CierreCajaDTO cierreDTO) {
        TurnoCaja turnoAbierto = turnoCajaRepository.findByCajeroIdAndEstado(cajeroId, "ABIERTA")
                .orElseThrow(() -> new RuntimeException("No hay ninguna caja abierta para este usuario."));

        turnoAbierto.setEstado("CERRADA");
        turnoAbierto.setFechaCierre(LocalDateTime.now());

        // Guardado de la cuadratura y cierre
        turnoAbierto.setTotalSistema(cierreDTO.getTotalSistema());
        turnoAbierto.setTotalRealFisico(cierreDTO.getTotalRealFisico());
        turnoAbierto.setDiferencia(cierreDTO.getDiferencia());

        // Consolidación de los acumulados de operación de la jornada
        if (cierreDTO.getVentasEfectivo() != null) turnoAbierto.setVentasEfectivo(cierreDTO.getVentasEfectivo());
        if (cierreDTO.getVentasTarjeta() != null) turnoAbierto.setVentasTarjeta(cierreDTO.getVentasTarjeta());
        if (cierreDTO.getIngresosExtra() != null) turnoAbierto.setIngresosExtra(cierreDTO.getIngresosExtra());
        if (cierreDTO.getAbonosCredito() != null) turnoAbierto.setAbonosCredito(cierreDTO.getAbonosCredito()); // ✨ NUEVO
        if (cierreDTO.getRetiros() != null) turnoAbierto.setRetiros(cierreDTO.getRetiros());

        turnoCajaRepository.save(turnoAbierto);
    }

    public void registrarMovimiento(Long cajeroId, MovimientoCajaDTO movimiento) {
        TurnoCaja turnoAbierto = turnoCajaRepository.findByCajeroIdAndEstado(cajeroId, "ABIERTA")
                .orElseThrow(() -> new RuntimeException("No hay ninguna caja abierta para registrar movimientos."));

        BigDecimal monto = BigDecimal.valueOf(movimiento.getMonto());

        if ("ingreso".equalsIgnoreCase(movimiento.getTipo())) {
            BigDecimal actual = turnoAbierto.getIngresosExtra() != null ? turnoAbierto.getIngresosExtra() : BigDecimal.ZERO;
            turnoAbierto.setIngresosExtra(actual.add(monto));
        } else if ("retiro".equalsIgnoreCase(movimiento.getTipo())) {
            BigDecimal actual = turnoAbierto.getRetiros() != null ? turnoAbierto.getRetiros() : BigDecimal.ZERO;
            turnoAbierto.setRetiros(actual.add(monto));
        } else {
            throw new RuntimeException("Tipo de movimiento inválido. Debe ser 'ingreso' o 'retiro'.");
        }

        turnoCajaRepository.save(turnoAbierto);
    }

    // ✨ NUEVO MÉTODO: Registra el abono de dinero en la caja activa del cajero
    public void registrarAbonoCredito(Long cajeroId, BigDecimal monto) {
        TurnoCaja turnoAbierto = turnoCajaRepository.findByCajeroIdAndEstado(cajeroId, "ABIERTA")
                .orElseThrow(() -> new RuntimeException("No hay ninguna caja abierta para registrar el abono."));

        BigDecimal actual = turnoAbierto.getAbonosCredito() != null ? turnoAbierto.getAbonosCredito() : BigDecimal.ZERO;
        turnoAbierto.setAbonosCredito(actual.add(monto));

        turnoCajaRepository.save(turnoAbierto);
    }

    public java.util.List<TurnoCaja> obtenerHistorial(Long empresaId) {
        return turnoCajaRepository.findByEmpresaIdOrderByIdDesc(empresaId);
    }

    public ResumenCajaDTO obtenerResumen(Long cajeroId) {
        TurnoCaja turnoAbierto = turnoCajaRepository.findByCajeroIdAndEstado(cajeroId, "ABIERTA")
                .orElseThrow(() -> new RuntimeException("No hay ninguna caja abierta."));

        BigDecimal fondo = turnoAbierto.getMontoApertura() != null ? turnoAbierto.getMontoApertura() : BigDecimal.ZERO;
        BigDecimal vEfectivo = turnoAbierto.getVentasEfectivo() != null ? turnoAbierto.getVentasEfectivo() : BigDecimal.ZERO;
        BigDecimal vTarjeta = turnoAbierto.getVentasTarjeta() != null ? turnoAbierto.getVentasTarjeta() : BigDecimal.ZERO;
        BigDecimal vCredito = turnoAbierto.getVentasCredito() != null ? turnoAbierto.getVentasCredito() : BigDecimal.ZERO;
        BigDecimal ingresos = turnoAbierto.getIngresosExtra() != null ? turnoAbierto.getIngresosExtra() : BigDecimal.ZERO;
        
        // ✨ NUEVO: Rescatar abonos acumulados de la caja
        BigDecimal abonosCredito = turnoAbierto.getAbonosCredito() != null ? turnoAbierto.getAbonosCredito() : BigDecimal.ZERO;
        BigDecimal retiros = turnoAbierto.getRetiros() != null ? turnoAbierto.getRetiros() : BigDecimal.ZERO;

        // ✨ SE SUMA abonosCredito AL DINERO FÍSICO EN CAJA
        BigDecimal totalEnCaja = fondo.add(vEfectivo).add(ingresos).add(abonosCredito).subtract(retiros);

        // ✨ PASAMOS LOS 8 PARÁMETROS AL DTO
        return new ResumenCajaDTO(
            fondo.doubleValue(),
            vEfectivo.doubleValue(),
            vTarjeta.doubleValue(),
            vCredito.doubleValue(),
            ingresos.doubleValue(),
            abonosCredito.doubleValue(), // ✨ NUEVO EN EL DTO
            retiros.doubleValue(),
            totalEnCaja.doubleValue()
        );
    }
}