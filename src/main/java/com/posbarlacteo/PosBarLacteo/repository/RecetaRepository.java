package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Receta;

@Repository
public interface RecetaRepository extends JpaRepository<Receta, Long> {
    
    // Este método es el que usa tu VentaService para buscar los ingredientes
    List<Receta> findByProductoPrincipalId(Long productoPrincipalId);
    List<Receta> findByInsumoId(Long insumoId);

    @Query("SELECT r FROM Receta r WHERE r.productoPrincipal.empresa.id = :empresaId")
    List<Receta> findByEmpresaId(@Param("empresaId") Long empresaId);

    @Query("SELECT r FROM Receta r WHERE r.id = :id AND r.productoPrincipal.empresa.id = :empresaId")
    java.util.Optional<Receta> findByIdAndEmpresaId(@Param("id") Long id, @Param("empresaId") Long empresaId);
}