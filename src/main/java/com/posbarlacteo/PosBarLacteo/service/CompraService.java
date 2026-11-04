package com.posbarlacteo.PosBarLacteo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.Compra;
import com.posbarlacteo.PosBarLacteo.model.CompraItem;
import com.posbarlacteo.PosBarLacteo.model.CompraPago;
import com.posbarlacteo.PosBarLacteo.model.MovimientoInventario;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Proveedor;
import com.posbarlacteo.PosBarLacteo.repository.CompraPagoRepository;
import com.posbarlacteo.PosBarLacteo.repository.CompraRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProveedorRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/**
 * Compras a proveedores: registro del documento, ingreso al stock, actualización del costo (último costo pagado),
 * cuentas por pagar y anulación. Solo administradores (lo exige SecurityConfig).
 */
@Service
public class CompraService {

    static final Set<String> TIPOS = Set.of("FACTURA", "FACTURA_EXENTA", "BOLETA", "NOTA_VENTA", "GUIA", "OTRO");
    static final Set<String> METODOS = Set.of("EFECTIVO", "TRANSFERENCIA", "OTRO");
    private static final double IVA = 0.19;
    private static final long MAX_PESOS = 2_000_000_000L;

    private final CompraRepository compras;
    private final CompraPagoRepository pagos;
    private final ProveedorRepository proveedores;
    private final ProductoRepository productos;
    private final InventarioService inventario;
    private final AuditoriaService auditoria;
    private final ComprobanteService archivos;

    public CompraService(CompraRepository compras, CompraPagoRepository pagos, ProveedorRepository proveedores,
                         ProductoRepository productos, InventarioService inventario, AuditoriaService auditoria,
                         ComprobanteService archivos) {
        this.compras = compras;
        this.pagos = pagos;
        this.proveedores = proveedores;
        this.productos = productos;
        this.inventario = inventario;
        this.auditoria = auditoria;
        this.archivos = archivos;
    }

    // ------------------------------------------------------------------------------------------
    // Peticiones
    // ------------------------------------------------------------------------------------------

    public record LineaCompra(Long productoId, Double cantidad, Long costoUnitario) { }

    public record NuevaCompra(Long proveedorId, String tipoDocumento, String folio, LocalDate fechaDocumento,
                              LocalDate vencimiento, Boolean preciosNetos, Long totalDocumento,
                              Boolean ingresarStock, Boolean actualizarCostos, Boolean pagada, String metodoPago,
                              String nota, List<LineaCompra> items) { }

    public record NuevoPago(Long monto, String metodo, String nota, LocalDate fecha) { }

    public record DatosProveedor(String nombre, String rut, String telefono, String email, String notas) { }

