package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Devolucion;

@Repository
public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    @Query("SELECT d FROM Devolucion d WHERE d.empresaId = :empresaId AND d.ventaId = :ventaId ORDER BY d.id DESC")
    List<Devolucion> porVenta(@Param("empresaId") Long empresaId, @Param("ventaId") Long ventaId);

    @Query("SELECT d FROM Devolucion d WHERE d.empresaId = :empresaId ORDER BY d.id DESC")
    List<Devolucion> recientes(@Param("empresaId") Long empresaId, Pageable pageable);

    /** Lo ya devuelto de cada producto de una venta: [productoId, cantidadTotal]. */
    @Query("SELECT i.productoId, SUM(i.cantidad) FROM DevolucionItem i "
         + "WHERE i.devolucion.empresaId = :empresaId AND i.devolucion.ventaId = :ventaId GROUP BY i.productoId")
    List<Object[]> devueltoPorProducto(@Param("empresaId") Long empresaId, @Param("ventaId") Long ventaId);
}
