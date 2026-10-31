package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.posbarlacteo.PosBarLacteo.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findByEmpresaId(Long empresaId);

    Optional<Cliente> findByIdAndEmpresaId(Long id, Long empresaId);

    Optional<Cliente> findByRutAndEmpresaId(String rut, Long empresaId);

    // ✨ CORREGIDO: c.empresaId cambiado a c.empresa.id para coincidir con el campo de la entidad
    @Query("SELECT c FROM Cliente c WHERE c.empresa.id = :empresaId AND " +
           "(LOWER(c.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "REPLACE(REPLACE(c.rut, '.', ''), '-', '') LIKE CONCAT('%', :terminoLimpio, '%'))")
    List<Cliente> buscarPorTerminoYEmpresa(
            @Param("termino") String termino, 
            @Param("terminoLimpio") String terminoLimpio, 
            @Param("empresaId") Long empresaId);

    /** Suma deuda SOLO si el nuevo saldo no supera el límite del cliente. Atómico: devuelve 0 si no cabe. */
    @Modifying
    @Query("UPDATE Cliente c SET c.deudaActual = COALESCE(c.deudaActual, 0) + :monto " +
           "WHERE c.id = :id AND c.empresa.id = :empresaId " +
           "AND COALESCE(c.deudaActual, 0) + :monto <= COALESCE(c.limiteCredito, 0)")
    int cargarDeudaConLimite(@Param("id") Long id, @Param("empresaId") Long empresaId, @Param("monto") double monto);

    /** Resta deuda solo si alcanza (abonos). Atómico: devuelve 0 si el monto excede la deuda. */
    @Modifying
    @Query("UPDATE Cliente c SET c.deudaActual = COALESCE(c.deudaActual, 0) - :monto " +
           "WHERE c.id = :id AND c.empresa.id = :empresaId AND COALESCE(c.deudaActual, 0) >= :monto")
    int abonarDeuda(@Param("id") Long id, @Param("empresaId") Long empresaId, @Param("monto") double monto);

    @Query("SELECT COALESCE(c.deudaActual, 0) FROM Cliente c WHERE c.id = :id AND c.empresa.id = :empresaId")
    Double saldoActual(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
