package com.posbarlacteo.PosBarLacteo.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.dte.CredencialesDte;
import com.posbarlacteo.PosBarLacteo.dte.DatosEmisor;
import com.posbarlacteo.PosBarLacteo.dte.DteException;
import com.posbarlacteo.PosBarLacteo.dte.DteProveedor;
import com.posbarlacteo.PosBarLacteo.dte.DteProveedorRegistry;
import com.posbarlacteo.PosBarLacteo.dte.LineaDte;
import com.posbarlacteo.PosBarLacteo.dte.ResultadoDte;
import com.posbarlacteo.PosBarLacteo.dte.ResultadoPrueba;
import com.posbarlacteo.PosBarLacteo.dte.SolicitudDte;
import com.posbarlacteo.PosBarLacteo.dto.PagoRequest;
import com.posbarlacteo.PosBarLacteo.exception.AccesoDenegadoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.ConfiguracionDte;
import com.posbarlacteo.PosBarLacteo.model.DocumentoTributario;
import com.posbarlacteo.PosBarLacteo.model.Venta;
import com.posbarlacteo.PosBarLacteo.model.VentaDetalle;
import com.posbarlacteo.PosBarLacteo.repository.ConfiguracionDteRepository;
import com.posbarlacteo.PosBarLacteo.repository.DocumentoTributarioRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.VentaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/**
 * Orquesta la facturación electrónica de cada venta sin conocer a ningún proveedor en particular: elige el
 * proveedor de la empresa en {@link DteProveedorRegistry} y le delega la emisión.
 *
 * Principio: la venta ya está cobrada y guardada. Si el proveedor falla, la venta NO se pierde ni se cobra de
 * nuevo: el documento queda PENDIENTE (reintentable) o REVISAR (hay que verificarlo en el portal del proveedor).
 */
@Service
public class DteService {

    private static final Logger log = LoggerFactory.getLogger(DteService.class);
    private static final Duration EMISION_ATASCADA = Duration.ofMinutes(10);

    /** Resultado para el frontend. pdf puede venir vacío; estado: EMITIDO | PENDIENTE | REVISAR | EN_CURSO | NO_APLICA. */
    public record Emision(String estado, Long folio, String aviso, String pdf) {
    }

    private final ConfiguracionDteRepository configDteRepository;
    private final DocumentoTributarioRepository documentoRepository;
    private final VentaRepository ventaRepository;
    private final VoucherService voucherService;
    private final ComprobanteService comprobanteService;
    private final DteProveedorRegistry registry;
    private final EmpresaRepository empresaRepository;

    public DteService(ConfiguracionDteRepository configDteRepository, DocumentoTributarioRepository documentoRepository,
                      VentaRepository ventaRepository, VoucherService voucherService,
                      ComprobanteService comprobanteService, DteProveedorRegistry registry,
                      EmpresaRepository empresaRepository) {
        this.configDteRepository = configDteRepository;
        this.documentoRepository = documentoRepository;
        this.ventaRepository = ventaRepository;
        this.voucherService = voucherService;
        this.comprobanteService = comprobanteService;
        this.registry = registry;
        this.empresaRepository = empresaRepository;
    }

    /** Datos tributarios de la empresa tal como quedaron en su registro (null si no se encuentra). */
    private DatosEmisor datosEmisor(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .map(e -> new DatosEmisor(e.getRazonSocial(), e.getGiro(), e.getDireccion(), e.getComuna()))
                .orElse(null);
    }

    // ------------------------------------------------------------------------------------------
    // Venta nueva
    // ------------------------------------------------------------------------------------------

