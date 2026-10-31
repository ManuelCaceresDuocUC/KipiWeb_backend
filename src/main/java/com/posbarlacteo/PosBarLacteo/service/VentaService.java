package com.posbarlacteo.PosBarLacteo.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.dto.LineaPagoDTO;
import com.posbarlacteo.PosBarLacteo.dto.PagoRequest;
import com.posbarlacteo.PosBarLacteo.exception.AccesoDenegadoException;
import com.posbarlacteo.PosBarLacteo.exception.ConflictoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Cliente;
import com.posbarlacteo.PosBarLacteo.model.ItemVenta;
import com.posbarlacteo.PosBarLacteo.model.MovimientoCaja;
import com.posbarlacteo.PosBarLacteo.model.MovimientoCliente;
import com.posbarlacteo.PosBarLacteo.model.PagoVenta;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Receta;
import com.posbarlacteo.PosBarLacteo.model.TurnoCaja;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;
import com.posbarlacteo.PosBarLacteo.repository.ClienteRepository;
import com.posbarlacteo.PosBarLacteo.repository.MovimientoCajaRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.repository.RecetaRepository;
import com.posbarlacteo.PosBarLacteo.repository.TurnoCajaRepository;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;
import com.posbarlacteo.PosBarLacteo.repository.VentaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

@Service
public class VentaService {

    private static final Logger log = LoggerFactory.getLogger(VentaService.class);

    private static final Set<String> METODOS = Set.of(
            PagoVenta.EFECTIVO, PagoVenta.TARJETA, PagoVenta.TRANSFERENCIA, PagoVenta.CREDITO);
    private static final Set<String> PROVEEDORES_TARJETA = Set.of("GETNET", "MERCADOPAGO", "TRANSBANK");

    private final ProductoRepository productoRepository;
    private final RecetaRepository recetaRepository;
    private final VentaRepository ventaRepository;
    private final TurnoCajaRepository turnoCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final CuentaClienteService cuentaClienteService;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final ValeCreditoPdfService valeCreditoPdfService;
    private final InventarioService inventarioService;

    public VentaService(ProductoRepository productoRepository, RecetaRepository recetaRepository,
                        VentaRepository ventaRepository, TurnoCajaRepository turnoCajaRepository,
                        UsuarioRepository usuarioRepository, ClienteRepository clienteRepository,
                        CuentaClienteService cuentaClienteService,
                        MovimientoCajaRepository movimientoCajaRepository,
                        ValeCreditoPdfService valeCreditoPdfService, InventarioService inventarioService) {
        this.productoRepository = productoRepository;
        this.recetaRepository = recetaRepository;
        this.ventaRepository = ventaRepository;
        this.turnoCajaRepository = turnoCajaRepository;
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.cuentaClienteService = cuentaClienteService;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.valeCreditoPdfService = valeCreditoPdfService;
        this.inventarioService = inventarioService;
    }

    /** Venta ya registrada con esa clave (reintento del mismo cobro), si existe. */
    @Transactional(readOnly = true)
    public Optional<Venta> buscarPorClave(AuthPrincipal p, String clave) {
        if (clave == null || clave.isBlank()) {
            return Optional.empty();
        }
        return ventaRepository.buscarPorClave(p.empresaId(), clave.trim());
    }

