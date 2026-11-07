package com.posbarlacteo.PosBarLacteo.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Guarda en memoria los últimos avisos y errores del servidor para el panel del dueño.
 * Es temporal: se pierde al reiniciar (Render reinicia al desplegar). Para historial largo, revisar los logs de Render.
 */
@Component
public class ErroresRecientes {

    public record Entrada(Instant fecha, String nivel, String origen, String mensaje, String excepcion) { }

    private static final int MAX = 300;
    private final ConcurrentLinkedDeque<Entrada> buffer = new ConcurrentLinkedDeque<>();
    private AppenderBase<ILoggingEvent> appender;

    @PostConstruct
    void instalar() {
        if (!(LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext ctx)) {
            return;
        }
        appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent ev) {
                if (ev.getLevel().isGreaterOrEqual(Level.WARN)) {
                    registrar(ev);
                }
            }
        };
        appender.setContext(ctx);
        appender.setName("ERRORES_RECIENTES");
        appender.start();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @PreDestroy
    void quitar() {
        if (appender != null) {
            appender.stop();
        }
    }

    private void registrar(ILoggingEvent ev) {
        IThrowableProxy t = ev.getThrowableProxy();
        String exc = t == null ? null : recortar(t.getClassName() + ": " + t.getMessage(), 300);
        String origen = ev.getLoggerName();
        int i = origen.lastIndexOf('.');
        buffer.addFirst(new Entrada(Instant.ofEpochMilli(ev.getTimeStamp()), ev.getLevel().toString(),
                i >= 0 ? origen.substring(i + 1) : origen, recortar(ev.getFormattedMessage(), 600), exc));
        while (buffer.size() > MAX) {
            buffer.pollLast();
        }
    }

    public List<Entrada> recientes(String nivel, int max) {
        List<Entrada> out = new ArrayList<>();
        for (Entrada e : buffer) {
            if (nivel == null || nivel.isBlank() || e.nivel().equalsIgnoreCase(nivel)) {
                out.add(e);
                if (out.size() >= max) {
                    break;
                }
            }
        }
        return out;
    }

    public long ultimas24h(String nivel) {
        Instant desde = Instant.now().minusSeconds(86_400);
        return buffer.stream().filter(e -> e.fecha().isAfter(desde) && e.nivel().equalsIgnoreCase(nivel)).count();
    }

    private static String recortar(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) + "…" : s);
    }
}
