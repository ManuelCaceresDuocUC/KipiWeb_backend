package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.posbarlacteo.PosBarLacteo.model.Nota;

public interface NotaRepository extends JpaRepository<Nota, Long> {
    
    // ✨ NUEVO: Filtra por empresa y ordena de la más reciente a la más antigua
    List<Nota> findByEmpresaIdOrderByFechaHoraDesc(Long empresaId); 
    
    // (Opcional) Puedes conservar este si lo usas en otro lado para un súper-administrador
    List<Nota> findAllByOrderByFechaHoraDesc(); 
}