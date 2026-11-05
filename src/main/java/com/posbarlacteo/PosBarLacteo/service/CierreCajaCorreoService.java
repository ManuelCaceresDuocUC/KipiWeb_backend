package com.posbarlacteo.PosBarLacteo.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.TurnoCaja;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.repository.AnalisisVentasRepository;
import com.posbarlacteo.PosBarLacteo.repository.TurnoCajaRepository;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/**
 * Envía el resumen del cierre de caja a todos los administradores de la empresa. Se manda DESPUÉS de guardar el
 * cierre y en segundo plano: si el correo falla, el cierre ya quedó hecho y solo se deja el aviso en el log.
 */
@Service
public class CierreCajaCorreoService {

    private static final Logger log = LoggerFactory.getLogger(CierreCajaCorreoService.class);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale CL = Locale.forLanguageTag("es-CL");

    private final TurnoCajaRepository turnos;
    private final UsuarioRepository usuarios;
    private final AnalisisVentasRepository ventas;
    private final CorreosEmpresa correos;
    private final MailService mail;
    private final TransactionTemplate tx;
    private final boolean habilitado;

    public CierreCajaCorreoService(TurnoCajaRepository turnos, UsuarioRepository usuarios, AnalisisVentasRepository ventas,
                                   CorreosEmpresa correos, MailService mail, PlatformTransactionManager tm,
                                   @Value("${caja.correo-cierre-habilitado:true}") boolean habilitado) {
        this.turnos = turnos;
        this.usuarios = usuarios;
        this.ventas = ventas;
        this.correos = correos;
        this.mail = mail;
        this.tx = new TransactionTemplate(tm);
        this.tx.setReadOnly(true);
        this.habilitado = habilitado;
    }

    /** Manda el resumen a todos los administradores, sin bloquear la respuesta del cierre. */
    public void enviarAsync(Long turnoId) {
        if (!habilitado || turnoId == null || !mail.habilitado()) return;
        CompletableFuture.runAsync(() -> {
            try {
                Resumen r = tx.execute(st -> armar(turnoId));
                if (r == null) return;
                List<String> destinos = correos.administradores(r.empresaId);
                int enviados = 0;
                for (String d : destinos) {
                    try {
                        mail.enviarTexto(d, r.asunto, r.cuerpo, null);
                        enviados++;
                    } catch (RuntimeException e) {
                        log.warn("No se pudo enviar el cierre del turno {} a {}: {}", turnoId, d, e.getMessage());
                    }
                }
                log.info("Cierre del turno {} enviado a {} de {} administradores", turnoId, enviados, destinos.size());
            } catch (RuntimeException e) {
                log.warn("No se pudo preparar el correo de cierre del turno {}: {}", turnoId, e.getMessage());
            }
        });
    }

    /** Reenvía un cierre al correo del administrador que lo pide. Devuelve la dirección usada. */
    public String reenviar(AuthPrincipal p, Long turnoId) {
        if (!mail.habilitado()) throw new NegocioException("El envío de correos no está configurado en el servidor.");
        Usuario u = usuarios.findById(p.usuarioId()).orElseThrow(() -> new NegocioException("Usuario no encontrado."));
        String destino = u.getCorreo() == null ? "" : u.getCorreo().trim();
        if (destino.isEmpty() || destino.toLowerCase().endsWith(".local")) {
            throw new NegocioException("Tu usuario no tiene un correo válido para recibir el cierre.");
        }
        TurnoCaja t = turnos.findById(turnoId).orElseThrow(() -> new NegocioException("El turno no existe."));
        if (t.getEmpresa() == null || !t.getEmpresa().getId().equals(p.empresaId())) throw new NegocioException("El turno no existe.");
        if (!"CERRADA".equals(t.getEstado())) throw new NegocioException("El turno aún está abierto.");
        Resumen r = tx.execute(st -> armar(turnoId));
        if (r == null) throw new NegocioException("No se pudo preparar el resumen.");
        mail.enviarTexto(destino, r.asunto, r.cuerpo, null);
        return destino;
    }

    // ------------------------------------------------------------------------------------------

