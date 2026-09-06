package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Categoria;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    
    // Método para traer todas las activas (uso administrativo global)
    List<Categoria> findByActivoTrue();

    // ✨ NUEVO: Método que filtra por Empresa y que la categoría esté activa
    List<Categoria> findByEmpresaIdAndActivoTrue(Long empresaId);
}