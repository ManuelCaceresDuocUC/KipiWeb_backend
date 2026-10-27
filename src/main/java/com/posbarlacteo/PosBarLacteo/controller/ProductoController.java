package com.posbarlacteo.PosBarLacteo.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.dto.RecetaRequestDTO;
import com.posbarlacteo.PosBarLacteo.exception.ConflictoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Categoria;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Receta;
import com.posbarlacteo.PosBarLacteo.repository.CategoriaRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.repository.RecetaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;

/**
 * Catálogo por empresa. Lectura: cualquier usuario autenticado. Escritura: solo ADMIN (SecurityConfig).
 * La empresa siempre sale del token; nunca del cuerpo ni de parámetros.
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private static final int TAM_MAX_PAGINA = 2000;

    private final ProductoRepository productoRepository;
    private final RecetaRepository recetaRepository;
    private final CategoriaRepository categoriaRepository;
    private final EmpresaRepository empresaRepository;

    public ProductoController(ProductoRepository productoRepository, RecetaRepository recetaRepository,
                              CategoriaRepository categoriaRepository, EmpresaRepository empresaRepository) {
        this.productoRepository = productoRepository;
        this.recetaRepository = recetaRepository;
        this.categoriaRepository = categoriaRepository;
        this.empresaRepository = empresaRepository;
    }

    @GetMapping
    public Page<Producto> obtenerTodos(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuthPrincipal p = SecurityUtils.actual();
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAM_MAX_PAGINA));
        if (categoriaId != null) {
            return productoRepository.findByActivoTrueAndCategoriaIdAndEmpresaId(categoriaId, p.empresaId(), pr);
        }
        return productoRepository.findByActivoTrueAndEmpresaId(p.empresaId(), pr);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void eliminar(@PathVariable Long id) {
        Producto producto = buscar(id);
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    @PutMapping("/{id}")
    @Transactional
    public Producto actualizar(@PathVariable Long id, @RequestBody Producto body) {
        AuthPrincipal p = SecurityUtils.actual();
        Producto producto = buscar(id);
        String codigo = normalizarCodigo(body.getCodigoBarras());
        validarCodigoUnico(codigo, p.empresaId(), id);
        validarDatos(body);

        producto.setCodigoBarras(codigo);
        producto.setDescripcion(body.getDescripcion().trim());
        producto.setPrecio(body.getPrecio());
        producto.setStock(body.getStock() != null ? body.getStock() : producto.getStock());
        producto.setStockCritico(body.getStockCritico());
        producto.setCategoria(resolverCategoria(body.getCategoria(), p.empresaId()));
        producto.setUnidadMedida(body.getUnidadMedida());
        if (body.getEsInsumo() != null) {
            producto.setEsInsumo(body.getEsInsumo());
        }
        return productoRepository.save(producto);
    }

    @PatchMapping("/{id}")
    @Transactional
    public Producto editarParcial(@PathVariable Long id, @RequestBody Producto body) {
        AuthPrincipal p = SecurityUtils.actual();
        Producto producto = buscar(id);

        if (body.getDescripcion() != null) {
            if (body.getDescripcion().isBlank()) throw new NegocioException("La descripción no puede estar vacía.");
            producto.setDescripcion(body.getDescripcion().trim());
        }
        if (body.getPrecio() != null) {
            if (body.getPrecio() < 0 || body.getPrecio().isNaN()) throw new NegocioException("El precio no es válido.");
            producto.setPrecio(body.getPrecio());
        }
        if (body.getStock() != null) producto.setStock(body.getStock());
        if (body.getStockCritico() != null) producto.setStockCritico(body.getStockCritico());
        if (body.getUnidadMedida() != null) producto.setUnidadMedida(body.getUnidadMedida());
        if (body.getEsInsumo() != null) producto.setEsInsumo(body.getEsInsumo());

        // Se conserva el comportamiento anterior: enviar categoria null la quita
        producto.setCategoria(resolverCategoria(body.getCategoria(), p.empresaId()));

        if (body.getCodigoBarras() != null) {
            String codigo = normalizarCodigo(body.getCodigoBarras());
            validarCodigoUnico(codigo, p.empresaId(), id);
            producto.setCodigoBarras(codigo);
        }
        return productoRepository.save(producto);
    }

    @PostMapping
    @Transactional
    public Producto guardar(@RequestBody Producto body) {
        AuthPrincipal p = SecurityUtils.actual();
        validarDatos(body);
        Empresa empresa = empresaRepository.findById(p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));

        String codigo = normalizarCodigo(body.getCodigoBarras());
        validarCodigoUnico(codigo, p.empresaId(), null);

        body.setId(null);
        body.setEmpresa(empresa);
        body.setActivo(true);
        body.setCodigoBarras(codigo);
        body.setDescripcion(body.getDescripcion().trim());
        body.setCategoria(resolverCategoria(body.getCategoria(), p.empresaId()));
        if (body.getStock() == null) body.setStock(0.0);
        return productoRepository.save(body);
    }

    @PostMapping("/con-receta")
    @Transactional
    public Producto crearProductoConReceta(@RequestBody RecetaRequestDTO request) {
        AuthPrincipal p = SecurityUtils.actual();
        Producto principal = request.getProductoPrincipal();
        if (principal == null) throw new NegocioException("Falta el producto principal.");
        validarDatos(principal);

        Empresa empresa = empresaRepository.findById(p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));

        String codigo = normalizarCodigo(principal.getCodigoBarras());
        validarCodigoUnico(codigo, p.empresaId(), null);

        principal.setId(null);
        principal.setEmpresa(empresa);
        principal.setActivo(true);
        principal.setEsInsumo(false);
        principal.setStock(0.0);
        principal.setCodigoBarras(codigo);
        principal.setDescripcion(principal.getDescripcion().trim());
        principal.setCategoria(resolverCategoria(principal.getCategoria(), p.empresaId()));
        if (principal.getUnidadMedida() == null || principal.getUnidadMedida().isBlank()) {
            principal.setUnidadMedida("UN");
        }

        // Validar TODOS los insumos antes de guardar nada
        if (request.getIngredientes() != null) {
            for (var item : request.getIngredientes()) {
                if (item.getInsumoId() == null || item.getCantidad() == null || item.getCantidad() <= 0) {
                    throw new NegocioException("Hay un ingrediente con datos inválidos.");
                }
                productoRepository.findByIdAndEmpresaId(item.getInsumoId(), p.empresaId())
                        .orElseThrow(() -> new NegocioException("Insumo no existe: " + item.getInsumoId()));
            }
        }

        Producto nuevo = productoRepository.save(principal);

        if (request.getIngredientes() != null) {
            for (var item : request.getIngredientes()) {
                Receta vinculo = new Receta();
                vinculo.setProductoPrincipal(nuevo);
                vinculo.setInsumo(productoRepository.findByIdAndEmpresaId(item.getInsumoId(), p.empresaId()).get());
                vinculo.setCantidadUsada(item.getCantidad());
                recetaRepository.save(vinculo);
            }
        }
        return nuevo;
    }

    // ---------- helpers ----------

    private Producto buscar(Long id) {
        return productoRepository.findByIdAndEmpresaId(id, SecurityUtils.actual().empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado."));
    }

    private String normalizarCodigo(String codigo) {
        return (codigo == null || codigo.trim().isEmpty()) ? null : codigo.trim();
    }

    private void validarCodigoUnico(String codigo, Long empresaId, Long idActual) {
        if (codigo == null) return;
        productoRepository.findByCodigoBarrasAndEmpresaId(codigo, empresaId).ifPresent(existente -> {
            if (!existente.getId().equals(idActual)) {
                throw new ConflictoException("Ya existe un producto con el código: " + codigo);
            }
        });
    }

    private void validarDatos(Producto p) {
        if (p.getDescripcion() == null || p.getDescripcion().isBlank()) {
            throw new NegocioException("La descripción es obligatoria.");
        }
        if (p.getPrecio() == null || p.getPrecio() < 0 || p.getPrecio().isNaN()) {
            throw new NegocioException("El precio no es válido.");
        }
    }

    /** La categoría debe existir y ser de la misma empresa; null = sin categoría. */
    private Categoria resolverCategoria(Categoria enviada, Long empresaId) {
        if (enviada == null || enviada.getId() == null) return null;
        return categoriaRepository.findByIdAndEmpresaId(enviada.getId(), empresaId)
                .orElseThrow(() -> new NegocioException("La categoría no existe."));
    }
}