    /**
     * Se llama justo después de registrar la venta. Siempre devuelve algo imprimible: la boleta del proveedor si
     * salió, o el comprobante interno con un aviso si no corresponde emitir o si falló.
     */
    public Emision documentoDeVenta(Venta venta, PagoRequest request, Collection<String> metodosPago, AuthPrincipal p) {
        ConfiguracionDte config = null;
        String errorConfig = null;
        try {
            config = configDteRepository.findByEmpresaId(p.empresaId()).orElse(null);
        } catch (RuntimeException e) {
            log.error("No se pudo leer la configuración DTE de la empresa {}: {}", p.empresaId(), e.getMessage());
            errorConfig = "No se pudo leer la configuración de facturación (revisa DTE_ENCRYPTION_KEY en el servidor).";
        }

        if (errorConfig == null && (config == null || "ninguno".equalsIgnoreCase(config.getProveedor())
                || !config.isActivo() || !debeEmitir(config, metodosPago))) {
            return new Emision("NO_APLICA", null, null, voucherLocal(venta, request));
        }

        Emision e = emitir(DocumentoTributario.BOLETA, p.empresaId(), venta.getId(), p.usuario(),
                construirSolicitud(venta, null, null, null), config, errorConfig, false);
        if (e.pdf() != null && !e.pdf().isBlank()) {
            return e;
        }
        return new Emision(e.estado(), e.folio(), e.aviso(), voucherLocal(venta, request));
    }

    private boolean debeEmitir(ConfiguracionDte config, Collection<String> metodos) {
        for (String m : metodos) {
            if (("EFECTIVO".equalsIgnoreCase(m) && config.isEmitirBoletaEfectivo())
                    || ("TARJETA".equalsIgnoreCase(m) && config.isEmitirBoletaTarjeta())
                    || ("TRANSFERENCIA".equalsIgnoreCase(m) && config.isEmitirBoletaTransferencia())) {
                return true; // el fiado (CREDITO) nunca emite en el momento de la venta
            }
        }
        return false;
    }

    private String voucherLocal(Venta venta, PagoRequest request) {
        try {
            return voucherService.generarVoucherBase64(venta, request != null ? request.getItems() : null);
        } catch (Exception e) {
            log.error("Error generando voucher local para venta {}: {}", venta.getId(), e.getMessage(), e);
            return "";
        }
    }

    // ------------------------------------------------------------------------------------------
    // Reintentos y consultas
    // ------------------------------------------------------------------------------------------

