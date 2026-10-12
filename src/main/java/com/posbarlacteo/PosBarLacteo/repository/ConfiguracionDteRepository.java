package com.posbarlacteo.PosBarLacteo.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.posbarlacteo.PosBarLacteo.model.ConfiguracionDte;

@Repository
public interface ConfiguracionDteRepository extends JpaRepository<ConfiguracionDte, Long> {
    
    // Método clave que usa DteService para saber qué reglas aplicar
    Optional<ConfiguracionDte> findByEmpresaId(Long empresaId);
}