package com.posbarlacteo.PosBarLacteo.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;

/**
 * Concilia periódicamente cada suscripción con Flow (fuente de verdad) y deja el estado de cada empresa al día.
 * Cada empresa se consulta aparte: si una falla, las demás siguen. Si Flow no responde, no se cambia nada.
 */
@Component
public class ConciliacionFlowJob {

    private static final Logger log = LoggerFactory.getLogger(ConciliacionFlowJob.class);

    private final EmpresaRepository empresaRepository;
    private final SuscripcionService suscripcionService;
    private final boolean habilitado;

    public ConciliacionFlowJob(EmpresaRepository empresaRepository, SuscripcionService suscripcionService,
                               @Value("${suscripcion.sync-habilitado:true}") boolean habilitado) {
        this.empresaRepository = empresaRepository;
        this.suscripcionService = suscripcionService;
        this.habilitado = habilitado;
    }

    @Scheduled(initialDelayString = "${suscripcion.sync-inicial:PT3M}", fixedDelayString = "${suscripcion.sync-intervalo:PT6H}")
    public void conciliar() {
        if (!habilitado) {
            return;
        }
        long inicio = System.currentTimeMillis();
        List<Empresa> empresas = empresaRepository.findByFlowSubscriptionIdIsNotNullAndEstadoIn(SuscripcionService.ESTADOS_CONCILIABLES);

        List<SuscripcionService.Decision> decisiones = new ArrayList<>();
        int fallidas = 0;
        for (Empresa e : empresas) {
            try {
                decisiones.add(suscripcionService.consultar(e));
            } catch (RuntimeException ex) {
                fallidas++;
                log.warn("Conciliación Flow: no se pudo consultar la empresa {}: {}", e.getId(), ex.getMessage());
            }
            pausa(150); // cortesía con el límite de solicitudes de Flow
        }

        // Freno de seguridad: si de pronto "muchas" empresas pasarían a suspendidas a la vez, lo más probable es un
        // problema de datos o de Flow, no una ola de morosos. No se aplica y se deja registrado para revisión.
        long nuevasSuspensiones = decisiones.stream().filter(SuscripcionService.Decision::pasaASuspendida).count();
        boolean sospechoso = nuevasSuspensiones >= 3 && nuevasSuspensiones > decisiones.size() * 0.3;
        if (sospechoso) {
            log.error("Conciliación Flow ABORTADA: {} de {} empresas pasarían a SUSPENDIDA de una vez. No se guardó ningún cambio; revisar manualmente.",
                    nuevasSuspensiones, decisiones.size());
            return;
        }

        int cambios = 0;
        for (SuscripcionService.Decision d : decisiones) {
            Empresa e = d.empresa();
            empresaRepository.save(e);
            suscripcionService.invalidar(e.getId());
            cambios++;
        }

        int rescatadas = suscripcionService.rescatarRegistrosIncompletos(LocalDateTime.now().minusMinutes(10)).size();

        log.info("Conciliación Flow: {} empresas consultadas, {} sin respuesta, {} rescatadas, {} ms.",
                decisiones.size(), fallidas, rescatadas, System.currentTimeMillis() - inicio);
    }

    private static void pausa(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