    /**
     * Registra la venta completa. Empresa, cajero y sucursal salen SIEMPRE del token.
     * El total se calcula en el servidor; si el monto informado por el cliente no coincide, se rechaza.
     * Los pagos (uno o varios) deben sumar exactamente el total.
     */
    @Transactional
    public Venta procesarVenta(PagoRequest req, AuthPrincipal p) {
        List<ItemVenta> items = req.getItems();
        if (items == null || items.isEmpty()) {
            throw new NegocioException("El carrito está vacío.");
        }

        Usuario usuario = usuarioRepository.findById(p.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        if (usuario.getEmpresa() == null || !usuario.getEmpresa().getId().equals(p.empresaId())) {
            throw new NegocioException("Sesión inconsistente. Vuelve a iniciar sesión.");
        }
        if (usuario.getSucursal() == null) {
            throw new NegocioException("Tu usuario no tiene una sucursal asignada.");
        }

        // Primero la caja: así no se toca stock ni nada si el turno no está abierto
        TurnoCaja turno = turnoCajaRepository.findByCajeroIdAndEstado(p.usuarioId(), "ABIERTA")
                .orElseThrow(() -> new NegocioException(
                        "No hay un turno de caja abierto. Abre la caja antes de vender."));

        Venta venta = new Venta();
        venta.setEmpresa(usuario.getEmpresa());
        venta.setUsuario(usuario);
        venta.setSucursal(usuario.getSucursal());
        venta.setTurnoId(turno.getId());
        venta.setEstado("COMPLETADA");
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            String clave = req.getIdempotencyKey().trim();
            if (clave.length() > 64) {
                throw new NegocioException("La clave de la operación no es válida.");
            }
            venta.setIdempotencyKey(clave);
        }

        List<VentaDetalle> detalles = new ArrayList<>();
        Map<Long, Double> consumo = new HashMap<>();
        Map<Long, String> nombres = new HashMap<>();
        double total = 0.0;

        for (ItemVenta item : items) {
            if (item == null || item.getProductoId() == null || item.getCantidad() == null
                    || item.getCantidad() <= 0 || Double.isNaN(item.getCantidad())) {
                throw new NegocioException("Hay un producto con cantidad inválida en el carrito.");
            }
            Producto producto = productoRepository.findByIdAndEmpresaId(item.getProductoId(), p.empresaId())
                    .orElseThrow(() -> new NegocioException("Producto no encontrado: " + item.getProductoId()));
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new NegocioException("El producto \"" + producto.getDescripcion() + "\" está desactivado.");
            }
            if (Boolean.TRUE.equals(producto.getEsInsumo())) {
                throw new NegocioException("\"" + producto.getDescripcion() + "\" es un insumo y no se vende directamente.");
            }

            // Qué se consume realmente: el producto, o sus insumos si tiene receta
            List<Receta> ingredientes = recetaRepository.findByProductoPrincipalId(producto.getId());
            if (ingredientes.isEmpty()) {
                consumo.merge(producto.getId(), item.getCantidad(), Double::sum);
                nombres.put(producto.getId(), producto.getDescripcion());
            } else {
                for (Receta receta : ingredientes) {
                    Producto insumo = receta.getInsumo();
                    consumo.merge(insumo.getId(), receta.getCantidadUsada() * item.getCantidad(), Double::sum);
                    nombres.put(insumo.getId(), insumo.getDescripcion());
                }
            }

            VentaDetalle detalle = new VentaDetalle();
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setVenta(venta);
            detalles.add(detalle);
            total += producto.getPrecio() * item.getCantidad();
        }

        long totalServidor = Math.round(total);
        if (totalServidor != req.getMonto()) {
            throw new ConflictoException("El total cambió (" + totalServidor + " vs " + req.getMonto()
                    + "). Actualiza los precios del carrito e inténtalo de nuevo.");
        }
        if (totalServidor <= 0) {
            throw new NegocioException("El total de la venta debe ser mayor a cero.");
        }
        venta.setTotal((double) totalServidor);
        venta.setDetalles(detalles);

