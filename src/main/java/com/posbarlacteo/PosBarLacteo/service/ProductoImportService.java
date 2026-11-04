package com.posbarlacteo.PosBarLacteo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.posbarlacteo.PosBarLacteo.dto.ImportarProductosRequest;
import com.posbarlacteo.PosBarLacteo.dto.ImportarProductosRequest.Fila;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Categoria;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.model.MovimientoInventario;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.repository.CategoriaRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/**
 * Carga masiva de productos desde un archivo (Excel/CSV que el navegador ya convirtió en filas) y asignación de
 * códigos internos. Cada fila se valida por separado: una fila con error se informa y se omite, el resto se guarda.
 */
@Service
public class ProductoImportService {

    public static final int MAX_FILAS = 500;
    private static final Set<String> UNIDADES = Set.of("UN", "KG", "GR", "LT");

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final EmpresaRepository empresas;
    private final InventarioService inventario;
    private final CodigoInternoService codigos;
    private final AuditoriaService auditoria;

    public ProductoImportService(ProductoRepository productos, CategoriaRepository categorias, EmpresaRepository empresas,
                                 InventarioService inventario, CodigoInternoService codigos, AuditoriaService auditoria) {
        this.productos = productos;
        this.categorias = categorias;
        this.empresas = empresas;
        this.inventario = inventario;
        this.codigos = codigos;
        this.auditoria = auditoria;
    }

    /** Estado de un envío: contador de códigos internos y cachés, compartidos por todas sus filas. */
    private static final class Contexto {
        long contadorCodigo;
        final Set<String> codigosVistos = new HashSet<>();
        final Map<String, Categoria> categorias = new HashMap<>();
        Empresa empresa;
        AuthPrincipal p;
        ImportarProductosRequest req;
    }

