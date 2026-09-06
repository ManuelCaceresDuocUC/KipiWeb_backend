package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Receta;

@Repository
public interface RecetaRepository extends JpaRepository<Receta, Long> {
    
    // Este método es el que usa tu VentaService para buscar los ingredientes
    List<Receta> findByProductoPrincipalId(Long productoPrincipalId);
    List<Receta> findByInsumoId(Long insumoId);
}