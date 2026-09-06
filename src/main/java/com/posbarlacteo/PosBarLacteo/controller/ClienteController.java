package com.posbarlacteo.PosBarLacteo.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.model.Cliente;
import com.posbarlacteo.PosBarLacteo.model.Empresa; // ✨ NUEVO IMPORT
import com.posbarlacteo.PosBarLacteo.repository.ClienteRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository; // ✨ NUEVO IMPORT
import com.posbarlacteo.PosBarLacteo.service.CajaService;
import com.posbarlacteo.PosBarLacteo.service.ValeCreditoPdfService;

@RestController
@RequestMapping("/api/clientes")
@CrossOrigin(origins = {
    "http://posbarlacteo-manuel-2026.s3-website-us-east-1.amazonaws.com",
    "http://localhost:5173",
    "http://34.203.91.138",
    "https://ordpos.duckdns.org",
    "http://192.168.100.85:5173"
})
public class ClienteController {

    private final ClienteRepository clienteRepository;

    // ✨ SE INYECTA EL REPOSITORIO DE EMPRESA
    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private CajaService cajaService;

    @Autowired
    private ValeCreditoPdfService valeCreditoPdfService;

    public ClienteController(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @PostMapping
    public ResponseEntity<?> crearCliente(@RequestBody Cliente cliente) {
        try {
            if (cliente.getNombre() == null || cliente.getNombre().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El nombre es obligatorio"));
            }
            if (cliente.getRut() == null || cliente.getRut().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El RUT es obligatorio"));
            }

            Cliente nuevoCliente = clienteRepository.save(cliente);
            return ResponseEntity.ok(nuevoCliente);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error al crear cliente: " + e.getMessage()));
        }
    }
    
    // 📋 OBTENER TODOS LOS CLIENTES (Filtrado por empresa)
    @GetMapping
    public ResponseEntity<?> obtenerTodosLosClientes(@RequestParam(required = false) Long empresaId) {
        try {
            List<Cliente> clientes;
            if (empresaId != null) {
                clientes = clienteRepository.findByEmpresaId(empresaId);
            } else {
                clientes = clienteRepository.findAll();
            }
            
            // ✨ NUEVO: Filtrar la lista para quitar los que no tienen RUT
            clientes = clientes.stream()
                .filter(c -> c.getRut() != null && !c.getRut().trim().isEmpty())
                .toList();

            return ResponseEntity.ok(clientes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error al obtener clientes: " + e.getMessage()));
        }
    }

    // 🔄 REINICIAR CRÉDITOS (Filtrado por empresa)
    @PostMapping("/reiniciar-creditos")
    public ResponseEntity<?> reiniciarCreditos(@RequestParam(required = false) Long empresaId) {
        try {
            List<Cliente> clientes;
            if (empresaId != null) {
                clientes = clienteRepository.findByEmpresaId(empresaId);
            } else {
                return ResponseEntity.badRequest().body(Map.of("message", "El empresaId es obligatorio para esta acción."));
            }
            
            boolean cambiosRealizados = false;
            for (Cliente cliente : clientes) {
                if (cliente.getDeudaActual() != null && cliente.getDeudaActual() > 0) {
                    cliente.setDeudaActual(0.0);
                    cambiosRealizados = true;
                }
            }

            if (cambiosRealizados) {
                clienteRepository.saveAll(clientes);
            }

            return ResponseEntity.ok(Map.of("message", "Todos los créditos han sido reiniciados a 0 exitosamente."));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error al reiniciar los créditos: " + e.getMessage()));
        }
    }

    // 🔍 BUSCAR CLIENTES POR NOMBRE O RUT
    @GetMapping("/buscar")
    public ResponseEntity<?> buscarCliente(
            @RequestParam String termino,
            @RequestParam Long empresaId) {
        try {
            if (empresaId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "El empresaId de la sesión es obligatorio"));
            }

            if (termino == null || termino.trim().isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            String terminoOriginal = termino.trim();
            String terminoLimpio = terminoOriginal.replaceAll("[.-]", "");

            List<Cliente> clientes = clienteRepository.buscarPorTerminoYEmpresa(
                    terminoOriginal, 
                    terminoLimpio, 
                    empresaId
            );

            return ResponseEntity.ok(clientes);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error en la búsqueda: " + e.getMessage()));
        }
    }

    // 💰 ABONAR A LA DEUDA DEL CLIENTE
    @PostMapping("/{id}/abonar")
    public ResponseEntity<?> abonarDeuda(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            Optional<Cliente> clienteOpt = clienteRepository.findById(id);
            if (clienteOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Cliente no encontrado"));
            }

            Double monto = Double.parseDouble(payload.get("monto").toString());
            Long usuarioId = Long.parseLong(payload.get("usuarioId").toString());

            if (monto <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "El monto del abono debe ser mayor a 0"));
            }

            Cliente cliente = clienteOpt.get();

            if (monto > cliente.getDeudaActual()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El monto del abono no puede ser mayor a la deuda actual"));
            }

            // Guardamos el saldo anterior para el ticket
            double saldoAnterior = cliente.getDeudaActual();

            // 1. Descontar la deuda del cliente
            cliente.setDeudaActual(cliente.getDeudaActual() - monto);
            Cliente clienteActualizado = clienteRepository.save(cliente);

            // 2. REGISTRAR EL INGRESO EN LA CAJA ACTIVA
            cajaService.registrarAbonoCredito(usuarioId, BigDecimal.valueOf(monto));

            // 3. ✨ BUSCAR LA RAZÓN SOCIAL DE LA EMPRESA EN LA BD
            String local = "Local_Predeterminado"; 
            if (payload.containsKey("empresaId") && payload.get("empresaId") != null) {
                Long empresaId = Long.parseLong(payload.get("empresaId").toString());
                Optional<Empresa> empresaOpt = empresaRepository.findById(empresaId);
                if (empresaOpt.isPresent()) {
                    local = empresaOpt.get().getRazonSocial(); // Asigna la razón social real
                }
            }

            String metodoPago = payload.containsKey("metodoPago") ? payload.get("metodoPago").toString() : "EFECTIVO";

            // 4. GENERAR E IMPRIMIR EL COMPROBANTE DE ABONO
            valeCreditoPdfService.generarGuardarYImprimirAbono(
                clienteActualizado.getNombre(),
                clienteActualizado.getRut(),
                monto,
                saldoAnterior,
                clienteActualizado.getDeudaActual(),
                metodoPago,
                local // ✨ PASA LA RAZÓN SOCIAL AL PDF
            );

            return ResponseEntity.ok(clienteActualizado);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error al procesar el abono: " + e.getMessage()));
        }
    }
}