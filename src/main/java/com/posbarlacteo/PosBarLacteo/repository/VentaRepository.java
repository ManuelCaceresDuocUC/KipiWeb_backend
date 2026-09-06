package com.posbarlacteo.PosBarLacteo.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.posbarlacteo.PosBarLacteo.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    // ✨ SOLUCIÓN PARA MOVIMIENTO CONTROLLER:
    // Spring Boot creará la consulta SQL automáticamente basándose en el nombre de este método.
    List<Venta> findByEmpresaIdAndFechaHoraBetween(Long empresaId, LocalDateTime inicio, LocalDateTime fin);

    // =====================================================================
    // 📊 CONSULTAS PARA EL PANEL DE CONTROL (ADMINISTRACIÓN) - FILTRADAS
    // =====================================================================
    
    @Query("SELECT COALESCE(SUM(v.total), 0.0) FROM Venta v WHERE v.empresa.id = :empresaId AND v.fechaHora BETWEEN :inicio AND :fin")
    Double sumarVentasDelDia(@Param("empresaId") Long empresaId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(v.total), 0.0) FROM Venta v WHERE v.empresa.id = :empresaId AND v.fechaHora >= :inicioMes")
    Double sumarVentasDelMes(@Param("empresaId") Long empresaId, @Param("inicioMes") LocalDateTime inicioMes);

    @Query("SELECT COALESCE(AVG(v.total), 0.0) FROM Venta v WHERE v.empresa.id = :empresaId AND v.fechaHora BETWEEN :inicio AND :fin")
    Double obtenerTicketPromedioDelDia(@Param("empresaId") Long empresaId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT d.producto.descripcion AS nombre, SUM(d.cantidad) AS total " +
           "FROM Venta v JOIN v.detalles d " +
           "WHERE v.empresa.id = :empresaId AND v.fechaHora >= :desde " +
           "GROUP BY d.producto.descripcion " +
           "ORDER BY total DESC")
    List<Object[]> obtenerProductosMasVendidos(@Param("empresaId") Long empresaId, @Param("desde") LocalDateTime desde, Pageable pageable);
}