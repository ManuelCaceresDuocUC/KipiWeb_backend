package com.posbarlacteo.PosBarLacteo.repository;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.posbarlacteo.PosBarLacteo.model.Empresa;
@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    // Puedes agregar métodos personalizados si lo requieres, ej: buscar por RUT
    java.util.Optional<Empresa> findByRutEmpresa(String rutEmpresa);
    Optional<Empresa> findByFlowCustomerId(String flowCustomerId);

    /** Empresas con suscripción en Flow que se deben conciliar. */
    java.util.List<Empresa> findByFlowSubscriptionIdIsNotNullAndEstadoIn(java.util.Collection<String> estados);

    /** Registros que pagaron la tarjeta pero cuya suscripción no se alcanzó a guardar. */
    java.util.List<Empresa> findByEstadoAndFlowCustomerIdIsNotNullAndFlowSubscriptionIdIsNull(String estado);
}