    @Transactional
    public Map<String, Object> importar(AuthPrincipal p, ImportarProductosRequest req) {
        if (req == null || req.getFilas() == null || req.getFilas().isEmpty()) {
            throw new NegocioException("El archivo no trae productos.");
        }
        if (req.getFilas().size() > MAX_FILAS) {
            throw new NegocioException("Máximo " + MAX_FILAS + " productos por envío.");
        }
        Contexto cx = new Contexto();
        cx.p = p;
        cx.req = req;
        cx.empresa = empresas.findById(p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        cx.contadorCodigo = codigos.ultimoNumero(p.empresaId());

        int creados = 0, actualizados = 0, omitidos = 0, errores = 0;
        List<Map<String, Object>> resultados = new ArrayList<>();
        int indice = 0;
        for (Fila f : req.getFilas()) {
            indice++;
            int numero = f.getFila() != null ? f.getFila() : indice;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("fila", numero);
            try {
                String[] res = procesar(cx, f); // [estado, mensaje, codigo]
                r.put("estado", res[0]);
                r.put("mensaje", res[1]);
                r.put("codigo", res[2]);
                switch (res[0]) {
                    case "CREADO" -> creados++;
                    case "ACTUALIZADO" -> actualizados++;
                    default -> omitidos++;
                }
            } catch (NegocioException e) {
                r.put("estado", "ERROR");
                r.put("mensaje", e.getMessage());
                errores++;
            }
            r.put("descripcion", f.getDescripcion() == null ? "" : f.getDescripcion().trim());
            resultados.add(r);
        }

        if (req.isSoloValidar()) {
            // Vista previa: se hizo todo igual, pero no se guarda nada
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } else if (creados + actualizados > 0) {
            auditoria.registrar(p, "IMPORTAR_PRODUCTOS", creados + " creados, " + actualizados + " actualizados, "
                    + omitidos + " omitidos, " + errores + " con error.");
        }

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("simulacion", req.isSoloValidar());
        salida.put("creados", creados);
        salida.put("actualizados", actualizados);
        salida.put("omitidos", omitidos);
        salida.put("errores", errores);
        salida.put("filas", resultados);
        return salida;
    }

    /** Devuelve {estado, mensaje, código}. Lanza NegocioException si la fila no es válida. */
    private String[] procesar(Contexto cx, Fila f) {
        Long empresaId = cx.p.empresaId();

        String descripcion = limpiar(f.getDescripcion());
        if (descripcion.isEmpty()) throw new NegocioException("Falta el nombre del producto.");
        if (descripcion.length() > 255) throw new NegocioException("El nombre supera los 255 caracteres.");

        String codigo = limpiar(f.getCodigoBarras());
        if (codigo.length() > 60) throw new NegocioException("El código de barras supera los 60 caracteres.");
        if (codigo.matches(".*\\s.*")) throw new NegocioException("El código de barras no puede llevar espacios.");
        if (!codigo.isEmpty() && !cx.codigosVistos.add(codigo.toLowerCase(Locale.ROOT))) {
            throw new NegocioException("El código " + codigo + " está repetido en el archivo.");
        }

        String unidad = normalizarUnidad(f.getUnidadMedida());
        Double precio = monto("El precio", f.getPrecio());
        Double costo = monto("El costo", f.getCosto());
        Double stock = cantidad("El stock", f.getStock());
        Double stockCritico = cantidad("El stock crítico", f.getStockCritico());
        String nombreCategoria = limpiar(f.getCategoria());
        if (nombreCategoria.length() > 100) throw new NegocioException("La categoría supera los 100 caracteres.");

        // ¿Ya existe? Por código; si el archivo no trae código, por nombre exacto
        Producto existente = null;
        if (!codigo.isEmpty()) {
            existente = productos.findByCodigoBarrasAndEmpresaId(codigo, empresaId).orElse(null);
        } else {
            List<Producto> iguales = productos.findByEmpresaIdAndActivoTrueAndDescripcionIgnoreCase(empresaId, descripcion);
            if (iguales.size() > 1) {
                throw new NegocioException("Hay " + iguales.size() + " productos llamados \"" + descripcion
                        + "\". Agrega el código de barras en el archivo para saber cuál actualizar.");
            }
            if (iguales.size() == 1) existente = iguales.get(0);
        }

        if (existente != null) {
            if (!cx.req.isActualizarExistentes()) {
                return new String[] {"OMITIDO", "Ya existe; no se modificó.", nz(existente.getCodigoBarras())};
            }
            return actualizar(cx, existente, f, descripcion, codigo, unidad, precio, costo, stock, stockCritico, nombreCategoria);
        }

        if (precio == null) throw new NegocioException("Falta el precio (es obligatorio para productos nuevos).");

        boolean insumo = Boolean.TRUE.equals(f.getEsInsumo());
        Producto nuevo = new Producto();
        nuevo.setEmpresa(cx.empresa);
        nuevo.setActivo(true);
        nuevo.setDescripcion(descripcion);
        nuevo.setPrecio(precio);
        nuevo.setCosto(costo);
        nuevo.setStockCritico(stockCritico != null ? stockCritico : 0.0);
        nuevo.setUnidadMedida(unidad != null ? unidad : "UN");
        nuevo.setEsInsumo(insumo);
        nuevo.setPesable(!insumo && Boolean.TRUE.equals(f.getPesable()));
        nuevo.setStockLegacy(0.0);
        nuevo.setStock(null);
        nuevo.setCategoria(resolverCategoria(cx, nombreCategoria));
        String codigoFinal = codigo.isEmpty() ? nuevoCodigoInterno(cx) : codigo;
        nuevo.setCodigoBarras(codigoFinal);
        Producto guardado = productos.save(nuevo);
        if (stock != null && stock > 0) {
            inventario.cargarStockInicial(cx.p, guardado, stock);
        }
        return new String[] {"CREADO", codigo.isEmpty() ? "Se le asignó un código interno." : "Producto nuevo.", codigoFinal};
    }

    private String[] actualizar(Contexto cx, Producto e, Fila f, String descripcion, String codigo, String unidad,
                                Double precio, Double costo, Double stock, Double stockCritico, String nombreCategoria) {
        List<String> cambios = new ArrayList<>();
        if (!Boolean.TRUE.equals(e.getActivo())) {
            e.setActivo(true);
            cambios.add("reactivado");
        }
        // Si se identificó por código, el nombre del archivo manda; si se identificó por nombre, ya es el mismo
        if (!codigo.isEmpty() && !descripcion.equals(e.getDescripcion())) {
            e.setDescripcion(descripcion);
            cambios.add("nombre");
        }
        if (precio != null && !precio.equals(e.getPrecio())) {
            e.setPrecio(precio);
            cambios.add("precio");
        }
        if (costo != null && !costo.equals(e.getCosto())) {
            e.setCosto(costo);
            cambios.add("costo");
        }
        if (stockCritico != null && !stockCritico.equals(e.getStockCritico())) {
            e.setStockCritico(stockCritico);
            cambios.add("stock crítico");
        }
        if (unidad != null && !unidad.equals(e.getUnidadMedida())) {
            e.setUnidadMedida(unidad);
            cambios.add("unidad");
        }
        if (f.getEsInsumo() != null && !f.getEsInsumo().equals(e.getEsInsumo())) {
            e.setEsInsumo(f.getEsInsumo());
            cambios.add("tipo");
        }
        if (f.getPesable() != null && !Boolean.TRUE.equals(e.getEsInsumo())
                && !f.getPesable().equals(Boolean.TRUE.equals(e.getPesable()))) {
            e.setPesable(f.getPesable());
            cambios.add("granel");
        }
        if (!nombreCategoria.isEmpty()) {
            Categoria nueva = resolverCategoria(cx, nombreCategoria);
            if (nueva != null && (e.getCategoria() == null || !nueva.getId().equals(e.getCategoria().getId()))) {
                e.setCategoria(nueva);
                cambios.add("categoría");
            }
        }
        // Un producto sin código recibe uno interno al actualizarlo, para que se pueda escanear
        if (e.getCodigoBarras() == null || e.getCodigoBarras().isBlank()) {
            e.setCodigoBarras(nuevoCodigoInterno(cx));
            cambios.add("código interno");
        }
        productos.save(e);
        if (cx.req.isAjustarStock() && stock != null) {
            inventario.registrarManual(cx.p, null, e.getId(), MovimientoInventario.AJUSTE, stock,
                    "Importación desde archivo");
            cambios.add("stock");
        }
        if (cambios.isEmpty()) {
            return new String[] {"OMITIDO", "Sin cambios.", nz(e.getCodigoBarras())};
        }
        return new String[] {"ACTUALIZADO", "Cambió: " + String.join(", ", cambios) + ".", nz(e.getCodigoBarras())};
    }

    // ------------------------------------------------------------------------------------------
    // Códigos internos para productos que ya existen
    // ------------------------------------------------------------------------------------------

    /**
     * Asigna un código interno (EAN-13) a los productos activos que no tienen código. Con incluirProvisionales
     * también reemplaza los códigos "PROV-..." que el sistema ponía antes.
     */
    @Transactional
    public Map<String, Object> asignarCodigos(AuthPrincipal p, boolean incluirProvisionales) {
        List<Producto> lista = productos.sinCodigo(p.empresaId(), incluirProvisionales);
        long contador = codigos.ultimoNumero(p.empresaId());
        for (Producto pr : lista) {
            String c;
            do {
                contador++;
                c = codigos.formar(contador);
            } while (productos.findByCodigoBarrasAndEmpresaId(c, p.empresaId()).isPresent());
            pr.setCodigoBarras(c);
        }
        productos.saveAll(lista);
        if (!lista.isEmpty()) {
            auditoria.registrar(p, "CODIGOS_INTERNOS", "Se asignaron " + lista.size() + " códigos internos"
                    + (incluirProvisionales ? " (incluye reemplazo de provisionales)" : "") + ".");
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("asignados", lista.size());
        return r;
    }

    // ------------------------------------------------------------------------------------------

    private String nuevoCodigoInterno(Contexto cx) {
        String c;
        do {
            cx.contadorCodigo++;
            c = codigos.formar(cx.contadorCodigo);
        } while (cx.codigosVistos.contains(c) || productos.findByCodigoBarrasAndEmpresaId(c, cx.p.empresaId()).isPresent());
        cx.codigosVistos.add(c);
        return c;
    }

    private Categoria resolverCategoria(Contexto cx, String nombre) {
        if (nombre.isEmpty()) return null;
        String clave = nombre.toLowerCase(Locale.ROOT);
        Categoria cacheada = cx.categorias.get(clave);
        if (cacheada != null) return cacheada;
        Optional<Categoria> existente = categorias.findFirstByEmpresaIdAndNombreIgnoreCase(cx.p.empresaId(), nombre);
        Categoria c = existente.orElseGet(() -> {
            Categoria nueva = new Categoria();
            nueva.setNombre(nombre);
            nueva.setEmpresa(cx.empresa);
            nueva.setActivo(true);
            return categorias.save(nueva);
        });
        if (!c.isActivo()) {
            c.setActivo(true);
            c = categorias.save(c);
        }
        cx.categorias.put(clave, c);
        return c;
    }

    private static String normalizarUnidad(String texto) {
        String u = limpiar(texto).toUpperCase(Locale.ROOT).replace(".", "");
        if (u.isEmpty()) return null;
        switch (u) {
            case "UN", "UND", "UNID", "UNIDAD", "UNIDADES", "U" -> u = "UN";
            case "KG", "KGS", "KILO", "KILOS", "KILOGRAMO", "KILOGRAMOS" -> u = "KG";
            case "GR", "G", "GRS", "GRAMO", "GRAMOS" -> u = "GR";
            case "LT", "L", "LTS", "LITRO", "LITROS" -> u = "LT";
            default -> {
            }
        }
        if (!UNIDADES.contains(u)) {
            throw new NegocioException("La unidad \"" + texto.trim() + "\" no es válida. Usa UN, KG, GR o LT.");
        }
        return u;
    }

    private static Double monto(String campo, Double v) {
        if (v == null) return null;
        if (v.isNaN() || v.isInfinite() || v < 0 || v > 100_000_000d) {
            throw new NegocioException(campo + " no es válido.");
        }
        return (double) Math.round(v);
    }

    private static Double cantidad(String campo, Double v) {
        if (v == null) return null;
        if (v.isNaN() || v.isInfinite() || v < 0 || v > 10_000_000d) {
            throw new NegocioException(campo + " no es válido.");
        }
        return Math.round(v * 1000d) / 1000d;
    }

    private static String limpiar(String s) {
        return s == null ? "" : s.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
