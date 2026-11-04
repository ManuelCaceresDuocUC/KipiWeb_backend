package com.posbarlacteo.PosBarLacteo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.Cliente;
import com.posbarlacteo.PosBarLacteo.model.Devolucion;
import com.posbarlacteo.PosBarLacteo.model.DevolucionItem;
import com.posbarlacteo.PosBarLacteo.model.PagoVenta;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;
import com.posbarlacteo.PosBarLacteo.repository.ClienteRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.util.CsvUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static com.posbarlacteo.PosBarLacteo.util.CsvUtil.f;
import static com.posbarlacteo.PosBarLacteo.util.CsvUtil.fila;
import static com.posbarlacteo.PosBarLacteo.util.CsvUtil.n;
import static com.posbarlacteo.PosBarLacteo.util.CsvUtil.si;
import static com.posbarlacteo.PosBarLacteo.util.CsvUtil.t;

/** Exportación de los datos de la empresa a CSV (solo administrador). Todo se filtra por la empresa del token. */
@Service
public class ExportacionService {

    private static final int MAX_DIAS = 366;
    private static final int MAX_VENTAS = 20_000;
    private static final int MAX_MOVIMIENTOS = 20_000;
    private static final int LOTE_IN = 500;

    @PersistenceContext
    private EntityManager em;

    private final ProductoRepository productos;
    private final ClienteRepository clientes;
    private final InventarioService inventario;

    public ExportacionService(ProductoRepository productos, ClienteRepository clientes, InventarioService inventario) {
        this.productos = productos;
        this.clientes = clientes;
        this.inventario = inventario;
    }

