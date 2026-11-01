package com.posbarlacteo.PosBarLacteo.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.DocumentoTributario;

@Repository
public interface DocumentoTributarioRepository extends JpaRepository<DocumentoTributario, Long> {

    Optional<DocumentoTributario> findByEmpresaIdAndVentaIdAndTipoDte(Long empresaId, Long ventaId, int tipoDte);

    List<DocumentoTributario> findByEmpresaIdAndEstadoInOrderByIdDesc(Long empresaId, Collection<String> estados);

    /**
     * "Toma" el documento para emitirlo: solo UN proceso lo consigue (devuelve 1). Así dos reintentos
     * simultáneos no emiten dos veces.
     */
    @Transactional
    @Modifying
    @Query("UPDATE DocumentoTributario d SET d.estado = 'EMITIENDO', d.intentos = d.intentos + 1, d.fechaActualizacion = :ahora "
            + "WHERE d.id = :id AND d.estado IN :permitidos")
    int reclamar(@Param("id") Long id, @Param("ahora") LocalDateTime ahora, @Param("permitidos") Collection<String> permitidos);
}
