package com.posbarlacteo.PosBarLacteo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.posbarlacteo.PosBarLacteo.model.Empresa;

/**
 * Elimina definitivamente las cuentas dadas de baja una vez vencido el plazo de gracia.
 * Cada empresa va en su propia transacción: si una falla, queda registrada y las demás siguen.
 * Se apaga con SUSCRIPCION_BORRADO_HABILITADO=false.
 */
@Component
public class BajaCuentaJob {

    private static final Logger log = LoggerFactory.getLogger(BajaCuentaJob.class);

    private final BajaCuentaService bajas;
    private final boolean habilitado;

    public BajaCuentaJob(BajaCuentaService bajas, @Value("${suscripcion.borrado-habilitado:true}") boolean habilitado) {
        this.bajas = bajas;
        this.habilitado = habilitado;
    }

    @Scheduled(initialDelayString = "${suscripcion.borrado-inicial:PT10M}", fixedDelayString = "${suscripcion.borrado-intervalo:PT6H}")
    public void eliminarVencidas() {
        if (!habilitado) {
            return;
        }
        for (Empresa e : bajas.pendientesDeEliminar()) {
            try {
                bajas.eliminarDefinitivo(e.getId());
            } catch (RuntimeException ex) {
                log.error("No se pudo eliminar la empresa {} dada de baja (se reintentará): {}", e.getId(), ex.getMessage(), ex);
            }
        }
    }
}
