package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Proveedor;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    Optional<Proveedor> findByIdAndEmpresaId(Long id, Long empresaId);

    @Query("SELECT p FROM Proveedor p WHERE p.empresaId = :empresaId AND p.activo = true ORDER BY LOWER(p.nombre)")
    List<Proveedor> activos(@Param("empresaId") Long empresaId);

    @Query("SELECT COUNT(p) > 0 FROM Proveedor p WHERE p.empresaId = :empresaId AND p.activo = true "
         + "AND LOWER(p.nombre) = LOWER(:nombre) AND (:excluirId IS NULL OR p.id <> :excluirId)")
    boolean existeNombre(@Param("empresaId") Long empresaId, @Param("nombre") String nombre, @Param("excluirId") Long excluirId);
}
