package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.MovimientoCaja;

@Repository
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    @Query("SELECT m FROM MovimientoCaja m WHERE m.turno.id = :turnoId AND m.empresa.id = :empresaId ORDER BY m.id DESC")
    List<MovimientoCaja> porTurno(@Param("turnoId") Long turnoId, @Param("empresaId") Long empresaId);
}
