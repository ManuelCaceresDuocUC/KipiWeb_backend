package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Categoria;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    
    Optional<Categoria> findByIdAndEmpresaId(Long id, Long empresaId);

    // ✨ NUEVO: Método que filtra por Empresa y que la categoría esté activa
    List<Categoria> findByEmpresaIdAndActivoTrue(Long empresaId);
}