    private record Resumen(Long empresaId, String asunto, String cuerpo) { }

    private Resumen armar(Long turnoId) {
        TurnoCaja t = turnos.findById(turnoId).orElse(null);
        if (t == null || t.getEmpresa() == null || !"CERRADA".equals(t.getEstado())) return null;

        String empresa = t.getEmpresa().getRazonSocial() == null || t.getEmpresa().getRazonSocial().isBlank()
                ? "KIPIPOS" : t.getEmpresa().getRazonSocial();
        String sucursal = t.getSucursal() == null ? "Principal" : t.getSucursal().getNombre();
        String cajero = usuarios.findById(t.getCajeroId()).map(Usuario::getUsuario).orElse("—");

        long cantVentas = 0;
        double totalVentas = 0;
        List<Object[]> v = ventas.ventasDelTurno(turnoId);
        if (!v.isEmpty() && v.get(0)[0] != null) {
            cantVentas = ((Number) v.get(0)[0]).longValue();
            totalVentas = ((Number) v.get(0)[1]).doubleValue();
        }
        Long anuladas = ventas.anuladasDelTurno(turnoId);

        BigDecimal dif = nz(t.getDiferencia());
        String difTexto = dif.signum() == 0 ? "Cuadra exacto" : dif.signum() < 0 ? "FALTAN " + clp(dif.abs()) : "SOBRAN " + clp(dif);
        LocalDateTime cierre = t.getFechaCierre() == null ? LocalDateTime.now() : t.getFechaCierre();

        String asunto = "Cierre de caja · " + sucursal + " · " + cierre.format(FECHA_HORA) + " · " + difTexto;
        StringBuilder b = new StringBuilder();
        b.append("Cierre de caja — ").append(empresa).append("\n\n");
        b.append("Sucursal: ").append(sucursal).append('\n');
        b.append("Cajero: ").append(cajero).append('\n');
        b.append("Turno N° ").append(t.getId()).append('\n');
        if (t.getFechaApertura() != null) b.append("Abrió: ").append(t.getFechaApertura().format(FECHA_HORA)).append('\n');
        b.append("Cerró: ").append(cierre.format(FECHA_HORA)).append("\n\n");

        b.append("VENTAS DEL TURNO\n");
        b.append("N° de ventas: ").append(cantVentas);
        if (anuladas != null && anuladas > 0) b.append(" (").append(anuladas).append(" anuladas)");
        b.append('\n');
        b.append("Total vendido: ").append(clp(totalVentas)).append('\n');
        b.append("  Efectivo: ").append(clp(t.getVentasEfectivo())).append('\n');
        b.append("  Tarjeta: ").append(clp(t.getVentasTarjeta())).append('\n');
        b.append("  Transferencia: ").append(clp(t.getVentasTransferencia())).append('\n');
        b.append("  A crédito (fiado): ").append(clp(t.getVentasCredito())).append("\n\n");

        b.append("OTROS MOVIMIENTOS DE CAJA\n");
        b.append("Ingresos extra: ").append(clp(t.getIngresosExtra())).append('\n');
        b.append("Abonos de clientes en efectivo: ").append(clp(t.getAbonosCredito())).append('\n');
        b.append("Retiros: ").append(clp(t.getRetiros())).append("\n\n");

        b.append("CUADRATURA DEL EFECTIVO\n");
        b.append("Fondo inicial: ").append(clp(t.getMontoApertura())).append('\n');
        b.append("Efectivo esperado: ").append(clp(t.getTotalSistema())).append('\n');
        b.append("Efectivo contado: ").append(clp(t.getTotalRealFisico())).append('\n');
        b.append("Resultado: ").append(difTexto).append("\n\n");

        b.append("Este resumen se envía automáticamente a los administradores de ").append(empresa)
         .append(" cada vez que se cierra una caja.\n");
        return new Resumen(t.getEmpresa().getId(), asunto, b.toString());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String clp(BigDecimal v) {
        return clp(nz(v).doubleValue());
    }

    private static String clp(double v) {
        return "$" + NumberFormat.getIntegerInstance(CL).format(Math.round(v));
    }
}
