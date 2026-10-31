package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.PagoVenta;

@Repository
public interface PagoVentaRepository extends JpaRepository<PagoVenta, Long> {

    @Query("SELECT p FROM PagoVenta p WHERE p.venta.id = :ventaId ORDER BY p.id")
    List<PagoVenta> porVenta(@Param("ventaId") Long ventaId);
}
