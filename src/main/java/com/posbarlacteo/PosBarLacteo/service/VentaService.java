package com.posbarlacteo.PosBarLacteo.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.exception.ConflictoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Cliente;
import com.posbarlacteo.PosBarLacteo.model.ItemVenta;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Receta;
import com.posbarlacteo.PosBarLacteo.model.TurnoCaja;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;
import com.posbarlacteo.PosBarLacteo.repository.ClienteRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.repository.RecetaRepository;
import com.posbarlacteo.PosBarLacteo.repository.TurnoCajaRepository;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;
import com.posbarlacteo.PosBarLacteo.repository.VentaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

@Service
public class VentaService {

    private static final Logger log = LoggerFactory.getLogger(VentaService.class);

    private final ProductoRepository productoRepository;
    private final RecetaRepository recetaRepository;
    private final VentaRepository ventaRepository;
    private final TurnoCajaRepository turnoCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final ValeCreditoPdfService valeCreditoPdfService;
    private final InventarioService inventarioService;

    public VentaService(ProductoRepository productoRepository, RecetaRepository recetaRepository,
                        VentaRepository ventaRepository, TurnoCajaRepository turnoCajaRepository,
                        UsuarioRepository usuarioRepository, ClienteRepository clienteRepository,
                        ValeCreditoPdfService valeCreditoPdfService, InventarioService inventarioService) {
        this.productoRepository = productoRepository;
        this.recetaRepository = recetaRepository;
        this.ventaRepository = ventaRepository;
        this.turnoCajaRepository = turnoCajaRepository;
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.valeCreditoPdfService = valeCreditoPdfService;
        this.inventarioService = inventarioService;
    }

    /**
     * Registra la venta completa. Empresa, cajero y sucursal salen SIEMPRE del token.
     * El total se calcula en el servidor; si el monto informado por el cliente no coincide, se rechaza.
     */
    @Transactional
    public Venta procesarVentaCompleta(List<ItemVenta> items, long montoEsperado, String metodoPago,
                                       AuthPrincipal p, Long clienteId) {
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
        venta.setMetodoPago(metodoPago);
        venta.setEmpresa(usuario.getEmpresa());
        venta.setUsuario(usuario);
        venta.setSucursal(usuario.getSucursal());

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
        if (totalServidor != montoEsperado) {
            throw new ConflictoException("El total cambió (" + totalServidor + " vs " + montoEsperado
                    + "). Actualiza los precios del carrito e inténtalo de nuevo.");
        }
        venta.setTotal((double) totalServidor);

        if (clienteId != null) {
            Cliente cliente = clienteRepository.findByIdAndEmpresaId(clienteId, p.empresaId())
                    .orElseThrow(() -> new NegocioException("Cliente no encontrado."));
            venta.setCliente(cliente);

            if ("CREDITO".equalsIgnoreCase(metodoPago)) {
                double deuda = cliente.getDeudaActual() != null ? cliente.getDeudaActual() : 0.0;
                cliente.setDeudaActual(deuda + totalServidor);
                clienteRepository.save(cliente);
            }
        } else if ("CREDITO".equalsIgnoreCase(metodoPago)) {
            throw new NegocioException("Para vender a crédito debes seleccionar un cliente.");
        }

        venta.setDetalles(detalles);
        ventaRepository.save(venta);

        // Stock de la sucursal de la venta, atómico y con historial; si no alcanza (y la empresa no permite
        // stock negativo) lanza NegocioException y TODA la venta se revierte.
        inventarioService.descontarVenta(p.empresaId(), usuario.getSucursal().getId(), venta.getId(),
                p.usuario(), consumo, nombres);

        BigDecimal monto = BigDecimal.valueOf(totalServidor);
        if ("EFECTIVO".equalsIgnoreCase(metodoPago)) {
            turno.setVentasEfectivo(nz(turno.getVentasEfectivo()).add(monto));
        } else if ("TARJETA".equalsIgnoreCase(metodoPago)) {
            turno.setVentasTarjeta(nz(turno.getVentasTarjeta()).add(monto));
        } else if ("CREDITO".equalsIgnoreCase(metodoPago)) {
            turno.setVentasCredito(nz(turno.getVentasCredito()).add(monto));
            try {
                valeCreditoPdfService.generarGuardarYImprimirVale(venta, venta.getEmpresa().getRazonSocial());
            } catch (Exception e) {
                log.warn("No se pudo generar el vale de crédito de la venta {}: {}", venta.getId(), e.getMessage());
            }
        } else {
            throw new NegocioException("Método de pago no soportado: " + metodoPago);
        }
        turnoCajaRepository.save(turno);

        return venta;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