        // ---- pagos ----
        Map<String, LineaPagoDTO> lineas = normalizarPagos(req.getPagos(), totalServidor);
        List<PagoVenta> pagos = new ArrayList<>();
        for (LineaPagoDTO l : lineas.values()) {
            PagoVenta pago = new PagoVenta();
            pago.setVenta(venta);
            pago.setMetodo(l.getMetodo());
            pago.setMonto(l.getMonto());
            pago.setReferencia(limpiar(l.getReferencia(), 100));
            if (PagoVenta.TARJETA.equals(l.getMetodo())) {
                String prov = l.getProveedorTarjeta() == null ? null : l.getProveedorTarjeta().trim().toUpperCase();
                if (prov != null && !prov.isEmpty() && !PROVEEDORES_TARJETA.contains(prov)) {
                    throw new NegocioException("Terminal de tarjeta no soportada: " + l.getProveedorTarjeta());
                }
                pago.setProveedorTarjeta(prov == null || prov.isEmpty() ? null : prov);
            }
            if (PagoVenta.TRANSFERENCIA.equals(l.getMetodo()) && !Boolean.TRUE.equals(l.getConfirmado())) {
                throw new NegocioException("Confirma que recibiste la transferencia antes de registrar la venta.");
            }
            pago.setConfirmado(true);
            pago.setConfirmadoPor(p.usuario());
            pago.setFechaConfirmacion(LocalDateTime.now());
            pagos.add(pago);
        }
        venta.setPagos(pagos);
        venta.setMetodoPago(pagos.size() == 1 ? pagos.get(0).getMetodo() : "MIXTO");

        // ---- crédito (fiado) ----
        long montoCredito = lineas.containsKey(PagoVenta.CREDITO) ? lineas.get(PagoVenta.CREDITO).getMonto() : 0L;
        Cliente cliente = null;
        if (req.getClienteId() != null) {
            cliente = clienteRepository.findByIdAndEmpresaId(req.getClienteId(), p.empresaId())
                    .orElseThrow(() -> new NegocioException("Cliente no encontrado."));
            venta.setCliente(cliente);
        }
        if (montoCredito > 0) {
            if (!Boolean.TRUE.equals(usuario.getEmpresa().getPermiteFiado())) {
                throw new NegocioException("El fiado no está habilitado para tu empresa. "
                        + "Un administrador puede activarlo en Administración.");
            }
            if (cliente == null) {
                throw new NegocioException("Para vender a crédito debes seleccionar un cliente.");
            }
        }

        ventaRepository.save(venta);

        // Stock de la sucursal de la venta, atómico y con historial; si no alcanza (y la empresa no permite
        // stock negativo) lanza NegocioException y TODA la venta se revierte.
        inventarioService.descontarVenta(p.empresaId(), usuario.getSucursal().getId(), venta.getId(),
                p.usuario(), consumo, nombres);

        if (montoCredito > 0) {
            cargarCredito(cliente, p, venta.getId(), montoCredito);
        }

        // Totales del turno: una sola sentencia atómica (no pisa lo que otro proceso sume al mismo tiempo)
        int filas = turnoCajaRepository.sumarVentas(turno.getId(),
                monto(lineas, PagoVenta.EFECTIVO), monto(lineas, PagoVenta.TARJETA),
                monto(lineas, PagoVenta.TRANSFERENCIA), monto(lineas, PagoVenta.CREDITO));
        if (filas == 0) {
            throw new NegocioException("El turno de caja ya no está abierto.");
        }

