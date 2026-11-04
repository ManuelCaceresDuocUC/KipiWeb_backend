package com.posbarlacteo.PosBarLacteo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.exception.AccesoDenegadoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Devolucion;
import com.posbarlacteo.PosBarLacteo.model.DevolucionItem;
import com.posbarlacteo.PosBarLacteo.model.MovimientoInventario;
import com.posbarlacteo.PosBarLacteo.model.Receta;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;
import com.posbarlacteo.PosBarLacteo.repository.DevolucionRepository;
import com.posbarlacteo.PosBarLacteo.repository.RecetaRepository;
import com.posbarlacteo.PosBarLacteo.repository.VentaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/**
 * Devoluciones y cambios: se registra qué volvió y por qué. NO modifica la caja, la deuda del cliente ni los
 * documentos tributarios (si la venta tenía boleta, la nota de crédito, si corresponde, se gestiona aparte).
 */
@Service
public class DevolucionService {

    private static final double EPS = 1e-6;

    private final DevolucionRepository devoluciones;
    private final VentaRepository ventas;
    private final RecetaRepository recetas;
    private final InventarioService inventario;
    private final AuditoriaService auditoria;

    public DevolucionService(DevolucionRepository devoluciones, VentaRepository ventas, RecetaRepository recetas,
                             InventarioService inventario, AuditoriaService auditoria) {
        this.devoluciones = devoluciones;
        this.ventas = ventas;
        this.recetas = recetas;
        this.inventario = inventario;
        this.auditoria = auditoria;
    }

    public record LineaDevolucion(Long productoId, Double cantidad) {
    }

