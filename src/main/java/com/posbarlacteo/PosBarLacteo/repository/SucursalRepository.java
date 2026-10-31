package com.posbarlacteo.PosBarLacteo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Sucursal;

@Repository
public interface SucursalRepository extends JpaRepository<Sucursal, Long> {

    java.util.Optional<Sucursal> findByIdAndEmpresaId(Long id, Long empresaId);

    java.util.List<Sucursal> findByEmpresaIdAndActivoTrueOrderByIdAsc(Long empresaId);
}