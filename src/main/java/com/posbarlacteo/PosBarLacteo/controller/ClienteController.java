package com.posbarlacteo.PosBarLacteo.controller;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.posbarlacteo.PosBarLacteo.model.MovimientoCliente;
import com.posbarlacteo.PosBarLacteo.repository.ClienteRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.MovimientoClienteRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.CajaService;
import com.posbarlacteo.PosBarLacteo.service.CuentaClienteService;
import com.posbarlacteo.PosBarLacteo.service.DteService;
import com.posbarlacteo.PosBarLacteo.service.EstadoCuentaPdfService;
import com.posbarlacteo.PosBarLacteo.service.MailService;
import com.posbarlacteo.PosBarLacteo.service.ValeCreditoPdfService;

/** Todos los clientes pertenecen a la empresa del token; ningún endpoint recibe empresaId. */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteRepository clienteRepository;
    private final EmpresaRepository empresaRepository;
    private final CajaService cajaService;
    private final ValeCreditoPdfService valeCreditoPdfService;
    private final CuentaClienteService cuentaClienteService;
    private final MovimientoClienteRepository movimientoClienteRepository;
    private final EstadoCuentaPdfService estadoCuentaPdfService;
    private final MailService mailService;
    private final DteService dteService;
    private final TransactionTemplate transaccion;

    public ClienteController(ClienteRepository clienteRepository, EmpresaRepository empresaRepository,
                             CajaService cajaService, ValeCreditoPdfService valeCreditoPdfService,
                             CuentaClienteService cuentaClienteService,
                             MovimientoClienteRepository movimientoClienteRepository,
                             EstadoCuentaPdfService estadoCuentaPdfService, MailService mailService,
                             DteService dteService, TransactionTemplate transaccion) {
        this.clienteRepository = clienteRepository;
        this.empresaRepository = empresaRepository;
        this.cajaService = cajaService;
        this.valeCreditoPdfService = valeCreditoPdfService;
        this.cuentaClienteService = cuentaClienteService;
        this.movimientoClienteRepository = movimientoClienteRepository;
        this.estadoCuentaPdfService = estadoCuentaPdfService;
        this.mailService = mailService;
        this.dteService = dteService;
        this.transaccion = transaccion;
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

    /** Solo ADMIN (ver SecurityConfig). Deja un ajuste en el estado de cuenta de cada cliente afectado. */
    @PostMapping("/reiniciar-creditos")
    @Transactional
    public ResponseEntity<?> reiniciarCreditos() {
        AuthPrincipal p = SecurityUtils.actual();
        List<Cliente> clientes = clienteRepository.findByEmpresaId(p.empresaId());
        for (Cliente c : clientes) {
            double deuda = c.getDeudaActual() != null ? c.getDeudaActual() : 0.0;
            if (deuda > 0 && clienteRepository.abonarDeuda(c.getId(), p.empresaId(), deuda) > 0) {
                cuentaClienteService.registrar(c, p, MovimientoCliente.AJUSTE, -deuda, null, null,
                        "Reinicio de créditos por el administrador");
            }
        }
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

    /**
     * Registra el pago (total o parcial) de la deuda de un cliente. La parte de base de datos va en una sola
     * transacción; la boleta electrónica (llamada externa al proveedor) se emite DESPUÉS de confirmarla, para no
     * mantener una transacción abierta mientras se espera al proveedor ni arriesgar un rollback con la boleta ya emitida.
     */
    @PostMapping("/{id}/abonar")
    public ResponseEntity<?> abonarDeuda(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        AuthPrincipal p = SecurityUtils.actual();

        double monto;
        try {
            monto = Double.parseDouble(String.valueOf(payload.get("monto")));
        } catch (NumberFormatException e) {
            throw new NegocioException("El monto del abono no es válido.");
        }
        if (monto <= 0 || Double.isNaN(monto) || Double.isInfinite(monto)) {
            throw new NegocioException("El monto del abono debe ser mayor a 0.");
        }
        Object mp = payload.get("metodoPago");
        String metodoPago = mp != null ? mp.toString().toUpperCase() : "EFECTIVO";
        final double montoFinal = monto;

        Map<String, Object> hecho = transaccion.execute(status -> {
            Cliente cliente = clienteRepository.findByIdAndEmpresaId(id, p.empresaId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado."));
            double deudaAntes = cliente.getDeudaActual() != null ? cliente.getDeudaActual() : 0.0;

            // Primero la caja: si no hay turno abierto, falla antes de tocar la deuda
            cajaService.registrarAbono(p, BigDecimal.valueOf(montoFinal), metodoPago, cliente.getId(), cliente.getNombre());

            // Atómico: solo descuenta si la deuda actual alcanza (si no, se revierte también el registro de caja)
            if (clienteRepository.abonarDeuda(cliente.getId(), p.empresaId(), montoFinal) == 0) {
                throw new NegocioException("El monto del abono no puede ser mayor a la deuda actual.");
            }
            MovimientoCliente mov = cuentaClienteService.registrar(cliente, p, MovimientoCliente.ABONO, -montoFinal,
                    null, metodoPago, "Abono del cliente");

            double saldo = clienteRepository.saldoActual(cliente.getId(), p.empresaId());
            cliente.setDeudaActual(saldo);

            String local = cliente.getEmpresa() != null ? cliente.getEmpresa().getRazonSocial() : "Local";
            String comprobante = valeCreditoPdfService.generarAbonoBase64(
                    cliente.getNombre(), cliente.getRut(), montoFinal, deudaAntes, saldo, metodoPago, local);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("cliente", cliente);
            r.put("comprobantePdf", comprobante == null ? "" : comprobante);
            r.put("movimientoId", mov.getId());
            r.put("nombreCliente", cliente.getNombre());
            return r;
        });

        // Ya confirmado: ahora la boleta (si la empresa la tiene activada para pagos de deuda)
        Long movimientoId = (Long) hecho.remove("movimientoId");
        String nombre = (String) hecho.remove("nombreCliente");
        DteService.Emision dte = dteService.documentoDeAbono(movimientoId, monto, metodoPago, nombre, p);
        hecho.put("boletaPdf", dte.pdf() == null ? "" : dte.pdf());
        hecho.put("dteEstado", dte.estado());
        hecho.put("dteFolio", dte.folio());
        hecho.put("dteAviso", dte.aviso() == null ? "" : dte.aviso());
        return ResponseEntity.ok(hecho);
    }

    /** Envía por correo el estado de cuenta (últimos 100 movimientos). Body: {"email": "..."} (opcional: usa el del cliente). */
    @PostMapping("/{id}/estado-cuenta/enviar")
    @Transactional(readOnly = true)
    public ResponseEntity<?> enviarEstadoCuenta(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        AuthPrincipal p = SecurityUtils.actual();
        Cliente c = clienteRepository.findByIdAndEmpresaId(id, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado."));
        String destino = body != null && body.get("email") != null && !body.get("email").isBlank()
                ? body.get("email") : c.getEmail();
        if (destino == null || destino.isBlank()) {
            throw new NegocioException("El cliente no tiene correo registrado. Ingresa uno para enviar el estado de cuenta.");
        }
        destino = MailService.validarDestino(destino);

        String empresa = c.getEmpresa() != null ? c.getEmpresa().getRazonSocial() : "KIPIPOS";
        List<MovimientoCliente> movs = movimientoClienteRepository
                .porCliente(id, p.empresaId(), PageRequest.of(0, 100)).getContent();
        byte[] pdf;
        try {
            pdf = estadoCuentaPdfService.generar(empresa, c, movs);
        } catch (Exception e) {
            throw new NegocioException("No se pudo generar el estado de cuenta.");
        }
        mailService.enviarConAdjunto(destino, empresa, "Estado de cuenta - " + empresa,
                "Hola " + c.getNombre() + ",\n\nAdjuntamos tu estado de cuenta actualizado en " + empresa + ".\n\nSaludos.",
                "estado-de-cuenta.pdf", pdf);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Estado de cuenta enviado a " + destino));
    }

    /** Estado de cuenta: resumen del cliente y sus movimientos (cargos, abonos, anulaciones, ajustes). */
    @GetMapping("/{id}/movimientos")
    @Transactional(readOnly = true)
    public Map<String, Object> estadoDeCuenta(@PathVariable Long id,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "30") int size) {
        AuthPrincipal p = SecurityUtils.actual();
        Cliente c = clienteRepository.findByIdAndEmpresaId(id, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado."));
        Page<MovimientoCliente> pagina = movimientoClienteRepository.porCliente(
                id, p.empresaId(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));

        double limite = c.getLimiteCredito() != null ? c.getLimiteCredito() : 0.0;
        double deuda = c.getDeudaActual() != null ? c.getDeudaActual() : 0.0;

        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("id", c.getId());
        cliente.put("nombre", c.getNombre());
        cliente.put("rut", c.getRut());
        cliente.put("telefono", c.getTelefono());
        cliente.put("email", c.getEmail());
        cliente.put("limiteCredito", limite);
        cliente.put("deudaActual", deuda);
        cliente.put("disponible", Math.max(0, limite - deuda));

        List<Map<String, Object>> filas = pagina.getContent().stream().map(m -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", m.getId());
            f.put("fecha", m.getFechaHora().toString());
            f.put("tipo", m.getTipo());
            f.put("monto", m.getMonto());
            f.put("saldoResultante", m.getSaldoResultante());
            f.put("ventaId", m.getVentaId());
            f.put("metodoPago", m.getMetodoPago());
            f.put("motivo", m.getMotivo());
            f.put("usuario", m.getUsuarioNombre());
            return f;
        }).toList();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cliente", cliente);
        r.put("content", filas);
        r.put("totalPages", pagina.getTotalPages());
        r.put("totalElements", pagina.getTotalElements());
        return r;
    }

    /** Solo ADMIN (ver SecurityConfig): límite de crédito, teléfono y correo del cliente. */
    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<?> actualizarCliente(@PathVariable Long id, @RequestBody Cliente body) {
        AuthPrincipal p = SecurityUtils.actual();
        Cliente c = clienteRepository.findByIdAndEmpresaId(id, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado."));
        if (body.getLimiteCredito() != null) {
            double limite = body.getLimiteCredito();
            if (limite < 0 || Double.isNaN(limite) || Double.isInfinite(limite)) {
                throw new NegocioException("El límite de crédito no es válido.");
            }
            c.setLimiteCredito(limite);
        }
        if (body.getTelefono() != null) c.setTelefono(body.getTelefono().trim());
        if (body.getEmail() != null) c.setEmail(body.getEmail().trim());
        return ResponseEntity.ok(clienteRepository.save(c));
    }
}