    /** Reintenta una boleta PENDIENTE. Una REVISAR solo la reintenta un administrador que ya verificó en el portal. */
    public Emision reintentarBoleta(AuthPrincipal p, Long ventaId, boolean forzar) {
        Venta venta = ventaRepository.findByIdAndEmpresaId(ventaId, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada."));
        if ("ANULADA".equals(venta.getEstado())) {
            throw new NegocioException("La venta está anulada: ya no corresponde emitir su boleta.");
        }
        if (forzar && !p.esAdmin()) {
            throw new AccesoDenegadoException("Solo un administrador puede reintentar una boleta marcada para revisión.");
        }
        DocumentoTributario doc = documentoRepository
                .findByEmpresaIdAndVentaIdAndTipoDte(p.empresaId(), ventaId, DocumentoTributario.BOLETA)
                .orElseThrow(() -> new NegocioException("Esta venta no tiene una boleta electrónica pendiente."));
        if (DocumentoTributario.EMITIDO.equals(doc.getEstado())) {
            throw new NegocioException("La boleta ya fue emitida (folio " + doc.getFolio() + ").");
        }

        ConfiguracionDte config = null;
        String errorConfig = null;
        try {
            config = configDteRepository.findByEmpresaId(p.empresaId()).orElse(null);
        } catch (RuntimeException e) {
            errorConfig = "No se pudo leer la configuración de facturación (revisa DTE_ENCRYPTION_KEY en el servidor).";
        }
        return emitir(DocumentoTributario.BOLETA, p.empresaId(), ventaId, p.usuario(),
                construirSolicitud(venta, null, null, null), config, errorConfig, forzar);
    }

    /** Copia del PDF de la boleta emitida (si el almacenamiento de documentos está configurado). */
    public String pdfBoleta(AuthPrincipal p, Long ventaId) {
        ventaRepository.findByIdAndEmpresaId(ventaId, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada."));
        DocumentoTributario doc = documentoRepository
                .findByEmpresaIdAndVentaIdAndTipoDte(p.empresaId(), ventaId, DocumentoTributario.BOLETA)
                .orElseThrow(() -> new NegocioException("Esta venta no tiene boleta electrónica."));
        if (doc.getPdfKey() == null || doc.getPdfKey().isBlank()) {
            throw new NegocioException("No hay una copia guardada del PDF de esta boleta. Búscala en el portal de tu proveedor (folio "
                    + (doc.getFolio() != null ? doc.getFolio() : "sin registrar") + ").");
        }
        return Base64.getEncoder().encodeToString(comprobanteService.leer(doc.getPdfKey()));
    }

    /** Documentos que requieren atención (para que el administrador los vea). */
    public List<Map<String, Object>> pendientes(Long empresaId) {
        List<Map<String, Object>> salida = new ArrayList<>();
        for (DocumentoTributario d : documentoRepository.findByEmpresaIdAndEstadoInOrderByIdDesc(
                empresaId, Set.of(DocumentoTributario.PENDIENTE, DocumentoTributario.REVISAR, DocumentoTributario.EMITIENDO))) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", d.getId());
            f.put("ventaId", d.getVentaId());
            f.put("tipoDte", d.getTipoDte());
            f.put("estado", d.getEstado());
            f.put("intentos", d.getIntentos());
            f.put("ultimoError", d.getUltimoError());
            f.put("fecha", d.getFechaCreacion());
            salida.add(f);
        }
        return salida;
    }

    // ------------------------------------------------------------------------------------------
    // Nota de crédito al anular
    // ------------------------------------------------------------------------------------------

    /** Emite (o reintenta) la nota de crédito de una venta anulada que tenía boleta electrónica. */
    public Emision notaCreditoDeVentaAnulada(AuthPrincipal p, Long ventaId) {
        Venta venta = ventaRepository.findByIdAndEmpresaId(ventaId, p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada."));
        if (!"ANULADA".equals(venta.getEstado())) {
            throw new NegocioException("La venta no está anulada.");
        }
        DocumentoTributario boleta = documentoRepository
                .findByEmpresaIdAndVentaIdAndTipoDte(p.empresaId(), ventaId, DocumentoTributario.BOLETA).orElse(null);
        if (boleta == null || DocumentoTributario.CANCELADO.equals(boleta.getEstado())) {
            return new Emision("NO_APLICA", null, null, null);
        }
        if (DocumentoTributario.PENDIENTE.equals(boleta.getEstado())) {
            // La boleta nunca salió: no hay nada que anular, y se evita que un reintento la emita después
            boleta.setEstado(DocumentoTributario.CANCELADO);
            boleta.setUltimoError("Venta anulada antes de emitir la boleta.");
            documentoRepository.save(boleta);
            ventaRepository.actualizarDte(ventaId, p.empresaId(), DocumentoTributario.CANCELADO, null);
            return new Emision("NO_APLICA", null, "La boleta de esta venta nunca se emitió, por lo que no requiere nota de crédito.", null);
        }
        if (!DocumentoTributario.EMITIDO.equals(boleta.getEstado())) {
            return new Emision(DocumentoTributario.REVISAR, null,
                    "No se sabe si la boleta de esta venta llegó a emitirse. Verifícalo en el portal de tu proveedor y, si salió, emite la nota de crédito allí.", null);
        }

        ConfiguracionDte config = null;
        String errorConfig = null;
        try {
            config = configDteRepository.findByEmpresaId(p.empresaId()).orElse(null);
        } catch (RuntimeException e) {
            errorConfig = "No se pudo leer la configuración de facturación (revisa DTE_ENCRYPTION_KEY en el servidor).";
        }
        if (config != null && boleta.getProveedor() != null && !boleta.getProveedor().equalsIgnoreCase(config.getProveedor())) {
            return new Emision(DocumentoTributario.PENDIENTE, null,
                    "La boleta se emitió con " + boleta.getProveedor() + " pero hoy la facturación está configurada con "
                            + config.getProveedor() + ". Emite la nota de crédito en el portal de " + boleta.getProveedor() + ".", null);
        }
        LocalDate fechaBoleta = boleta.getFechaEmision() != null ? boleta.getFechaEmision().toLocalDate() : LocalDate.now();
        return emitir(DocumentoTributario.NOTA_CREDITO, p.empresaId(), ventaId, p.usuario(),
                construirSolicitud(venta, boleta.getFolio(), fechaBoleta, "Anula boleta N° " + boleta.getFolio() + " (venta N° " + ventaId + ")"),
                config, errorConfig, false);
    }

    // ------------------------------------------------------------------------------------------
    // Prueba de conexión
    // ------------------------------------------------------------------------------------------

    public ResultadoPrueba probarConexion(Long empresaId, ConfiguracionDte payload) {
        DteProveedor proveedor = registry.buscar(payload.getProveedor())
                .orElseThrow(() -> new NegocioException("Elige un proveedor de facturación para probar la conexión."));
        String key = payload.getApiKey();
        String secret = payload.getApiSecret();
        String rut = payload.getRutEmisor();
        String ambiente = payload.getAmbiente();
        if (key == null || key.isBlank()) {
            ConfiguracionDte guardada;
            try {
                guardada = configDteRepository.findByEmpresaId(empresaId).orElse(null);
            } catch (RuntimeException e) {
                throw new NegocioException("No se pudo leer la API Key guardada (revisa DTE_ENCRYPTION_KEY en el servidor). Ingrésala de nuevo.");
            }
            key = guardada != null ? guardada.getApiKey() : null;
            if (secret == null || secret.isBlank()) secret = guardada != null ? guardada.getApiSecret() : null;
        }
        if (key == null || key.isBlank()) {
            return ResultadoPrueba.rechazada("La API Key no puede estar vacía.");
        }
        return proveedor.probarConexion(new CredencialesDte(key.trim(), secret, rut, ambiente, datosEmisor(empresaId)));
    }

    // ------------------------------------------------------------------------------------------
    // Núcleo de emisión (boleta y nota de crédito)
    // ------------------------------------------------------------------------------------------

    private Emision emitir(int tipo, Long empresaId, Long ventaId, String usuario, SolicitudDte solicitud,
                           ConfiguracionDte config, String errorConfig, boolean forzar) {
        DocumentoTributario doc = obtenerOCrear(empresaId, ventaId, tipo, usuario);
        String etiqueta = tipo == DocumentoTributario.BOLETA ? "La boleta" : "La nota de crédito";

        if (DocumentoTributario.EMITIDO.equals(doc.getEstado())) {
            return new Emision(DocumentoTributario.EMITIDO, doc.getFolio(), etiqueta + " ya estaba emitida.", null);
        }
        if (DocumentoTributario.CANCELADO.equals(doc.getEstado())) {
            return new Emision("NO_APLICA", null, etiqueta + " fue cancelada porque la venta se anuló.", null);
        }
        if (DocumentoTributario.EMITIENDO.equals(doc.getEstado())) {
            boolean atascada = doc.getFechaActualizacion() != null
                    && Duration.between(doc.getFechaActualizacion(), LocalDateTime.now()).compareTo(EMISION_ATASCADA) > 0;
            if (!atascada) {
                return new Emision("EN_CURSO", null, etiqueta + " se está emitiendo en este momento. Espera unos segundos.", null);
            }
            // Un proceso quedó a medio camino: no se sabe si el proveedor alcanzó a emitir
            doc.setEstado(DocumentoTributario.REVISAR);
            doc.setUltimoError("La emisión quedó interrumpida; verifica en el portal del proveedor si el documento salió.");
            documentoRepository.save(doc);
            sincronizarVenta(tipo, empresaId, ventaId, doc);
        }
        if (DocumentoTributario.REVISAR.equals(doc.getEstado()) && !forzar) {
            return new Emision(DocumentoTributario.REVISAR, doc.getFolio(),
                    etiqueta + " quedó para revisión: " + nz(doc.getUltimoError())
                            + " Verifícalo en el portal de tu proveedor; si NO salió, un administrador puede forzar el reintento.", null);
        }

        // Solo un proceso puede emitir a la vez
        Set<String> permitidos = forzar
                ? Set.of(DocumentoTributario.PENDIENTE, DocumentoTributario.REVISAR)
                : Set.of(DocumentoTributario.PENDIENTE);
        if (documentoRepository.reclamar(doc.getId(), LocalDateTime.now(), permitidos) == 0) {
            return new Emision("EN_CURSO", null, etiqueta + " ya se está procesando.", null);
        }
        doc.setIntentos(doc.getIntentos() + 1);
        doc.setFechaActualizacion(LocalDateTime.now());
        if (tipo == DocumentoTributario.BOLETA) {
            ventaRepository.actualizarDte(ventaId, empresaId, DocumentoTributario.PENDIENTE, null);
        }

        try {
            if (errorConfig != null) {
                throw new DteException(errorConfig);
            }
            if (config == null || "ninguno".equalsIgnoreCase(config.getProveedor())) {
                throw new DteException("La facturación electrónica no está configurada.");
            }
            DteProveedor proveedor = registry.buscar(config.getProveedor())
                    .orElseThrow(() -> new DteException("El proveedor \"" + config.getProveedor() + "\" no está soportado."));
            if (config.getApiKey() == null || config.getApiKey().isBlank()) {
                throw new DteException("Falta la API Key del proveedor de facturación.");
            }
            if (config.getRutEmisor() == null || config.getRutEmisor().isBlank()) {
                throw new DteException("Falta el RUT emisor en la configuración de facturación.");
            }
            CredencialesDte cred = new CredencialesDte(config.getApiKey(), config.getApiSecret(), config.getRutEmisor(), config.getAmbiente(), datosEmisor(empresaId));

            ResultadoDte r = tipo == DocumentoTributario.BOLETA
                    ? proveedor.emitirBoleta(solicitud, cred)
                    : proveedor.emitirNotaCredito(solicitud, cred);

            doc.setProveedor(proveedor.codigo());
            doc.setFolio(r.folio());
            doc.setEstado(DocumentoTributario.EMITIDO);
            doc.setUltimoError(null);
            doc.setFechaEmision(LocalDateTime.now());
            if (r.pdfBase64() != null && !r.pdfBase64().isBlank()) {
                guardarCopia(doc, empresaId, ventaId, tipo, r);
            }
            documentoRepository.save(doc);
            sincronizarVenta(tipo, empresaId, ventaId, doc);

            String aviso = (r.pdfBase64() == null || r.pdfBase64().isBlank())
                    ? etiqueta + " se emitió (folio " + r.folio() + ") pero no se pudo obtener su PDF; descárgalo desde el portal del proveedor."
                    : null;
            return new Emision(DocumentoTributario.EMITIDO, r.folio(), aviso, r.pdfBase64());

        } catch (DteException e) {
            return registrarFallo(doc, tipo, empresaId, ventaId, etiqueta, e.getMessage(), e.posibleDuplicado());
        } catch (RuntimeException e) {
            log.error("Error inesperado emitiendo documento {} de la venta {}: {}", tipo, ventaId, e.getMessage(), e);
            // Podría haberse emitido antes del error: se deja para revisión en vez de arriesgar un duplicado
            return registrarFallo(doc, tipo, empresaId, ventaId, etiqueta, "Error inesperado: " + e.getMessage(), true);
        }
    }

    private Emision registrarFallo(DocumentoTributario doc, int tipo, Long empresaId, Long ventaId, String etiqueta,
                                   String mensaje, boolean posibleDuplicado) {
        doc.setEstado(posibleDuplicado ? DocumentoTributario.REVISAR : DocumentoTributario.PENDIENTE);
        doc.setUltimoError(recortar(mensaje));
        documentoRepository.save(doc);
        sincronizarVenta(tipo, empresaId, ventaId, doc);
        log.warn("Documento DTE {} de la venta {} quedó {}: {}", tipo, ventaId, doc.getEstado(), mensaje);

        String aviso = posibleDuplicado
                ? etiqueta + " podría haberse emitido: " + mensaje + " NO la reemitas sin verificarlo en el portal de tu proveedor."
                : etiqueta + " no se pudo emitir: " + mensaje + " La venta quedó registrada; podrás reintentar desde Movimientos.";
        return new Emision(doc.getEstado(), null, aviso, null);
    }

    private void guardarCopia(DocumentoTributario doc, Long empresaId, Long ventaId, int tipo, ResultadoDte r) {
        try {
            String key = "empresas/" + empresaId + "/dte/" + ventaId + "-" + tipo + "-" + (r.folio() != null ? r.folio() : "s-f") + ".pdf";
            doc.setPdfKey(comprobanteService.guardarBytes(key, Base64.getDecoder().decode(r.pdfBase64()), "application/pdf"));
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar la copia del PDF de la venta {}: {}", ventaId, e.getMessage());
        }
    }

    private void sincronizarVenta(int tipo, Long empresaId, Long ventaId, DocumentoTributario doc) {
        if (tipo == DocumentoTributario.BOLETA) {
            ventaRepository.actualizarDte(ventaId, empresaId, doc.getEstado(), doc.getFolio());
        }
    }

    private DocumentoTributario obtenerOCrear(Long empresaId, Long ventaId, int tipo, String usuario) {
        return documentoRepository.findByEmpresaIdAndVentaIdAndTipoDte(empresaId, ventaId, tipo).orElseGet(() -> {
            DocumentoTributario d = new DocumentoTributario();
            d.setEmpresaId(empresaId);
            d.setVentaId(ventaId);
            d.setTipoDte(tipo);
            d.setEstado(DocumentoTributario.PENDIENTE);
            d.setCreadoPor(usuario);
            try {
                return documentoRepository.save(d);
            } catch (DataIntegrityViolationException e) {
                // Otro proceso lo creó al mismo tiempo
                return documentoRepository.findByEmpresaIdAndVentaIdAndTipoDte(empresaId, ventaId, tipo).orElseThrow(() -> e);
            }
        });
    }

    private SolicitudDte construirSolicitud(Venta venta, Long folioRef, LocalDate fechaRef, String razon) {
        List<LineaDte> lineas = new ArrayList<>();
        if (venta.getDetalles() != null) {
            for (VentaDetalle d : venta.getDetalles()) {
                double pu = d.getPrecioUnitario() != null ? d.getPrecioUnitario()
                        : (d.getProducto() != null && d.getProducto().getPrecio() != null ? d.getProducto().getPrecio() : 0.0);
                double cant = d.getCantidad() != null ? d.getCantidad() : 0;
                String nombre = d.getProducto() != null && d.getProducto().getDescripcion() != null
                        ? d.getProducto().getDescripcion() : "Item";
                lineas.add(new LineaDte(nombre, cant, Math.round(pu), Math.round(pu * cant)));
            }
        }
        long total = Math.round(venta.getTotal() != null ? venta.getTotal() : 0.0);
        return new SolicitudDte(venta.getId(), LocalDate.now(), total, lineas, folioRef, fechaRef, razon);
    }

    private static String recortar(String s) {
        if (s == null) return null;
        return s.length() > 480 ? s.substring(0, 480) + "…" : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
