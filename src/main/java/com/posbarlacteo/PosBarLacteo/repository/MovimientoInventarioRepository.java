package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.MovimientoInventario;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    @EntityGraph(attributePaths = {"producto", "sucursal"})
    @Query("SELECT m FROM MovimientoInventario m WHERE m.empresa.id = :empresaId " +
           "AND (:sucursalId IS NULL OR m.sucursal.id = :sucursalId) " +
           "AND (:productoId IS NULL OR m.producto.id = :productoId) " +
           "ORDER BY m.id DESC")
    Page<MovimientoInventario> buscar(@Param("empresaId") Long empresaId,
                                      @Param("sucursalId") Long sucursalId,
                                      @Param("productoId") Long productoId,
                                      Pageable pageable);

    @Query("SELECT m FROM MovimientoInventario m WHERE m.empresa.id = :empresaId AND m.ventaId = :ventaId AND m.tipo = :tipo ORDER BY m.id")
    List<MovimientoInventario> porVenta(@Param("empresaId") Long empresaId, @Param("ventaId") Long ventaId,
                                        @Param("tipo") String tipo);
}
