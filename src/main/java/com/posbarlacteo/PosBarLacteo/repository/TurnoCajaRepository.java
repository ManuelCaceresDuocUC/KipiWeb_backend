package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List; // ✨ AGREGAR IMPORT
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.TurnoCaja;

@Repository
public interface TurnoCajaRepository extends JpaRepository<TurnoCaja, Long> {
    
    Optional<TurnoCaja> findByCajeroIdAndEstado(Long cajeroId, String estado);

    // ✨ NUEVO: Buscar historial por ID de empresa ordenado por ID descendente
    List<TurnoCaja> findByEmpresaIdOrderByIdDesc(Long empresaId);
}