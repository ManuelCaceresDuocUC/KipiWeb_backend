package com.posbarlacteo.PosBarLacteo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita las tareas @Scheduled (conciliación periódica de suscripciones con Flow). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
