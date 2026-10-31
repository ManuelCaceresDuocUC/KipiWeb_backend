package com.posbarlacteo.PosBarLacteo.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.InventarioSucursal;

@Repository
public interface InventarioSucursalRepository extends JpaRepository<InventarioSucursal, Long> {

    Optional<InventarioSucursal> findByProductoIdAndSucursalId(Long productoId, Long sucursalId);

    /** Pares [productoId, stock] de una sucursal para una lista de productos (una sola consulta). */
    @Query("SELECT i.producto.id, i.stock FROM InventarioSucursal i " +
           "WHERE i.sucursal.id = :sucursalId AND i.producto.id IN :productoIds")
    List<Object[]> stocksDe(@Param("sucursalId") Long sucursalId, @Param("productoIds") Collection<Long> productoIds);

    @Query("SELECT i.stock FROM InventarioSucursal i WHERE i.producto.id = :productoId AND i.sucursal.id = :sucursalId")
    Optional<Double> stockActual(@Param("productoId") Long productoId, @Param("sucursalId") Long sucursalId);

    /** Crea la fila con stock 0 si no existe (seguro ante concurrencia gracias a la restricción única). */
    @Modifying
    @Query(value = "INSERT IGNORE INTO inventario_sucursal (producto_id, sucursal_id, stock, stock_critico) " +
                   "VALUES (:productoId, :sucursalId, 0, 0)", nativeQuery = true)
    void crearFilaSiNoExiste(@Param("productoId") Long productoId, @Param("sucursalId") Long sucursalId);

    /** Suma (o resta) sin límite: empresas que permiten stock negativo, y todas las entradas. */
    @Modifying
    @Query("UPDATE InventarioSucursal i SET i.stock = i.stock + :delta " +
           "WHERE i.producto.id = :productoId AND i.sucursal.id = :sucursalId")
    int aplicarDelta(@Param("productoId") Long productoId, @Param("sucursalId") Long sucursalId, @Param("delta") double delta);

    /** Resta atómica que NO deja el stock bajo cero: devuelve 0 filas si no alcanza. */
    @Modifying
    @Query("UPDATE InventarioSucursal i SET i.stock = i.stock + :delta " +
           "WHERE i.producto.id = :productoId AND i.sucursal.id = :sucursalId AND i.stock + :delta >= -0.000001")
    int aplicarDeltaConPiso(@Param("productoId") Long productoId, @Param("sucursalId") Long sucursalId, @Param("delta") double delta);

    /** Fija el stock a un valor (conteo físico). */
    @Modifying
    @Query("UPDATE InventarioSucursal i SET i.stock = :valor " +
           "WHERE i.producto.id = :productoId AND i.sucursal.id = :sucursalId")
    int fijarStock(@Param("productoId") Long productoId, @Param("sucursalId") Long sucursalId, @Param("valor") double valor);

    // Valorización (costo si existe, si no precio de venta)
    @Query("SELECT COALESCE(SUM(i.stock * COALESCE(i.producto.costo, i.producto.precio)), 0.0) " +
           "FROM InventarioSucursal i WHERE i.sucursal.empresa.id = :empresaId AND i.producto.activo = true AND i.stock > 0")
    Double valorInventarioEmpresa(@Param("empresaId") Long empresaId);
}
