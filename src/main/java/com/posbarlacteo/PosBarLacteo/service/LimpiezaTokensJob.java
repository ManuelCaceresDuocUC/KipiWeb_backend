package com.posbarlacteo.PosBarLacteo.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Borra códigos de verificación y enlaces de recuperación vencidos (a diario). */
@Component
public class LimpiezaTokensJob {

    private final VerificacionCorreoService verificacion;
    private final RecuperacionClaveService recuperacion;

    public LimpiezaTokensJob(VerificacionCorreoService verificacion, RecuperacionClaveService recuperacion) {
        this.verificacion = verificacion;
        this.recuperacion = recuperacion;
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT24H")
    public void limpiar() {
        verificacion.limpiar();
        recuperacion.limpiar();
    }
}