    /** Productos con las mismas columnas que acepta la importación: se puede editar en Excel y volver a importar. */
    @Transactional(readOnly = true)
    public String productos(AuthPrincipal p) {
        List<Producto> lista = productos.catalogoCompleto(p.empresaId());
        if (p.sucursalId() != null) {
            inventario.poblarStock(lista, inventario.resolverSucursal(p, null));
        } else {
            lista.forEach(x -> x.setStock(0.0));
        }
        StringBuilder sb = new StringBuilder(CsvUtil.BOM);
        fila(sb, "Código de barras", "Nombre", "Categoría", "Precio", "Costo", "Stock", "Stock crítico", "Unidad", "Granel", "Tipo");
        for (Producto x : lista) {
            fila(sb, t(x.getCodigoBarras()), t(x.getDescripcion()), t(x.getCategoria() != null ? x.getCategoria().getNombre() : ""),
                    n(x.getPrecio()), n(x.getCosto()), n(x.getStock()), n(x.getStockCritico()),
                    t(x.getUnidadMedida()), si(x.getPesable()), Boolean.TRUE.equals(x.getEsInsumo()) ? "INSUMO" : "VENTA");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String clientes(AuthPrincipal p) {
        StringBuilder sb = new StringBuilder(CsvUtil.BOM);
        fila(sb, "Nombre", "RUT", "Teléfono", "Correo", "Límite de crédito", "Deuda actual");
        for (Cliente c : clientes.findByEmpresaId(p.empresaId())) {
            fila(sb, t(c.getNombre()), t(c.getRut()), t(c.getTelefono()), t(c.getEmail()),
                    n(c.getLimiteCredito()), n(c.getDeudaActual()));
        }
        return sb.toString();
    }

    /** Una fila por producto vendido. El total de la venta va solo en la primera línea para no sumarlo dos veces. */
    @Transactional(readOnly = true)
    public String ventas(AuthPrincipal p, LocalDate desde, LocalDate hasta) {
        LocalDateTime d = rangoDesde(desde, hasta), h = hasta.plusDays(1).atStartOfDay();
        List<Venta> ventas = em.createQuery(
                "SELECT v FROM Venta v LEFT JOIN FETCH v.cliente LEFT JOIN FETCH v.usuario "
              + "WHERE v.empresa.id = :e AND v.fechaHora >= :d AND v.fechaHora < :h ORDER BY v.id", Venta.class)
                .setParameter("e", p.empresaId()).setParameter("d", d).setParameter("h", h)
                .setMaxResults(MAX_VENTAS + 1).getResultList();
        if (ventas.size() > MAX_VENTAS) {
            throw new NegocioException("Hay más de " + MAX_VENTAS + " ventas en ese período. Elige un rango más corto.");
        }

        Map<Long, List<VentaDetalle>> detalles = new LinkedHashMap<>();
        Map<Long, List<PagoVenta>> pagos = new LinkedHashMap<>();
        List<Long> ids = ventas.stream().map(Venta::getId).collect(Collectors.toList());
        for (int i = 0; i < ids.size(); i += LOTE_IN) {
            List<Long> lote = ids.subList(i, Math.min(i + LOTE_IN, ids.size()));
            em.createQuery("SELECT x FROM VentaDetalle x JOIN FETCH x.producto WHERE x.venta.id IN :ids ORDER BY x.id", VentaDetalle.class)
                    .setParameter("ids", lote).getResultList()
                    .forEach(x -> detalles.computeIfAbsent(x.getVenta().getId(), k -> new ArrayList<>()).add(x));
            em.createQuery("SELECT x FROM PagoVenta x WHERE x.venta.id IN :ids ORDER BY x.id", PagoVenta.class)
                    .setParameter("ids", lote).getResultList()
                    .forEach(x -> pagos.computeIfAbsent(x.getVenta().getId(), k -> new ArrayList<>()).add(x));
        }

        StringBuilder sb = new StringBuilder(CsvUtil.BOM);
        fila(sb, "Venta N°", "Fecha", "Estado", "Cajero", "Cliente", "Código de barras", "Producto", "Cantidad", "Unidad",
                "Precio unitario", "Subtotal línea", "Total venta (solo 1ª línea)", "Medios de pago", "Boleta", "Folio boleta", "Venta sin conexión");
        for (Venta v : ventas) {
            String medios = pagos.getOrDefault(v.getId(), List.of()).stream()
                    .map(x -> x.getMetodo() + " " + (x.getMonto() == null ? 0 : x.getMonto()))
                    .collect(Collectors.joining(" + "));
            List<VentaDetalle> lineas = detalles.getOrDefault(v.getId(), List.of());
            boolean primera = true;
            for (VentaDetalle x : lineas) {
                double precio = x.getPrecioUnitario() == null ? 0 : x.getPrecioUnitario();
                double cant = x.getCantidad() == null ? 0 : x.getCantidad();
                fila(sb, n(v.getId()), f(v.getFechaHora()), t(v.getEstado()),
                        t(v.getUsuario() != null ? v.getUsuario().getUsuario() : ""),
                        t(v.getCliente() != null ? v.getCliente().getNombre() : ""),
                        t(x.getProducto().getCodigoBarras()), t(x.getProducto().getDescripcion()), n(cant),
                        t(x.getProducto().getUnidadMedida()), n(precio), n((double) Math.round(precio * cant)),
                        primera ? n(v.getTotal()) : "", t(medios), t(v.getDteEstado()), n(v.getDteFolio()),
                        si(v.getDiferida()));
                primera = false;
            }
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String devoluciones(AuthPrincipal p, LocalDate desde, LocalDate hasta) {
        LocalDateTime d = rangoDesde(desde, hasta), h = hasta.plusDays(1).atStartOfDay();
        List<Devolucion> lista = em.createQuery(
                "SELECT x FROM Devolucion x WHERE x.empresaId = :e AND x.fechaHora >= :d AND x.fechaHora < :h ORDER BY x.id", Devolucion.class)
                .setParameter("e", p.empresaId()).setParameter("d", d).setParameter("h", h).getResultList();
        StringBuilder sb = new StringBuilder(CsvUtil.BOM);
        fila(sb, "Fecha", "Venta N°", "Tipo", "Producto", "Cantidad", "Precio unitario", "Motivo", "Cambio entregado", "Repuso stock", "Registrado por");
        for (Devolucion x : lista) {
            for (DevolucionItem i : x.getItems()) {
                fila(sb, f(x.getFechaHora()), n(x.getVentaId()), t(x.getTipo()), t(i.getDescripcion()), n(i.getCantidad()),
                        n(i.getPrecioUnitario()), t(x.getMotivo()), t(x.getNota()), si(x.getReponeStock()), t(x.getUsuario()));
            }
        }
        return sb.toString();
    }

    /** Historial de movimientos de inventario (entradas, mermas, ajustes, ventas), los más recientes primero. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public String movimientosInventario(AuthPrincipal p) {
        StringBuilder sb = new StringBuilder(CsvUtil.BOM);
        fila(sb, "Fecha", "Tipo", "Producto", "Sucursal", "Cantidad", "Stock resultante", "Motivo", "Usuario");
        int filas = 0;
        for (int pagina = 0; filas < MAX_MOVIMIENTOS; pagina++) {
            Map<String, Object> r = inventario.listarMovimientos(p, null, null, pagina, 200);
            List<Map<String, Object>> contenido = (List<Map<String, Object>>) r.get("content");
            if (contenido == null || contenido.isEmpty()) break;
            for (Map<String, Object> m : contenido) {
                fila(sb, t(String.valueOf(m.get("fecha")).replace('T', ' ')), t(str(m.get("tipo"))), t(str(m.get("producto"))),
                        t(str(m.get("sucursal"))), n(num(m.get("cantidad"))), n(num(m.get("stockResultante"))),
                        t(str(m.get("motivo"))), t(str(m.get("usuario"))));
                filas++;
            }
            if (pagina + 1 >= ((Number) r.get("totalPages")).intValue()) break;
        }
        return sb.toString();
    }

    private static LocalDateTime rangoDesde(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new NegocioException("Indica el período (desde y hasta).");
        if (hasta.isBefore(desde)) throw new NegocioException("La fecha final es anterior a la inicial.");
        if (desde.plusDays(MAX_DIAS).isBefore(hasta)) throw new NegocioException("El período no puede superar un año.");
        return desde.atStartOfDay();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Double num(Object o) {
        return o instanceof Number x ? x.doubleValue() : null;
    }
}
