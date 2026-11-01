package com.posbarlacteo.PosBarLacteo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.posbarlacteo.PosBarLacteo.model.VerificacionCorreo;

public interface VerificacionCorreoRepository extends JpaRepository<VerificacionCorreo, Long> {

    Optional<VerificacionCorreo> findFirstByCorreoAndUsadoFalseOrderByIdDesc(String correo);

    long countByCorreoAndCreadoAfter(String correo, LocalDateTime desde);

    List<VerificacionCorreo> findByCorreoAndTokenHashAndUsadoFalse(String correo, String tokenHash);

    @Modifying
    @Query("delete from VerificacionCorreo v where v.creado < :limite")
    int borrarAnteriores(LocalDateTime limite);
}