    // ------------------------------------------------------------------------------------------
    // Proveedores
    // ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarProveedores(AuthPrincipal p) {
        Map<Long, Long> comprado = new HashMap<>();
        for (Object[] f : compras.totalesPorProveedor(p.empresaId())) comprado.put((Long) f[0], ((Number) f[1]).longValue());
        Map<Long, Long> pagado = new HashMap<>();
        for (Object[] f : pagos.pagadoPorProveedor(p.empresaId())) pagado.put((Long) f[0], ((Number) f[1]).longValue());
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Proveedor pr : proveedores.activos(p.empresaId())) {
            Map<String, Object> m = mapaProveedor(pr);
            long total = comprado.getOrDefault(pr.getId(), 0L);
            m.put("totalComprado", total);
            m.put("adeudado", Math.max(total - pagado.getOrDefault(pr.getId(), 0L), 0L));
            salida.add(m);
        }
        return salida;
    }

    @Transactional
    public Map<String, Object> crearProveedor(AuthPrincipal p, DatosProveedor d) {
        Proveedor pr = new Proveedor();
        pr.setEmpresaId(p.empresaId());
        aplicarProveedor(pr, d, null);
        proveedores.save(pr);
        auditoria.registrar(p, "PROVEEDOR", "Proveedor creado: " + pr.getNombre());
        return mapaProveedor(pr);
    }

    @Transactional
    public Map<String, Object> editarProveedor(AuthPrincipal p, Long id, DatosProveedor d) {
        Proveedor pr = proveedores.findByIdAndEmpresaId(id, p.empresaId())
                .orElseThrow(() -> new NegocioException("El proveedor no existe."));
        aplicarProveedor(pr, d, id);
        proveedores.save(pr);
        auditoria.registrar(p, "PROVEEDOR", "Proveedor editado: " + pr.getNombre());
        return mapaProveedor(pr);
    }

    @Transactional
    public void desactivarProveedor(AuthPrincipal p, Long id) {
        Proveedor pr = proveedores.findByIdAndEmpresaId(id, p.empresaId())
                .orElseThrow(() -> new NegocioException("El proveedor no existe."));
        pr.setActivo(false);
        proveedores.save(pr);
        auditoria.registrar(p, "PROVEEDOR", "Proveedor desactivado: " + pr.getNombre());
    }

    private void aplicarProveedor(Proveedor pr, DatosProveedor d, Long excluirId) {
        String nombre = limpiar(d == null ? null : d.nombre(), 150);
        if (nombre == null) throw new NegocioException("Escribe el nombre del proveedor.");
        if (proveedores.existeNombre(pr.getEmpresaId(), nombre, excluirId)) {
            throw new NegocioException("Ya tienes un proveedor con ese nombre.");
        }
        String email = limpiar(d.email(), 120);
        if (email != null && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")) {
            throw new NegocioException("El correo del proveedor no es válido.");
        }
        pr.setNombre(nombre);
        pr.setRut(limpiar(d.rut(), 12));
        pr.setTelefono(limpiar(d.telefono(), 40));
        pr.setEmail(email);
        pr.setNotas(limpiar(d.notas(), 300));
    }

    private Map<String, Object> mapaProveedor(Proveedor pr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", pr.getId());
        m.put("nombre", pr.getNombre());
        m.put("rut", pr.getRut());
        m.put("telefono", pr.getTelefono());
        m.put("email", pr.getEmail());
        m.put("notas", pr.getNotas());
        return m;
    }

    // ------------------------------------------------------------------------------------------
    // Compras
    // ------------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> registrar(AuthPrincipal p, NuevaCompra r) {
        if (r == null) throw new NegocioException("Faltan los datos de la compra.");
        Proveedor prov = proveedores.findByIdAndEmpresaId(r.proveedorId(), p.empresaId())
                .filter(x -> Boolean.TRUE.equals(x.getActivo()))
                .orElseThrow(() -> new NegocioException("Elige un proveedor."));
        String tipo = r.tipoDocumento() == null ? "" : r.tipoDocumento().trim().toUpperCase();
        if (!TIPOS.contains(tipo)) throw new NegocioException("Elige el tipo de documento.");
        String folio = limpiar(r.folio(), 30);
        LocalDate hoy = LocalDate.now();
        LocalDate fecha = r.fechaDocumento() == null ? hoy : r.fechaDocumento();
        if (fecha.isAfter(hoy.plusDays(1)) || fecha.isBefore(LocalDate.of(2000, 1, 1))) {
            throw new NegocioException("La fecha del documento no es válida.");
        }
        if (r.vencimiento() != null && r.vencimiento().isBefore(fecha)) {
            throw new NegocioException("El vencimiento no puede ser anterior a la fecha del documento.");
        }
        if (folio != null && compras.existeDocumento(p.empresaId(), prov.getId(), tipo, folio)) {
            throw new NegocioException("Ya registraste este documento de " + prov.getNombre() + " (folio " + folio + ").");
        }
        List<LineaCompra> lineas = r.items();
        if (lineas == null || lineas.isEmpty()) throw new NegocioException("Agrega al menos un producto.");
        if (lineas.size() > 200) throw new NegocioException("Máximo 200 líneas por compra.");

        boolean netos = Boolean.TRUE.equals(r.preciosNetos()) && !"FACTURA_EXENTA".equals(tipo);
        boolean ingresar = r.ingresarStock() == null || r.ingresarStock();
        boolean costos = r.actualizarCostos() == null || r.actualizarCostos();

        Compra c = new Compra();
        c.setEmpresaId(p.empresaId());
        c.setProveedorId(prov.getId());
        c.setTipoDocumento(tipo);
        c.setFolio(folio);
        c.setFechaDocumento(fecha);
        c.setVencimiento(r.vencimiento());
        c.setPreciosNetos(netos);
        c.setIngresoStock(ingresar);
        c.setActualizoCostos(costos);
        c.setNota(limpiar(r.nota(), 255));
        c.setUsuario(p.usuario());

        Set<Long> vistos = new HashSet<>();
        Map<Long, Producto> prods = new HashMap<>();
        long suma = 0;
        for (LineaCompra l : lineas) {
            if (l == null || l.productoId() == null) throw new NegocioException("Hay una línea sin producto.");
            if (!vistos.add(l.productoId())) throw new NegocioException("Un producto está repetido en la compra. Súmalo en una sola línea.");
            Producto prod = productos.findByIdAndEmpresaId(l.productoId(), p.empresaId())
                    .filter(x -> !Boolean.FALSE.equals(x.getActivo()))
                    .orElseThrow(() -> new NegocioException("Uno de los productos no existe."));
            double cant = l.cantidad() == null ? 0 : l.cantidad();
            if (!Double.isFinite(cant) || cant <= 0 || cant > 1_000_000) {
                throw new NegocioException("Cantidad no válida en " + prod.getDescripcion() + ".");
            }
            if (Boolean.TRUE.equals(prod.getPesable())) {
                cant = Math.round(cant * 1000d) / 1000d;
                if (cant <= 0) throw new NegocioException("Cantidad no válida en " + prod.getDescripcion() + ".");
            } else if (cant != Math.rint(cant)) {
                throw new NegocioException(prod.getDescripcion() + " no se vende por peso: la cantidad debe ser un número entero.");
            }
            long costo = l.costoUnitario() == null ? -1 : l.costoUnitario();
            if (costo < 0 || costo > 1_000_000_000L) throw new NegocioException("Costo no válido en " + prod.getDescripcion() + ".");
            CompraItem it = new CompraItem();
            it.setCompra(c);
            it.setProductoId(prod.getId());
            it.setDescripcion(prod.getDescripcion());
            it.setCantidad(cant);
            it.setCostoUnitario(costo);
            it.setSubtotal(Math.round(cant * costo));
            c.getItems().add(it);
            prods.put(prod.getId(), prod);
            suma += it.getSubtotal();
        }
        long total = netos ? Math.round(suma * (1 + IVA)) : suma;
        if (r.totalDocumento() != null && r.totalDocumento() > 0) total = r.totalDocumento(); // lo que dice el papel
        if (total < 0 || total > MAX_PESOS) throw new NegocioException("El total no es válido.");
        c.setTotal(total);

        boolean pagada = Boolean.TRUE.equals(r.pagada());
        if (pagada && total > 0) {
            String metodo = metodoValido(r.metodoPago());
            agregarPago(c, total, metodo, "Pagada al registrar", hoy, p);
        }
        compras.save(c);

        if (ingresar) {
            c.setSucursalId(inventario.resolverSucursal(p, null));
            String ref = "Compra N° " + c.getId() + " · " + prov.getNombre() + (folio != null ? " · doc. " + folio : "");
            for (CompraItem it : c.getItems()) {
                inventario.registrarManual(p, null, it.getProductoId(), MovimientoInventario.ENTRADA, it.getCantidad(), ref);
            }
        }
        if (costos) {
            for (CompraItem it : c.getItems()) {
                Producto prod = prods.get(it.getProductoId());
                prod.setCosto((double) it.getCostoUnitario());
                productos.save(prod);
            }
        }
        auditoria.registrar(p, "COMPRA", "Compra N° " + c.getId() + " a " + prov.getNombre() + " por $" + total
                + (ingresar ? " (stock ingresado)" : "") + (costos ? " (costos actualizados)" : ""));
        return detalle(p, c.getId());
    }

    @Transactional
    public Map<String, Object> pagar(AuthPrincipal p, Long id, NuevoPago pago) {
        Compra c = compras.findByIdAndEmpresaId(id, p.empresaId()).orElseThrow(() -> new NegocioException("La compra no existe."));
        if (!Compra.ACTIVA.equals(c.getEstado())) throw new NegocioException("La compra está anulada.");
        long saldo = c.getTotal() - sumaPagos(c);
        long monto = pago == null || pago.monto() == null ? 0 : pago.monto();
        if (monto <= 0) throw new NegocioException("Indica el monto del pago.");
        if (monto > saldo) throw new NegocioException("El pago ($" + monto + ") supera lo que se debe ($" + Math.max(saldo, 0) + ").");
        LocalDate hoy = LocalDate.now();
        LocalDate fecha = pago.fecha() == null ? hoy : pago.fecha();
        if (fecha.isAfter(hoy)) throw new NegocioException("La fecha del pago no puede ser futura.");
        agregarPago(c, monto, metodoValido(pago.metodo()), limpiar(pago.nota(), 255), fecha, p);
        compras.save(c);
        auditoria.registrar(p, "COMPRA_PAGO", "Pago de $" + monto + " a la compra N° " + c.getId());
        return detalle(p, id);
    }

    @Transactional
    public Map<String, Object> anular(AuthPrincipal p, Long id, String motivo) {
        Compra c = compras.findByIdAndEmpresaId(id, p.empresaId()).orElseThrow(() -> new NegocioException("La compra no existe."));
        if (!Compra.ACTIVA.equals(c.getEstado())) throw new NegocioException("La compra ya está anulada.");
        String m = limpiar(motivo, 255);
        if (m == null || m.length() < 3) throw new NegocioException("Escribe el motivo de la anulación.");
        if (Boolean.TRUE.equals(c.getIngresoStock())) {
            for (CompraItem it : c.getItems()) {
                try {
                    inventario.registrarManual(p, c.getSucursalId(), it.getProductoId(), MovimientoInventario.MERMA,
                            it.getCantidad(), "Anulación de la compra N° " + c.getId());
                } catch (NegocioException e) {
                    throw new NegocioException("No se puede anular: ya no hay stock suficiente de " + it.getDescripcion()
                            + " para devolver lo que ingresó esta compra. Ajusta el stock y vuelve a intentar.");
                }
            }
        }
        c.setEstado(Compra.ANULADA);
        c.setMotivoAnulacion(m);
        compras.save(c);
        auditoria.registrar(p, "COMPRA_ANULADA", "Compra N° " + c.getId() + " anulada: " + m);
        return detalle(p, id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(AuthPrincipal p, Long proveedorId, String estado, LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now();
        LocalDate d = desde == null ? hoy.minusMonths(3) : desde;
        LocalDate h = hasta == null ? hoy : hasta;
        List<Compra> lista = compras.listar(p.empresaId(), proveedorId, d, h, PageRequest.of(0, 500));
        Map<Long, Long> pagado = pagadoDe(p.empresaId(), lista);
        Map<Long, String> nombres = new HashMap<>();
        proveedores.findAllById(lista.stream().map(Compra::getProveedorId).distinct().toList())
                .forEach(x -> nombres.put(x.getId(), x.getNombre()));
        String filtro = estado == null ? "TODAS" : estado.trim().toUpperCase();
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Compra c : lista) {
            long pg = pagado.getOrDefault(c.getId(), 0L);
            String est = estadoPago(c, pg, hoy);
            if (!"TODAS".equals(filtro)) {
                boolean porPagar = "POR_PAGAR".equals(est) || "VENCIDA".equals(est);
                if (("POR_PAGAR".equals(filtro) && !porPagar) || ("PAGADA".equals(filtro) && !"PAGADA".equals(est))
                        || ("VENCIDA".equals(filtro) && !"VENCIDA".equals(est)) || ("ANULADA".equals(filtro) && !"ANULADA".equals(est))) {
                    continue;
                }
            }
            salida.add(resumen(c, nombres.get(c.getProveedorId()), pg, est));
        }
        return salida;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(AuthPrincipal p, Long id) {
        Compra c = compras.findByIdAndEmpresaId(id, p.empresaId()).orElseThrow(() -> new NegocioException("La compra no existe."));
        long pg = sumaPagos(c);
        String nombre = proveedores.findById(c.getProveedorId()).map(Proveedor::getNombre).orElse("—");
        Map<String, Object> m = resumen(c, nombre, pg, estadoPago(c, pg, LocalDate.now()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (CompraItem it : c.getItems()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("productoId", it.getProductoId());
            x.put("descripcion", it.getDescripcion());
            x.put("cantidad", it.getCantidad());
            x.put("costoUnitario", it.getCostoUnitario());
            x.put("subtotal", it.getSubtotal());
            items.add(x);
        }
        m.put("items", items);
        List<Map<String, Object>> ps = new ArrayList<>();
        for (CompraPago x : c.getPagos()) {
            Map<String, Object> y = new LinkedHashMap<>();
            y.put("id", x.getId());
            y.put("monto", x.getMonto());
            y.put("metodo", x.getMetodo());
            y.put("fecha", x.getFecha().toString());
            y.put("nota", x.getNota());
            y.put("usuario", x.getUsuario());
            ps.add(y);
        }
        m.put("pagos", ps);
        m.put("nota", c.getNota());
        m.put("motivoAnulacion", c.getMotivoAnulacion());
        m.put("usuario", c.getUsuario());
        m.put("actualizoCostos", Boolean.TRUE.equals(c.getActualizoCostos()));
        m.put("preciosNetos", Boolean.TRUE.equals(c.getPreciosNetos()));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumenGeneral(AuthPrincipal p) {
        LocalDate hoy = LocalDate.now();
        long comprado = 0;
        for (Object[] f : compras.totalesPorProveedor(p.empresaId())) comprado += ((Number) f[1]).longValue();
        long pagado = 0;
        for (Object[] f : pagos.pagadoPorProveedor(p.empresaId())) pagado += ((Number) f[1]).longValue();
        long vencido = 0;
        List<Compra> v = compras.conVencimientoPasado(p.empresaId(), hoy);
        Map<Long, Long> pg = pagadoDe(p.empresaId(), v);
        for (Compra c : v) vencido += Math.max(c.getTotal() - pg.getOrDefault(c.getId(), 0L), 0L);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("porPagar", Math.max(comprado - pagado, 0L));
        m.put("vencido", vencido);
        m.put("comprasDelMes", compras.totalEnPeriodo(p.empresaId(), hoy.withDayOfMonth(1), hoy.plusDays(1)));
        m.put("adjuntosHabilitados", archivos.habilitado());
        return m;
    }

    // ------------------------------------------------------------------------------------------
    // Foto o PDF del documento
    // ------------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> adjuntar(AuthPrincipal p, Long id, MultipartFile archivo) {
        Compra c = compras.findByIdAndEmpresaId(id, p.empresaId()).orElseThrow(() -> new NegocioException("La compra no existe."));
        ComprobanteService.Archivo a = archivos.subirEn(p.empresaId(), "compras", id, archivo);
        String anterior = c.getAdjuntoKey();
        c.setAdjuntoKey(a.key());
        c.setAdjuntoNombre(a.nombre());
        compras.save(c);
        archivos.eliminar(anterior);
        auditoria.registrar(p, "COMPRA_ADJUNTO", "Documento adjunto a la compra N° " + id);
        return detalle(p, id);
    }

    @Transactional(readOnly = true)
    public String urlAdjunto(AuthPrincipal p, Long id) {
        Compra c = compras.findByIdAndEmpresaId(id, p.empresaId()).orElseThrow(() -> new NegocioException("La compra no existe."));
        if (c.getAdjuntoKey() == null || c.getAdjuntoKey().isBlank()) throw new NegocioException("Esta compra no tiene documento adjunto.");
        return archivos.urlTemporal(c.getAdjuntoKey());
    }

    // ------------------------------------------------------------------------------------------

    private Map<String, Object> resumen(Compra c, String proveedor, long pagado, String estadoPago) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("proveedorId", c.getProveedorId());
        m.put("proveedor", proveedor);
        m.put("tipoDocumento", c.getTipoDocumento());
        m.put("folio", c.getFolio());
        m.put("fechaDocumento", c.getFechaDocumento().toString());
        m.put("vencimiento", c.getVencimiento() == null ? null : c.getVencimiento().toString());
        m.put("total", c.getTotal());
        m.put("pagado", pagado);
        m.put("saldo", Compra.ANULADA.equals(c.getEstado()) ? 0L : Math.max(c.getTotal() - pagado, 0L));
        m.put("estado", estadoPago);
        m.put("ingresoStock", Boolean.TRUE.equals(c.getIngresoStock()));
        m.put("tieneAdjunto", c.getAdjuntoKey() != null && !c.getAdjuntoKey().isBlank());
        m.put("adjuntoNombre", c.getAdjuntoNombre());
        return m;
    }

    /** ANULADA | PAGADA | VENCIDA | POR_PAGAR */
    private String estadoPago(Compra c, long pagado, LocalDate hoy) {
        if (Compra.ANULADA.equals(c.getEstado())) return "ANULADA";
        if (pagado >= c.getTotal()) return "PAGADA";
        if (c.getVencimiento() != null && c.getVencimiento().isBefore(hoy)) return "VENCIDA";
        return "POR_PAGAR";
    }

    private long sumaPagos(Compra c) {
        long s = 0;
        for (CompraPago x : c.getPagos()) s += x.getMonto();
        return s;
    }

    private Map<Long, Long> pagadoDe(Long empresaId, List<Compra> lista) {
        Map<Long, Long> m = new HashMap<>();
        if (lista.isEmpty()) return m;
        for (Object[] f : pagos.pagadoPorCompra(empresaId, lista.stream().map(Compra::getId).toList())) {
            m.put((Long) f[0], ((Number) f[1]).longValue());
        }
        return m;
    }

    private void agregarPago(Compra c, long monto, String metodo, String nota, LocalDate fecha, AuthPrincipal p) {
        CompraPago pg = new CompraPago();
        pg.setCompra(c);
        pg.setMonto(monto);
        pg.setMetodo(metodo);
        pg.setNota(nota);
        pg.setFecha(fecha);
        pg.setUsuario(p.usuario());
        c.getPagos().add(pg);
    }

    private String metodoValido(String metodo) {
        String m = metodo == null ? "TRANSFERENCIA" : metodo.trim().toUpperCase();
        if (!METODOS.contains(m)) throw new NegocioException("Forma de pago no válida.");
        return m;
    }

    private static String limpiar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
