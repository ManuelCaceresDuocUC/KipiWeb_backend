package com.posbarlacteo.PosBarLacteo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.posbarlacteo.PosBarLacteo.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    // ✨ SOLUCIÓN PARA MOVIMIENTO CONTROLLER:
    // Spring Boot creará la consulta SQL automáticamente basándose en el nombre de este método.
    List<Venta> findByEmpresaIdAndFechaHoraBetween(Long empresaId, LocalDateTime inicio, LocalDateTime fin);

    // =====================================================================
    // 📊 CONSULTAS PARA EL PANEL DE CONTROL (ADMINISTRACIÓN) - FILTRADAS
    // =====================================================================
    
    @Query("SELECT COALESCE(SUM(v.total), 0.0) FROM Venta v WHERE v.empresa.id = :empresaId AND v.fechaHora BETWEEN :inicio AND :fin AND (v.estado IS NULL OR v.estado <> 'ANULADA')")
    Double sumarVentasDelDia(@Param("empresaId") Long empresaId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(v.total), 0.0) FROM Venta v WHERE v.empresa.id = :empresaId AND v.fechaHora >= :inicioMes AND (v.estado IS NULL OR v.estado <> 'ANULADA')")
    Double sumarVentasDelMes(@Param("empresaId") Long empresaId, @Param("inicioMes") LocalDateTime inicioMes);

    @Query("SELECT COALESCE(AVG(v.total), 0.0) FROM Venta v WHERE v.empresa.id = :empresaId AND v.fechaHora BETWEEN :inicio AND :fin AND (v.estado IS NULL OR v.estado <> 'ANULADA')")
    Double obtenerTicketPromedioDelDia(@Param("empresaId") Long empresaId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT d.producto.descripcion AS nombre, SUM(d.cantidad) AS total " +
           "FROM Venta v JOIN v.detalles d " +
           "WHERE v.empresa.id = :empresaId AND v.fechaHora >= :desde AND (v.estado IS NULL OR v.estado <> 'ANULADA') " +
           "GROUP BY d.producto.descripcion " +
           "ORDER BY total DESC")
    List<Object[]> obtenerProductosMasVendidos(@Param("empresaId") Long empresaId, @Param("desde") LocalDateTime desde, Pageable pageable);

    Optional<Venta> findByIdAndEmpresaId(Long id, Long empresaId);

    @Query("SELECT v FROM Venta v WHERE v.empresa.id = :empresaId AND v.idempotencyKey = :clave")
    Optional<Venta> buscarPorClave(@Param("empresaId") Long empresaId, @Param("clave") String clave);

    /** Bloqueo de escritura: dos anulaciones simultáneas de la misma venta no pueden pasar a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venta v WHERE v.id = :id AND v.empresa.id = :empresaId")
    Optional<Venta> buscarConBloqueo(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
