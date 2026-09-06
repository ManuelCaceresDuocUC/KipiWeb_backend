package com.posbarlacteo.PosBarLacteo.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.model.Cliente;
import com.posbarlacteo.PosBarLacteo.model.Empresa; 
import com.posbarlacteo.PosBarLacteo.model.ItemVenta;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Receta;
import com.posbarlacteo.PosBarLacteo.model.TurnoCaja;
import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;
import com.posbarlacteo.PosBarLacteo.repository.ClienteRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository; 
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.repository.RecetaRepository;
import com.posbarlacteo.PosBarLacteo.repository.TurnoCajaRepository;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;
import com.posbarlacteo.PosBarLacteo.repository.VentaRepository;

import jakarta.transaction.Transactional;

@Service
public class VentaService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private RecetaRepository recetaRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private TurnoCajaRepository turnoCajaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ValeCreditoPdfService valeCreditoPdfService;
    

    @Transactional
    public Venta procesarVentaCompleta(List<ItemVenta> items, Double montoTotal, String metodoPago, Long usuarioId, Long empresaId, Long clienteId) {
        
        Venta venta = new Venta();
        venta.setTotal(montoTotal);
        venta.setMetodoPago(metodoPago);

        if (empresaId != null) {
            Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada con el ID: " + empresaId));
            venta.setEmpresa(empresa);
        } else {
            throw new RuntimeException("El ID de la empresa es obligatorio para registrar la venta");
        }

        if (usuarioId != null) {
            Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario cajero no encontrado"));
            venta.setUsuario(usuario);
        } else {
            throw new RuntimeException("El ID del cajero es obligatorio para registrar la venta");
        }
        
        List<VentaDetalle> detalles = new ArrayList<>();

        for (ItemVenta item : items) {
            Producto producto = productoRepository.findById(item.getProductoId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + item.getProductoId()));

            List<Receta> ingredientes = recetaRepository.findByProductoPrincipalId(producto.getId());

            if (ingredientes.isEmpty()) {
                producto.setStock(producto.getStock() - item.getCantidad());
                productoRepository.save(producto);
            } else {
                for (Receta receta : ingredientes) {
                    Producto insumo = receta.getInsumo();
                    Double gastoTotal = receta.getCantidadUsada() * item.getCantidad();

                    insumo.setStock(insumo.getStock() - gastoTotal);
                    productoRepository.save(insumo);
                }
            }

            VentaDetalle detalle = new VentaDetalle();
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setVenta(venta);
            detalles.add(detalle);
        }
        
        if (clienteId != null) {
            Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
            venta.setCliente(cliente);
            
            if ("CREDITO".equalsIgnoreCase(metodoPago)) {
                Double deudaActual = cliente.getDeudaActual() != null ? cliente.getDeudaActual() : 0.0;
                cliente.setDeudaActual(deudaActual + montoTotal);
                clienteRepository.save(cliente); 
            }
        } else if ("CREDITO".equalsIgnoreCase(metodoPago)) {
            throw new RuntimeException("Para vender con crédito, debe seleccionar un cliente.");
        }
        
        venta.setDetalles(detalles);
        ventaRepository.save(venta);

        TurnoCaja turnoActivo = turnoCajaRepository.findByCajeroIdAndEstado(usuarioId, "ABIERTA")
            .orElseThrow(() -> new RuntimeException("No se puede procesar la venta: No hay un turno de caja abierto para este cajero."));

        BigDecimal montoEnBigDecimal = BigDecimal.valueOf(montoTotal);

        if ("EFECTIVO".equalsIgnoreCase(metodoPago)) {
            BigDecimal efectivoActual = turnoActivo.getVentasEfectivo() != null 
                                        ? turnoActivo.getVentasEfectivo() 
                                        : BigDecimal.ZERO;
            turnoActivo.setVentasEfectivo(efectivoActual.add(montoEnBigDecimal));
            
        } else if ("TARJETA".equalsIgnoreCase(metodoPago)) {
            BigDecimal tarjetaActual = turnoActivo.getVentasTarjeta() != null 
                                       ? turnoActivo.getVentasTarjeta() 
                                       : BigDecimal.ZERO;
            turnoActivo.setVentasTarjeta(tarjetaActual.add(montoEnBigDecimal));
            
        } else if ("CREDITO".equalsIgnoreCase(metodoPago)) {
            BigDecimal creditoActual = turnoActivo.getVentasCredito() != null 
                                       ? turnoActivo.getVentasCredito() 
                                       : BigDecimal.ZERO;
            turnoActivo.setVentasCredito(creditoActual.add(montoEnBigDecimal));
            
            // Genera e imprime el Vale de Crédito de la venta fiada
            valeCreditoPdfService.generarGuardarYImprimirVale(venta, venta.getEmpresa().getRazonSocial());
        }

        turnoCajaRepository.save(turnoActivo);
        
        return venta;
    }

    @Transactional
    public Map<String, Object> procesarAbonoCliente(Long clienteId, Double montoAbono, String metodoPago, Long usuarioId, Long empresaId) {
        if (clienteId == null || montoAbono == null || montoAbono <= 0) {
            throw new RuntimeException("El cliente y un monto de abono válido son obligatorios.");
        }

        if (empresaId == null) {
            throw new RuntimeException("El ID de la empresa es obligatorio para registrar el abono.");
        }

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con el ID: " + clienteId));

        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada con el ID: " + empresaId));

        Double saldoAnterior = cliente.getDeudaActual() != null ? cliente.getDeudaActual() : 0.0;
        Double saldoRestante = Math.max(0.0, saldoAnterior - montoAbono);

        // 1. Actualizar deuda del cliente
        cliente.setDeudaActual(saldoRestante);
        clienteRepository.save(cliente);

        // 2. Actualizar turno de caja activo
        if (usuarioId != null) {
            turnoCajaRepository.findByCajeroIdAndEstado(usuarioId, "ABIERTA").ifPresent(turnoActivo -> {
                BigDecimal montoBD = BigDecimal.valueOf(montoAbono);

                BigDecimal abonosActuales = turnoActivo.getAbonosCredito() != null ? turnoActivo.getAbonosCredito() : BigDecimal.ZERO;
                turnoActivo.setAbonosCredito(abonosActuales.add(montoBD));

                if ("EFECTIVO".equalsIgnoreCase(metodoPago)) {
                    BigDecimal efectivo = turnoActivo.getVentasEfectivo() != null ? turnoActivo.getVentasEfectivo() : BigDecimal.ZERO;
                    turnoActivo.setVentasEfectivo(efectivo.add(montoBD));
                } else if ("TARJETA".equalsIgnoreCase(metodoPago)) {
                    BigDecimal tarjeta = turnoActivo.getVentasTarjeta() != null ? turnoActivo.getVentasTarjeta() : BigDecimal.ZERO;
                    turnoActivo.setVentasTarjeta(tarjeta.add(montoBD));
                }

                turnoCajaRepository.save(turnoActivo);
            });
        }

        // 3. Generar PDF e imprimir usando la razón social de la empresa
        String rutaPdf = valeCreditoPdfService.generarGuardarYImprimirAbono(
                cliente.getNombre(),
                cliente.getRut(),
                montoAbono,
                saldoAnterior,
                saldoRestante,
                metodoPago,
                empresa.getRazonSocial()
        );

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("clienteNombre", cliente.getNombre());
        respuesta.put("clienteRut", cliente.getRut());
        respuesta.put("saldoAnterior", saldoAnterior);
        respuesta.put("saldoRestante", saldoRestante);
        respuesta.put("montoAbonado", montoAbono);
        respuesta.put("comprobantePdf", rutaPdf);

        return respuesta;
    }
}