package com.posbarlacteo.PosBarLacteo.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.ConflictoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Cliente;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.ClienteRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.CajaService;
import com.posbarlacteo.PosBarLacteo.service.ValeCreditoPdfService;

/** Todos los clientes pertenecen a la empresa del token; ningún endpoint recibe empresaId. */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteRepository clienteRepository;
    private final EmpresaRepository empresaRepository;
    private final CajaService cajaService;
    private final ValeCreditoPdfService valeCreditoPdfService;

    public ClienteController(ClienteRepository clienteRepository, EmpresaRepository empresaRepository,
                             CajaService cajaService, ValeCreditoPdfService valeCreditoPdfService) {
        this.clienteRepository = clienteRepository;
        this.empresaRepository = empresaRepository;
        this.cajaService = cajaService;
        this.valeCreditoPdfService = valeCreditoPdfService;
    }

    @PostMapping
    public ResponseEntity<?> crearCliente(@RequestBody Cliente body) {
        AuthPrincipal p = SecurityUtils.actual();

        if (body.getNombre() == null || body.getNombre().isBlank()) {
            throw new NegocioException("El nombre es obligatorio.");
        }
        if (body.getRut() == null || body.getRut().isBlank()) {
            throw new NegocioException("El RUT es obligatorio.");
        }
        String rut = body.getRut().trim();
        if (clienteRepository.findByRutAndEmpresaId(rut, p.empresaId()).isPresent()) {
            throw new ConflictoException("Ya existe un cliente con ese RUT.");
        }
        double limite = body.getLimiteCredito() != null ? body.getLimiteCredito() : 0.0;
        if (limite < 0 || Double.isNaN(limite)) {
            throw new NegocioException("El límite de crédito no es válido.");
        }

        Empresa empresa = empresaRepository.findById(p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));

        Cliente nuevo = new Cliente();           // se copian solo campos permitidos (nada de id/deuda desde el cliente)
        nuevo.setEmpresa(empresa);
        nuevo.setNombre(body.getNombre().trim());
        nuevo.setRut(rut);
        nuevo.setTelefono(body.getTelefono());
        nuevo.setEmail(body.getEmail());
        nuevo.setLimiteCredito(limite);
        nuevo.setDeudaActual(0.0);
        return ResponseEntity.ok(clienteRepository.save(nuevo));
    }

    @GetMapping
    public ResponseEntity<?> obtenerTodosLosClientes() {
        AuthPrincipal p = SecurityUtils.actual();
        List<Cliente> clientes = clienteRepository.findByEmpresaId(p.empresaId()).stream()
                .filter(c -> c.getRut() != null && !c.getRut().trim().isEmpty())
                .toList();
        return ResponseEntity.ok(clientes);
    }

    /** Solo ADMIN (ver SecurityConfig). */
    @PostMapping("/reiniciar-creditos")
    @Transactional
    public ResponseEntity<?> reiniciarCreditos() {
        AuthPrincipal p = SecurityUtils.actual();
        List<Cliente> clientes = clienteRepository.findByEmpresaId(p.empresaId());
        for (Cliente c : clientes) {
            if (c.getDeudaActual() != null && c.getDeudaActual() > 0) {
                c.setDeudaActual(0.0);
            }
        }
        clienteRepository.saveAll(clientes);
        return ResponseEntity.ok(Map.of("message", "Todos los créditos han sido reiniciados a 0 exitosamente."));
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscarCliente(@RequestParam String termino) {
        AuthPrincipal p = SecurityUtils.actual();
        if (termino == null || termino.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        String original = termino.trim();
        String limpio = original.replaceAll("[.-]", "");
        return ResponseEntity.ok(clienteRepository.buscarPorTerminoYEmpresa(original, limpio, p.empresaId()));
    }

    @PostMapping("/{id}/abonar")
    @Transactional
    public ResponseEntity<?> abonarDeuda(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        AuthPrincipal p = SecurityUtils.actual();

        Cliente cliente = clienteRepository.findByIdAndEmpresaId(id, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado."));

        double monto;
        try {
            monto = Double.parseDouble(String.valueOf(payload.get("monto")));
        } catch (NumberFormatException e) {
            throw new NegocioException("El monto del abono no es válido.");
        }
        if (monto <= 0 || Double.isNaN(monto) || Double.isInfinite(monto)) {
            throw new NegocioException("El monto del abono debe ser mayor a 0.");
        }
        double deuda = cliente.getDeudaActual() != null ? cliente.getDeudaActual() : 0.0;
        if (monto > deuda) {
            throw new NegocioException("El monto del abono no puede ser mayor a la deuda actual.");
        }
        Object mp = payload.get("metodoPago");
        String metodoPago = mp != null ? mp.toString().toUpperCase() : "EFECTIVO";

        // Primero la caja: si no hay turno abierto, falla antes de tocar la deuda
        cajaService.registrarAbono(p, BigDecimal.valueOf(monto), metodoPago);

        cliente.setDeudaActual(deuda - monto);
        Cliente actualizado = clienteRepository.save(cliente);

        String local = actualizado.getEmpresa() != null ? actualizado.getEmpresa().getRazonSocial() : "Local";
        valeCreditoPdfService.generarGuardarYImprimirAbono(
                actualizado.getNombre(), actualizado.getRut(), monto, deuda,
                actualizado.getDeudaActual(), metodoPago, local);

        return ResponseEntity.ok(actualizado);
    }
}
