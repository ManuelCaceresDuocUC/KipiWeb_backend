package com.posbarlacteo.PosBarLacteo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.MovimientoCliente;

@Repository
public interface MovimientoClienteRepository extends JpaRepository<MovimientoCliente, Long> {

    @Query("SELECT m FROM MovimientoCliente m WHERE m.cliente.id = :clienteId AND m.empresa.id = :empresaId ORDER BY m.id DESC")
    Page<MovimientoCliente> porCliente(@Param("clienteId") Long clienteId,
                                       @Param("empresaId") Long empresaId, Pageable pageable);
}
