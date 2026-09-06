package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.posbarlacteo.PosBarLacteo.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findByEmpresaId(Long empresaId);

    Optional<Cliente> findByRut(String rut);

    // ✨ Normaliza c.rut eliminando '.' y '-' en la base de datos para comparar con terminoLimpio
    @Query("SELECT c FROM Cliente c WHERE c.empresaId = :empresaId AND " +
           "(LOWER(c.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "REPLACE(REPLACE(c.rut, '.', ''), '-', '') LIKE CONCAT('%', :terminoLimpio, '%'))")
    List<Cliente> buscarPorTerminoYEmpresa(
            @Param("termino") String termino, 
            @Param("terminoLimpio") String terminoLimpio, 
            @Param("empresaId") Long empresaId);
}