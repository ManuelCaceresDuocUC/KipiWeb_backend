package com.posbarlacteo.PosBarLacteo.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.CompraPago;

@Repository
public interface CompraPagoRepository extends JpaRepository<CompraPago, Long> {

    /** [compraId, suma pagada] de las compras indicadas. */
    @Query("SELECT p.compra.id, SUM(p.monto) FROM CompraPago p WHERE p.compra.empresaId = :empresaId AND p.compra.id IN :ids GROUP BY p.compra.id")
    List<Object[]> pagadoPorCompra(@Param("empresaId") Long empresaId, @Param("ids") Collection<Long> ids);

    /** [proveedorId, suma pagada] de las compras activas. */
    @Query("SELECT p.compra.proveedorId, SUM(p.monto) FROM CompraPago p WHERE p.compra.empresaId = :empresaId AND p.compra.estado = 'ACTIVA' GROUP BY p.compra.proveedorId")
    List<Object[]> pagadoPorProveedor(@Param("empresaId") Long empresaId);
}
