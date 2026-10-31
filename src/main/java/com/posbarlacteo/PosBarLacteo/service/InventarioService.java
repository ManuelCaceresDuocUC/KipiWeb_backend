package com.posbarlacteo.PosBarLacteo.service;

import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.exception.AccesoDenegadoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.model.MovimientoInventario;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.InventarioSucursalRepository;
import com.posbarlacteo.PosBarLacteo.repository.MovimientoInventarioRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.repository.SucursalRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/**
 * Único punto donde cambia el stock. Catálogo por empresa, stock por sucursal.
 * Cada cambio es atómico en la base (UPDATE condicionado) y deja una fila en el historial.
 */
@Service
public class InventarioService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final InventarioSucursalRepository inventario;
    private final MovimientoInventarioRepository movimientos;
    private final ProductoRepository productos;
    private final SucursalRepository sucursales;
    private final EmpresaRepository empresas;

    public InventarioService(InventarioSucursalRepository inventario, MovimientoInventarioRepository movimientos,
                             ProductoRepository productos, SucursalRepository sucursales, EmpresaRepository empresas) {
        this.inventario = inventario;
        this.movimientos = movimientos;
        this.productos = productos;
        this.sucursales = sucursales;
        this.empresas = empresas;
    }

    // ---------- política de la empresa ----------

    /** null (empresas anteriores a esta política) se trata como "permitido", que era el comportamiento histórico. */
    @Transactional(readOnly = true)
    public boolean permiteStockNegativo(Long empresaId) {
        return empresas.findById(empresaId)
                .map(e -> e.getPermiteStockNegativo() == null || e.getPermiteStockNegativo())
                .orElse(true);
    }

    @Transactional
    public void definirPermiteStockNegativo(Long empresaId, boolean valor) {
        Empresa e = empresas.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        e.setPermiteStockNegativo(valor);
        empresas.save(e);
    }

    // ---------- sucursal efectiva ----------

    /** Vendedor: siempre su sucursal. Admin: la suya o cualquiera de SU empresa. */
    public Long resolverSucursal(AuthPrincipal p, Long solicitada) {
        if (solicitada == null || solicitada.equals(p.sucursalId())) {
            if (p.sucursalId() == null) {
                throw new NegocioException("Tu usuario no tiene una sucursal asignada.");
            }
            return p.sucursalId();
        }
        if (!p.esAdmin()) {
            throw new AccesoDenegadoException("No puedes ver el inventario de otra sucursal.");
        }
        return sucursales.findByIdAndEmpresaId(solicitada, p.empresaId())
                .orElseThrow(() -> new NegocioException("La sucursal no existe."))
                .getId();
    }

    // ---------- lectura ----------

    /** Completa Producto.stock con el stock de esa sucursal (0 si nunca tuvo movimiento). */
    @Transactional(readOnly = true)
    public void poblarStock(Collection<Producto> lista, Long sucursalId) {
        if (lista.isEmpty()) return;
        List<Long> ids = lista.stream().map(Producto::getId).toList();
        Map<Long, Double> mapa = new HashMap<>();
        for (Object[] fila : inventario.stocksDe(sucursalId, ids)) {
            mapa.put((Long) fila[0], (Double) fila[1]);
        }
        lista.forEach(pr -> pr.setStock(mapa.getOrDefault(pr.getId(), 0.0)));
    }

    // ---------- escritura ----------

    /**
     * Descuenta lo consumido por una venta. consumo: productoId → cantidad (ya expandida por recetas).
     * Se procesa en orden de id para evitar interbloqueos entre ventas simultáneas.
     */
    @Transactional
    public void descontarVenta(Long empresaId, Long sucursalId, Long ventaId, String usuario,
                               Map<Long, Double> consumo, Map<Long, String> nombres) {
        boolean permiteNeg = permiteStockNegativo(empresaId);
        new java.util.TreeMap<>(consumo).forEach((productoId, cantidad) ->
                aplicar(empresaId, sucursalId, productoId, nombres.get(productoId), -cantidad, permiteNeg,
                        MovimientoInventario.VENTA, "Venta N° " + ventaId, usuario, ventaId));
    }

    /**
     * Anulación de una venta: repone exactamente lo que descontó (leído del historial, no recalculado
     * desde las recetas actuales). Ventas anteriores al stock por sucursal no tienen historial y no reponen nada.
     */
    @Transactional
    public void revertirVenta(AuthPrincipal p, Long ventaId, String motivo) {
        List<MovimientoInventario> originales =
                new java.util.ArrayList<>(movimientos.porVenta(p.empresaId(), ventaId, MovimientoInventario.VENTA));
        originales.sort(java.util.Comparator.comparing(m -> m.getProducto().getId()));
        for (MovimientoInventario m : originales) {
            aplicar(p.empresaId(), m.getSucursal().getId(), m.getProducto().getId(),
                    m.getProducto().getDescripcion(), -m.getCantidad(), true,
                    MovimientoInventario.ANULACION, motivo, p.usuario(), ventaId);
        }
    }

    /** Stock inicial al crear un producto (no pasa por la política: es una carga, siempre positiva). */
    @Transactional
    public void cargarStockInicial(AuthPrincipal p, Producto producto, double cantidad) {
        if (cantidad <= 0) return;
        Long sucursalId = resolverSucursal(p, null);
        aplicar(p.empresaId(), sucursalId, producto.getId(), producto.getDescripcion(), cantidad, true,
                MovimientoInventario.INICIAL, "Stock inicial", p.usuario(), null);
    }

    /** Movimiento manual: ENTRADA (+), MERMA (−) o AJUSTE (cantidad = conteo físico). Devuelve el stock resultante. */
    @Transactional
    public double registrarManual(AuthPrincipal p, Long sucursalSolicitada, Long productoId,
                                  String tipo, double cantidad, String motivo) {
        if (Double.isNaN(cantidad) || Double.isInfinite(cantidad)) {
            throw new NegocioException("La cantidad no es válida.");
        }
        Producto producto = productos.findByIdAndEmpresaId(productoId, p.empresaId())
                .orElseThrow(() -> new NegocioException("El producto no existe."));
        Long sucursalId = resolverSucursal(p, sucursalSolicitada);
        String t = tipo == null ? "" : tipo.trim().toUpperCase();
        String nota = motivo == null || motivo.isBlank() ? null : motivo.trim();

        switch (t) {
            case MovimientoInventario.ENTRADA -> {
                if (cantidad <= 0) throw new NegocioException("La cantidad de la entrada debe ser mayor a cero.");
                return aplicar(p.empresaId(), sucursalId, productoId, producto.getDescripcion(), cantidad, true,
                        t, nota != null ? nota : "Ingreso de mercadería", p.usuario(), null);
            }
            case MovimientoInventario.MERMA -> {
                if (cantidad <= 0) throw new NegocioException("La cantidad de la merma debe ser mayor a cero.");
                if (nota == null) throw new NegocioException("Indica el motivo de la merma.");
                return aplicar(p.empresaId(), sucursalId, productoId, producto.getDescripcion(), -cantidad,
                        permiteStockNegativo(p.empresaId()), t, nota, p.usuario(), null);
            }
            case MovimientoInventario.AJUSTE -> {
                if (cantidad < 0) throw new NegocioException("El conteo físico no puede ser negativo.");
                if (nota == null) throw new NegocioException("Indica el motivo del ajuste.");
                inventario.crearFilaSiNoExiste(productoId, sucursalId);
                double antes = inventario.stockActual(productoId, sucursalId).orElse(0.0);
                inventario.fijarStock(productoId, sucursalId, cantidad);
                guardarMovimiento(p.empresaId(), sucursalId, productoId, t, cantidad - antes, cantidad, nota, p.usuario(), null);
                return cantidad;
            }
            default -> throw new NegocioException("Tipo de movimiento inválido. Usa ENTRADA, MERMA o AJUSTE.");
        }
    }

    // ---------- historial ----------

    @Transactional(readOnly = true)
    public Map<String, Object> listarMovimientos(AuthPrincipal p, Long sucursalSolicitada, Long productoId,
                                                 int page, int size) {
        // Admin sin filtro de sucursal ve todas las de su empresa
        Long filtroSucursal = sucursalSolicitada == null && p.esAdmin() ? null : resolverSucursal(p, sucursalSolicitada);
        Page<MovimientoInventario> pagina = movimientos.buscar(p.empresaId(), filtroSucursal, productoId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));

        List<Map<String, Object>> filas = pagina.getContent().stream().map(m -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", m.getId());
            f.put("fecha", FECHA.format(m.getFechaHora()));
            f.put("tipo", m.getTipo());
            f.put("cantidad", m.getCantidad());
            f.put("stockResultante", m.getStockResultante());
            f.put("motivo", m.getMotivo());
            f.put("producto", m.getProducto().getDescripcion());
            f.put("sucursal", m.getSucursal().getNombre());
            f.put("usuario", m.getUsuarioNombre());
            return f;
        }).toList();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("content", filas);
        r.put("totalPages", pagina.getTotalPages());
        r.put("totalElements", pagina.getTotalElements());
        return r;
    }

    // ---------- núcleo atómico ----------

    private double aplicar(Long empresaId, Long sucursalId, Long productoId, String nombre, double delta,
                           boolean permiteNeg, String tipo, String motivo, String usuario, Long ventaId) {
        inventario.crearFilaSiNoExiste(productoId, sucursalId);
        int filas = (delta >= 0 || permiteNeg)
                ? inventario.aplicarDelta(productoId, sucursalId, delta)
                : inventario.aplicarDeltaConPiso(productoId, sucursalId, delta);
        if (filas == 0) {
            throw new NegocioException("Stock insuficiente de \"" + (nombre != null ? nombre : productoId)
                    + "\" en esta sucursal.");
        }
        double nuevo = inventario.stockActual(productoId, sucursalId).orElse(0.0);
        guardarMovimiento(empresaId, sucursalId, productoId, tipo, delta, nuevo, motivo, usuario, ventaId);
        return nuevo;
    }

    private void guardarMovimiento(Long empresaId, Long sucursalId, Long productoId, String tipo, double delta,
                                   double stockResultante, String motivo, String usuario, Long ventaId) {
        MovimientoInventario m = new MovimientoInventario();
        m.setEmpresa(empresas.getReferenceById(empresaId));
        m.setSucursal(sucursales.getReferenceById(sucursalId));
        m.setProducto(productos.getReferenceById(productoId));
        m.setTipo(tipo);
        m.setCantidad(delta);
        m.setStockResultante(stockResultante);
        m.setMotivo(motivo);
        m.setUsuarioNombre(usuario);
        m.setVentaId(ventaId);
        movimientos.save(m);
    }
}
