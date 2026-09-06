package com.posbarlacteo.PosBarLacteo.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    
    // ✨ MAGIA CORREGIDA: Quitamos el JOIN FETCH para que MySQL haga la paginación
    // Usamos EntityGraph para traer la categoría y empresa en 1 sola consulta rápida (soluciona el problema N+1)
    @EntityGraph(attributePaths = {"categoria", "empresa"})
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.empresa.id = :empresaId")
    Page<Producto> findByActivoTrueAndEmpresaId(@Param("empresaId") Long empresaId, Pageable pageable);
    
    // Verificamos el código de barras pero SOLO dentro de la misma empresa
    Optional<Producto> findByCodigoBarrasAndEmpresaId(String codigoBarras, Long empresaId);
    Optional<Producto> findByCodigoBarras(String codigoBarras);
    
    // Agregamos EntityGraph aquí también para optimizar el filtro por categoría
    @EntityGraph(attributePaths = {"categoria", "empresa"})
    Page<Producto> findByActivoTrueAndCategoriaIdAndEmpresaId(Long categoriaId, Long empresaId, Pageable pageable);
    
    // Calcula el valor del inventario solo de esa empresa
    @Query("SELECT COALESCE(SUM(p.stock * p.precio), 0.0) FROM Producto p WHERE p.activo = true AND p.empresa.id = :empresaId")
    Double calcularValorTotalInventarioPorEmpresa(@Param("empresaId") Long empresaId);

    @Query("SELECT COALESCE(SUM(p.precio * p.stock), 0.0) FROM Producto p WHERE p.activo = true AND p.empresa.id = :empresaId")
    Double calcularValorTotalInventario(@Param("empresaId") Long empresaId);
}