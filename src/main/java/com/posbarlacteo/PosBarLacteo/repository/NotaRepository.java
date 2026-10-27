package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.posbarlacteo.PosBarLacteo.model.Nota;

public interface NotaRepository extends JpaRepository<Nota, Long> {
    
    // ✨ NUEVO: Filtra por empresa y ordena de la más reciente a la más antigua
    List<Nota> findByEmpresaIdOrderByFechaHoraDesc(Long empresaId); 
    
    Optional<Nota> findByIdAndEmpresaId(Long id, Long empresaId);
}