    @Transactional
    public Devolucion registrar(AuthPrincipal p, Long ventaId, String tipo, String motivo, String nota,
                                boolean reponerStock, List<LineaDevolucion> lineas) {
        String t = tipo == null ? "" : tipo.trim().toUpperCase();
        if (!Devolucion.DEVOLUCION.equals(t) && !Devolucion.CAMBIO.equals(t)) {
            throw new NegocioException("Indica si es una devolución o un cambio.");
        }
        String m = motivo == null ? "" : motivo.trim();
        if (m.length() < 3) {
            throw new NegocioException("Escribe el motivo (mínimo 3 letras).");
        }
        if (m.length() > 255) {
            throw new NegocioException("El motivo no puede superar los 255 caracteres.");
        }
        String n = nota == null ? "" : nota.trim();
        if (n.length() > 255) {
            throw new NegocioException("La nota no puede superar los 255 caracteres.");
        }
        if (lineas == null || lineas.isEmpty()) {
            throw new NegocioException("Selecciona al menos un producto devuelto.");
        }
        if (reponerStock && !p.esAdmin()) {
            throw new AccesoDenegadoException("Solo un administrador puede reponer el stock al registrar una devolución.");
        }

        Venta venta = ventas.findByIdAndEmpresaId(ventaId, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada."));
        if ("ANULADA".equals(venta.getEstado())) {
            throw new NegocioException("La venta está anulada: no admite devoluciones.");
        }

        // Lo vendido por producto (con el precio al que se vendió) y lo ya devuelto antes
        Map<Long, Double> vendido = new HashMap<>();
        Map<Long, VentaDetalle> detalleDe = new HashMap<>();
        for (VentaDetalle d : venta.getDetalles()) {
            if (d.getProducto() == null) continue;
            vendido.merge(d.getProducto().getId(), d.getCantidad() == null ? 0.0 : d.getCantidad(), Double::sum);
            detalleDe.putIfAbsent(d.getProducto().getId(), d);
        }
        Map<Long, Double> yaDevuelto = new HashMap<>();
        for (Object[] fila : devoluciones.devueltoPorProducto(p.empresaId(), ventaId)) {
            yaDevuelto.put((Long) fila[0], ((Number) fila[1]).doubleValue());
        }

        Devolucion dev = new Devolucion();
        dev.setEmpresaId(p.empresaId());
        dev.setVentaId(ventaId);
        dev.setTipo(t);
        dev.setMotivo(m);
        dev.setNota(n.isEmpty() ? null : n);
        dev.setReponeStock(reponerStock);
        dev.setUsuario(p.usuario());

        Map<Long, Double> porProducto = new HashMap<>();
        for (LineaDevolucion l : lineas) {
            if (l == null || l.productoId() == null || l.cantidad() == null || l.cantidad() <= 0
                    || l.cantidad().isNaN() || l.cantidad().isInfinite()) {
                throw new NegocioException("Hay un producto con cantidad inválida.");
            }
            porProducto.merge(l.productoId(), Math.round(l.cantidad() * 1000d) / 1000d, Double::sum);
        }

        double monto = 0;
        List<String> resumen = new ArrayList<>();
        for (Map.Entry<Long, Double> e : porProducto.entrySet()) {
            Long productoId = e.getKey();
            double cant = e.getValue();
            VentaDetalle d = detalleDe.get(productoId);
            if (d == null) {
                throw new NegocioException("Uno de los productos no pertenece a esta venta.");
            }
            double disponible = vendido.get(productoId) - yaDevuelto.getOrDefault(productoId, 0.0);
            if (cant > disponible + EPS) {
                throw new NegocioException("De \"" + d.getProducto().getDescripcion() + "\" solo quedan "
                        + formato(disponible) + " por devolver (se vendieron " + formato(vendido.get(productoId)) + ").");
            }
            double precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : 0.0;
            DevolucionItem item = new DevolucionItem();
            item.setDevolucion(dev);
            item.setProductoId(productoId);
            item.setDescripcion(d.getProducto().getDescripcion());
            item.setCantidad(cant);
            item.setPrecioUnitario(precio);
            dev.getItems().add(item);
            monto += Math.round(precio * cant);
            resumen.add(formato(cant) + " x " + d.getProducto().getDescripcion());
        }
        dev.setMontoReferencial(monto);
        Devolucion guardada = devoluciones.save(dev);

        if (reponerStock) {
            reponer(p, ventaId, porProducto, detalleDe);
        }

        auditoria.registrar(p, "DEVOLUCION", t + " venta N° " + ventaId + ": " + String.join(", ", resumen)
                + (reponerStock ? " (con reposición de stock)" : "") + ". Motivo: " + m);
        return guardada;
    }

    /** Devuelve al inventario lo devuelto; si el producto lleva receta, repone sus insumos. */
    private void reponer(AuthPrincipal p, Long ventaId, Map<Long, Double> porProducto, Map<Long, VentaDetalle> detalleDe) {
        String motivo = "Devolución de la venta N° " + ventaId;
        for (Map.Entry<Long, Double> e : new java.util.TreeMap<>(porProducto).entrySet()) {
            List<Receta> ingredientes = recetas.findByProductoPrincipalId(e.getKey());
            if (ingredientes.isEmpty()) {
                inventario.registrarManual(p, null, e.getKey(), MovimientoInventario.ENTRADA, e.getValue(), motivo);
            } else {
                for (Receta r : ingredientes) {
                    inventario.registrarManual(p, null, r.getInsumo().getId(), MovimientoInventario.ENTRADA,
                            r.getCantidadUsada() * e.getValue(), motivo);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Devolucion> deLaVenta(AuthPrincipal p, Long ventaId) {
        ventas.findByIdAndEmpresaId(ventaId, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada."));
        List<Devolucion> lista = devoluciones.porVenta(p.empresaId(), ventaId);
        lista.forEach(d -> d.getItems().size()); // carga los ítems dentro de la transacción
        return lista;
    }

    @Transactional(readOnly = true)
    public List<Devolucion> recientes(AuthPrincipal p) {
        List<Devolucion> lista = devoluciones.recientes(p.empresaId(), PageRequest.of(0, 100));
        lista.forEach(d -> d.getItems().size());
        return lista;
    }

    private static String formato(double c) {
        return c == Math.rint(c) ? String.valueOf((long) c) : String.format(java.util.Locale.ROOT, "%.3f", c).replaceAll("0+$", "");
    }
}
