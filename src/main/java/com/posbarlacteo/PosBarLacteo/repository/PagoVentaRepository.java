package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.PagoVenta;

@Repository
public interface PagoVentaRepository extends JpaRepository<PagoVenta, Long> {

    /** Ventas vigentes (no anuladas) de la empresa cuyo pago lleva exactamente esa referencia (vales manuales). */
    @Query("SELECT COUNT(p) FROM PagoVenta p WHERE p.venta.empresa.id = :empresaId AND p.referencia = :referencia "
            + "AND (p.venta.estado IS NULL OR p.venta.estado <> 'ANULADA')")
    long contarPorReferencia(@Param("empresaId") Long empresaId, @Param("referencia") String referencia);

    @Query("SELECT p FROM PagoVenta p WHERE p.venta.id = :ventaId ORDER BY p.id")
    List<PagoVenta> porVenta(@Param("ventaId") Long ventaId);
}
