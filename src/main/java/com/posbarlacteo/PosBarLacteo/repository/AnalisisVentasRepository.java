package com.posbarlacteo.PosBarLacteo.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;

/** Consultas de solo lectura para analizar qué se vende (sugerencias de precio y ofertas). */
@Repository
public interface AnalisisVentasRepository extends JpaRepository<VentaDetalle, Long> {

    /** [productoId, unidades vendidas, ingresos] desde una fecha, sin contar ventas anuladas. */
    @Query("SELECT d.producto.id, SUM(d.cantidad), SUM(d.cantidad * d.precioUnitario) "
         + "FROM VentaDetalle d JOIN d.venta v "
         + "WHERE v.empresa.id = :empresaId AND v.fechaHora >= :desde AND (v.estado IS NULL OR v.estado <> 'ANULADA') "
         + "GROUP BY d.producto.id")
    List<Object[]> vendidoDesde(@Param("empresaId") Long empresaId, @Param("desde") LocalDateTime desde);

    /** [productoId, fecha de la última venta] de todo el historial, sin contar ventas anuladas. */
    @Query("SELECT d.producto.id, MAX(v.fechaHora) FROM VentaDetalle d JOIN d.venta v "
         + "WHERE v.empresa.id = :empresaId AND (v.estado IS NULL OR v.estado <> 'ANULADA') GROUP BY d.producto.id")
    List<Object[]> ultimaVenta(@Param("empresaId") Long empresaId);

    /** [cantidad de ventas, total vendido] de un turno de caja, sin contar las anuladas. */
    @Query("SELECT COUNT(v), COALESCE(SUM(v.total), 0.0) FROM Venta v WHERE v.turnoId = :turnoId AND (v.estado IS NULL OR v.estado <> 'ANULADA')")
    List<Object[]> ventasDelTurno(@Param("turnoId") Long turnoId);

    @Query("SELECT COUNT(v) FROM Venta v WHERE v.turnoId = :turnoId AND v.estado = 'ANULADA'")
    Long anuladasDelTurno(@Param("turnoId") Long turnoId);

    /** [productoId, stock total en todas las sucursales]. */
    @Query("SELECT i.producto.id, SUM(i.stock) FROM InventarioSucursal i WHERE i.producto.empresa.id = :empresaId GROUP BY i.producto.id")
    List<Object[]> stockTotal(@Param("empresaId") Long empresaId);
}
