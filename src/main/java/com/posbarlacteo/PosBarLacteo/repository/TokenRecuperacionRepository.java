package com.posbarlacteo.PosBarLacteo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.posbarlacteo.PosBarLacteo.model.TokenRecuperacion;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Long> {

    Optional<TokenRecuperacion> findByTokenHashAndUsadoFalse(String tokenHash);

    List<TokenRecuperacion> findByUsuarioIdAndUsadoFalse(Long usuarioId);

    @Modifying
    @Query("delete from TokenRecuperacion t where t.creado < :limite")
    int borrarAnteriores(LocalDateTime limite);
}
