package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List; // ✨ AGREGAR IMPORT
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.TurnoCaja;

@Repository
public interface TurnoCajaRepository extends JpaRepository<TurnoCaja, Long> {
    
    Optional<TurnoCaja> findByCajeroIdAndEstado(Long cajeroId, String estado);

    // ✨ NUEVO: Buscar historial por ID de empresa ordenado por ID descendente
    List<TurnoCaja> findByEmpresaIdOrderByIdDesc(Long empresaId);

    Optional<TurnoCaja> findByIdAndEmpresaId(Long id, Long empresaId);

    /**
     * Suma (o resta, con valores negativos) a los totales de un turno ABIERTO en una sola sentencia,
     * sin leer-modificar-escribir. Devuelve 0 si el turno ya no está abierto.
     */
    @Modifying
    @Query("UPDATE TurnoCaja t SET " +
           "t.ventasEfectivo = COALESCE(t.ventasEfectivo, 0) + :ef, " +
           "t.ventasTarjeta = COALESCE(t.ventasTarjeta, 0) + :tj, " +
           "t.ventasTransferencia = COALESCE(t.ventasTransferencia, 0) + :tr, " +
           "t.ventasCredito = COALESCE(t.ventasCredito, 0) + :cr " +
           "WHERE t.id = :id AND t.estado = 'ABIERTA'")
    int sumarVentas(@Param("id") Long id,
                    @Param("ef") java.math.BigDecimal ef, @Param("tj") java.math.BigDecimal tj,
                    @Param("tr") java.math.BigDecimal tr, @Param("cr") java.math.BigDecimal cr);
}
