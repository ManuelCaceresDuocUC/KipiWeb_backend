package com.posbarlacteo.PosBarLacteo.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.InventarioSucursal;

@Repository
public interface InventarioSucursalRepository extends JpaRepository<InventarioSucursal, Long> {

    // Obtener inventario de una sucursal específica
    Page<InventarioSucursal> findBySucursalId(Long sucursalId, Pageable pageable);

    // Buscar el stock de un producto específico en una sucursal
    Optional<InventarioSucursal> findByProductoIdAndSucursalId(Long productoId, Long sucursalId);

    // Valor del inventario de UNA sucursal
    @Query("SELECT COALESCE(SUM(i.stock * i.producto.precio), 0.0) " +
           "FROM InventarioSucursal i " +
           "WHERE i.sucursal.id = :sucursalId AND i.producto.activo = true")
    Double calcularValorInventarioPorSucursal(@Param("sucursalId") Long sucursalId);

    // Valor total del inventario de TODAS las sucursales de la empresa (Vista Admin)
    @Query("SELECT COALESCE(SUM(i.stock * i.producto.precio), 0.0) " +
           "FROM InventarioSucursal i " +
           "WHERE i.sucursal.empresa.id = :empresaId AND i.producto.activo = true")
    Double calcularValorInventarioTotalEmpresa(@Param("empresaId") Long empresaId);
}