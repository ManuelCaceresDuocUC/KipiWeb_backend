package com.posbarlacteo.PosBarLacteo.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Compra;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {

    Optional<Compra> findByIdAndEmpresaId(Long id, Long empresaId);

    @Query("SELECT c FROM Compra c WHERE c.empresaId = :empresaId "
         + "AND (:proveedorId IS NULL OR c.proveedorId = :proveedorId) "
         + "AND c.fechaDocumento >= :desde AND c.fechaDocumento <= :hasta "
         + "ORDER BY c.fechaDocumento DESC, c.id DESC")
    List<Compra> listar(@Param("empresaId") Long empresaId, @Param("proveedorId") Long proveedorId,
                        @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta, Pageable pageable);

    /** Compras activas de un proveedor con el mismo tipo y folio (para no registrar dos veces el mismo documento). */
    @Query("SELECT COUNT(c) > 0 FROM Compra c WHERE c.empresaId = :empresaId AND c.proveedorId = :proveedorId "
         + "AND c.tipoDocumento = :tipo AND c.folio = :folio AND c.estado = 'ACTIVA'")
    boolean existeDocumento(@Param("empresaId") Long empresaId, @Param("proveedorId") Long proveedorId,
                            @Param("tipo") String tipo, @Param("folio") String folio);

    /** [proveedorId, suma de totales] de las compras activas. */
    @Query("SELECT c.proveedorId, SUM(c.total) FROM Compra c WHERE c.empresaId = :empresaId AND c.estado = 'ACTIVA' GROUP BY c.proveedorId")
    List<Object[]> totalesPorProveedor(@Param("empresaId") Long empresaId);

    @Query("SELECT COALESCE(SUM(c.total), 0) FROM Compra c WHERE c.empresaId = :empresaId AND c.estado = 'ACTIVA' "
         + "AND c.fechaDocumento >= :desde AND c.fechaDocumento <= :hasta")
    Long totalEnPeriodo(@Param("empresaId") Long empresaId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Compras activas con vencimiento anterior a una fecha (candidatas a estar vencidas si siguen con saldo). */
    @Query("SELECT c FROM Compra c WHERE c.empresaId = :empresaId AND c.estado = 'ACTIVA' AND c.vencimiento IS NOT NULL AND c.vencimiento < :hoy")
    List<Compra> conVencimientoPasado(@Param("empresaId") Long empresaId, @Param("hoy") LocalDate hoy);
}