        if (montoCredito > 0) {
            try {
                valeCreditoPdfService.generarGuardarYImprimirVale(venta, venta.getEmpresa().getRazonSocial());
            } catch (Exception e) {
                log.warn("No se pudo generar el vale de crédito de la venta {}: {}", venta.getId(), e.getMessage());
            }
        }
        return venta;
    }

    /**
     * Anula una venta (solo administrador): repone el stock exacto que descontó, revierte la deuda del
     * cliente y, si el turno sigue abierto, descuenta los cobros de sus totales. Devuelve un resumen.
     */
    @Transactional
    public Map<String, Object> anular(AuthPrincipal p, Long ventaId, String motivo) {
        if (!p.esAdmin()) {
            throw new AccesoDenegadoException("Solo un administrador puede anular ventas.");
        }
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < 3) {
            throw new NegocioException("Indica el motivo de la anulación.");
        }
        if (razon.length() > 255) {
            throw new NegocioException("El motivo no puede superar los 255 caracteres.");
        }

        Venta venta = ventaRepository.buscarConBloqueo(ventaId, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada."));
        if ("ANULADA".equals(venta.getEstado())) {
            throw new ConflictoException("La venta N° " + ventaId + " ya estaba anulada.");
        }

        // 1) Stock: se invierte exactamente lo que se descontó
        inventarioService.revertirVenta(p, ventaId, "Anulación venta N° " + ventaId);

        // 2) Deuda del cliente
        Map<String, BigDecimal> porMetodo = new LinkedHashMap<>();
        if (venta.getPagos() != null && !venta.getPagos().isEmpty()) {
            for (PagoVenta pago : venta.getPagos()) {
                porMetodo.merge(pago.getMetodo(), BigDecimal.valueOf(pago.getMonto()), BigDecimal::add);
            }
        } else {
            porMetodo.put(venta.getMetodoPago() == null ? PagoVenta.EFECTIVO : venta.getMetodoPago(),
                    BigDecimal.valueOf(venta.getTotal()));
        }
        double creditoRevertido = 0;
        double excedenteCliente = 0;
        BigDecimal credito = porMetodo.getOrDefault(PagoVenta.CREDITO, BigDecimal.ZERO);
        if (credito.signum() > 0 && venta.getCliente() != null) {
            Long clienteId = venta.getCliente().getId();
            double saldo = nz(clienteRepository.saldoActual(clienteId, p.empresaId()));
            double aReducir = Math.min(credito.doubleValue(), saldo);
            if (aReducir > 0 && clienteRepository.abonarDeuda(clienteId, p.empresaId(), aReducir) > 0) {
                creditoRevertido = aReducir;
                cuentaClienteService.registrar(venta.getCliente(), p, MovimientoCliente.ANULACION, -aReducir,
                        venta.getId(), null, "Anulación venta N° " + ventaId + ": " + razon);
            }
            // Si el cliente ya había abonado parte de esta deuda, le sobra dinero a su favor
            excedenteCliente = credito.doubleValue() - creditoRevertido;
        }

        // 3) Caja: solo si el turno de la venta sigue abierto se corrigen sus totales
        boolean turnoAbierto = false;
        if (venta.getTurnoId() != null) {
            TurnoCaja turno = turnoCajaRepository.findByIdAndEmpresaId(venta.getTurnoId(), p.empresaId()).orElse(null);
            if (turno != null && "ABIERTA".equals(turno.getEstado())) {
                int filas = turnoCajaRepository.sumarVentas(turno.getId(),
                        porMetodo.getOrDefault(PagoVenta.EFECTIVO, BigDecimal.ZERO).negate(),
                        porMetodo.getOrDefault(PagoVenta.TARJETA, BigDecimal.ZERO).negate(),
                        porMetodo.getOrDefault(PagoVenta.TRANSFERENCIA, BigDecimal.ZERO).negate(),
                        credito.negate());
                turnoAbierto = filas > 0;
                if (turnoAbierto) {
                    MovimientoCaja mc = new MovimientoCaja();
                    mc.setTurno(turno);
                    mc.setEmpresa(venta.getEmpresa());
                    mc.setTipo(MovimientoCaja.ANULACION);
                    mc.setMonto(BigDecimal.valueOf(venta.getTotal()));
                    mc.setMotivo(razon);
                    mc.setUsuarioNombre(p.usuario());
                    mc.setVentaId(venta.getId());
                    movimientoCajaRepository.save(mc);
                }
            }
        }

        venta.setEstado("ANULADA");
        venta.setAnuladaPor(p.usuario());
        venta.setFechaAnulacion(LocalDateTime.now());
        venta.setMotivoAnulacion(razon);
        ventaRepository.save(venta);

        BigDecimal efectivo = porMetodo.getOrDefault(PagoVenta.EFECTIVO, BigDecimal.ZERO);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ventaId", venta.getId());
        r.put("estado", "ANULADA");
        r.put("turnoAbierto", turnoAbierto);
        r.put("efectivoADevolver", efectivo.longValue());
        r.put("creditoRevertido", Math.round(creditoRevertido));
        r.put("excedenteCliente", Math.round(excedenteCliente));
        StringBuilder aviso = new StringBuilder();
        if (!turnoAbierto && venta.getTurnoId() != null) {
            aviso.append("El turno de esta venta ya estaba cerrado: sus totales no se modificaron. ")
                 .append("Si devolviste efectivo, regístralo como retiro en tu caja con el motivo. ");
        }
        if (excedenteCliente > 0) {
            aviso.append("El cliente ya había abonado parte de esta deuda: tiene $")
                 .append(Math.round(excedenteCliente)).append(" a su favor que debes devolverle. ");
        }
        aviso.append("Si la venta tenía boleta electrónica, emite la nota de crédito desde tu proveedor de facturación.");
        r.put("aviso", aviso.toString());
        return r;
    }

    // ---------- helpers ----------

    /** Valida y fusiona las líneas (un método una sola vez). Sin líneas informadas no hay venta: se exige al menos una. */
    private Map<String, LineaPagoDTO> normalizarPagos(List<LineaPagoDTO> entrada, long total) {
        if (entrada == null || entrada.isEmpty()) {
            throw new NegocioException("Indica cómo se paga la venta.");
        }
        Map<String, LineaPagoDTO> resultado = new LinkedHashMap<>();
        long suma = 0;
        for (LineaPagoDTO l : entrada) {
            if (l == null || l.getMetodo() == null) {
                throw new NegocioException("Hay una forma de pago sin método.");
            }
            String metodo = l.getMetodo().trim().toUpperCase();
            if (!METODOS.contains(metodo)) {
                throw new NegocioException("Método de pago no soportado: " + l.getMetodo());
            }
            if (l.getMonto() == null || l.getMonto() <= 0) {
                throw new NegocioException("El monto de cada forma de pago debe ser mayor a cero.");
            }
            LineaPagoDTO existente = resultado.get(metodo);
            if (existente == null) {
                LineaPagoDTO copia = new LineaPagoDTO();
                copia.setMetodo(metodo);
                copia.setMonto(l.getMonto());
                copia.setProveedorTarjeta(l.getProveedorTarjeta());
                copia.setReferencia(l.getReferencia());
                copia.setConfirmado(l.getConfirmado());
                resultado.put(metodo, copia);
            } else {
                existente.setMonto(existente.getMonto() + l.getMonto());
                if (existente.getReferencia() == null) existente.setReferencia(l.getReferencia());
                if (existente.getProveedorTarjeta() == null) existente.setProveedorTarjeta(l.getProveedorTarjeta());
                existente.setConfirmado(Boolean.TRUE.equals(existente.getConfirmado()) && Boolean.TRUE.equals(l.getConfirmado()));
            }
            suma += l.getMonto();
        }
        if (suma != total) {
            throw new NegocioException("Los pagos suman $" + suma + " y el total de la venta es $" + total + ".");
        }
        return resultado;
    }

    private void cargarCredito(Cliente cliente, AuthPrincipal p, Long ventaId, long monto) {
        int filas = clienteRepository.cargarDeudaConLimite(cliente.getId(), p.empresaId(), monto);
        if (filas == 0) {
            double limite = nz(cliente.getLimiteCredito());
            double deuda = nz(clienteRepository.saldoActual(cliente.getId(), p.empresaId()));
            double disponible = Math.max(0, limite - deuda);
            if (limite <= 0) {
                throw new NegocioException("El cliente \"" + cliente.getNombre()
                        + "\" no tiene crédito autorizado (límite $0).");
            }
            throw new NegocioException("Supera el límite de crédito de \"" + cliente.getNombre()
                    + "\". Disponible: $" + Math.round(disponible) + ", solicitado: $" + monto + ".");
        }
        cuentaClienteService.registrar(cliente, p, MovimientoCliente.CARGO, monto, ventaId, PagoVenta.CREDITO,
                "Venta N° " + ventaId + " a crédito");
    }

    private static BigDecimal monto(Map<String, LineaPagoDTO> lineas, String metodo) {
        LineaPagoDTO l = lineas.get(metodo);
        return l == null ? BigDecimal.ZERO : BigDecimal.valueOf(l.getMonto());
    }

    private static String limpiar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static double nz(Double v) {
        return v != null ? v : 0.0;
    }
}
