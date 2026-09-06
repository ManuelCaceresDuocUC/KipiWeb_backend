package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
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
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Receta;
import com.posbarlacteo.PosBarLacteo.repository.CategoriaRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.repository.RecetaRepository;

import jakarta.transaction.Transactional;

@CrossOrigin(origins = {
    "http://posbarlacteo-manuel-2026.s3-website-us-east-1.amazonaws.com", // Producción AWS
    "http://localhost:5173",
    "http://34.203.91.138",
    "https://ordpos.duckdns.org",                                       // PC Local
    "http://192.168.100.85:5173"                                        // Tu Celular
})
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private RecetaRepository recetaRepository;
    
    @Autowired 
    private CategoriaRepository categoriaRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @GetMapping
    public Page<Producto> obtenerTodos(
            @RequestParam(required = false) Long categoriaId, 
            @RequestParam(defaultValue = "1") Long empresaId, 
            @RequestParam(defaultValue = "0") int page, 
            @RequestParam(defaultValue = "20") int size) {
        
        if (categoriaId != null) {
            return productoRepository.findByActivoTrueAndCategoriaIdAndEmpresaId(categoriaId, empresaId, PageRequest.of(page, size));
        }
        
        return productoRepository.findByActivoTrueAndEmpresaId(empresaId, PageRequest.of(page, size));
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        productoRepository.findById(id).ifPresent(p -> {
            p.setActivo(false);
            productoRepository.save(p);
        });
    }
    
    @PutMapping("/{id}")
    public Producto actualizar(@PathVariable Long id, @RequestBody Producto productoActualizado) {
        return productoRepository.findById(id)
            .map(producto -> {
                // 🛡️ CORRECCIÓN: Si actualizan el código de barras y viene vacío "", lo pasamos a null
                if (productoActualizado.getCodigoBarras() != null && productoActualizado.getCodigoBarras().trim().isEmpty()) {
                    producto.setCodigoBarras(null);
                } else {
                    producto.setCodigoBarras(productoActualizado.getCodigoBarras());
                }

                producto.setDescripcion(productoActualizado.getDescripcion());
                producto.setPrecio(productoActualizado.getPrecio());
                producto.setStock(productoActualizado.getStock());
                producto.setStockCritico(productoActualizado.getStockCritico());
                
                // 🟢 CORRECCIÓN: Actualización de campos faltantes
                producto.setCategoria(productoActualizado.getCategoria());
                producto.setUnidadMedida(productoActualizado.getUnidadMedida());
                producto.setEsInsumo(productoActualizado.getEsInsumo());
                
                return productoRepository.save(producto);
            })
            .orElseThrow(() -> new RuntimeException("Producto no encontrado con id: " + id));
    }
    
    @PutMapping("/{id}/descontar")
    @Transactional 
    public Producto descontarStock(@PathVariable Long id, @RequestParam Double cantidad) {
        return productoRepository.findById(id)
            .map(producto -> {
                List<Receta> ingredientes = recetaRepository.findByProductoPrincipalId(id);

                if (ingredientes.isEmpty()) {
                    // 🟢 Descuenta directamente, permitiendo valores negativos
                    producto.setStock(producto.getStock() - cantidad);
                } else {
                    for (Receta item : ingredientes) {
                        Producto insumo = item.getInsumo();
                        Double cantidadAGastar = item.getCantidadUsada() * cantidad;

                        // 🟢 Descuenta el insumo permitiendo valores negativos
                        insumo.setStock(insumo.getStock() - cantidadAGastar);
                        productoRepository.save(insumo); 
                    }
                }
                
                return productoRepository.save(producto);
            })
            .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    @PatchMapping("/{id}")
    public Producto editarParcial(@PathVariable Long id, @RequestBody Producto productoActualizado) {
        return productoRepository.findById(id)
            .map(producto -> {
                if (productoActualizado.getDescripcion() != null) producto.setDescripcion(productoActualizado.getDescripcion());
                if (productoActualizado.getPrecio() != null) producto.setPrecio(productoActualizado.getPrecio());
                if (productoActualizado.getStock() != null) producto.setStock(productoActualizado.getStock());
                if (productoActualizado.getStockCritico() != null) producto.setStockCritico(productoActualizado.getStockCritico());
                
                // 🟢 CORRECCIÓN: Actualización de campos faltantes (Unidad de Medida e Insumo)
                if (productoActualizado.getUnidadMedida() != null) producto.setUnidadMedida(productoActualizado.getUnidadMedida());
                if (productoActualizado.getEsInsumo() != null) {producto.setEsInsumo(productoActualizado.getEsInsumo());}

                // 🟢 CORRECCIÓN CLAVE: Asignación directa para permitir que envíe 'null' y quite la categoría
                producto.setCategoria(productoActualizado.getCategoria());
                
                // 🛡️ CORRECCIÓN: Manejo seguro en edición parcial del código de barras
                if (productoActualizado.getCodigoBarras() != null) {
                    if (productoActualizado.getCodigoBarras().trim().isEmpty()) {
                        producto.setCodigoBarras(null);
                    } else {
                        producto.setCodigoBarras(productoActualizado.getCodigoBarras());
                    }
                }
                
                return productoRepository.save(producto);
            })
            .orElseThrow(() -> new RuntimeException("Producto no encontrado con id: " + id));
    }

    @PostMapping
    public Producto guardar(
            @RequestBody Producto producto,
            @RequestParam(defaultValue = "1") Long empresaId
    ) {
        // Asignamos la empresa si el objeto viene sin ella desde el frontend
        if (producto.getEmpresa() == null) {
            Empresa empresa = empresaRepository.findById(empresaId)
                    .orElseThrow(() -> new RuntimeException("Empresa no encontrada con ID: " + empresaId));
            producto.setEmpresa(empresa);
        }

        // 🛡️ CORRECCIÓN CLAVE: Si el código de barras es "" o solo espacios en blanco, lo convertimos en null
        if (producto.getCodigoBarras() != null && producto.getCodigoBarras().trim().isEmpty()) {
            producto.setCodigoBarras(null);
        }

        // Validación de código de barras por empresa (solo si el código no es null)
        if (producto.getCodigoBarras() != null) {
            Optional<Producto> existente = productoRepository.findByCodigoBarrasAndEmpresaId(producto.getCodigoBarras(), empresaId);
            if (existente.isPresent()) {
                throw new RuntimeException("Ya existe un producto con el código: " + producto.getCodigoBarras());
            }
        }

        return productoRepository.save(producto);
    }

    @PostMapping("/con-receta")
    @Transactional
    public Producto crearProductoConReceta(
            @RequestBody RecetaRequestDTO request,
            @RequestParam(defaultValue = "1") Long empresaId 
    ) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada con ID: " + empresaId));

        Producto pPrincipal = request.getProductoPrincipal();
        pPrincipal.setEsInsumo(false);
        pPrincipal.setStock(0.0);
        pPrincipal.setEmpresa(empresa); 
        
        // 🟢 SEGURIDAD: Asignamos unidad de medida por defecto si llega nula
        if (pPrincipal.getUnidadMedida() == null || pPrincipal.getUnidadMedida().trim().isEmpty()) {
            pPrincipal.setUnidadMedida("UN");
        }

        // 🟢 SEGURIDAD: Buscamos y asignamos la categoría para evitar que quede en NULL en MySQL
        if (pPrincipal.getCategoria() != null && pPrincipal.getCategoria().getId() != null) {
            categoriaRepository.findById(pPrincipal.getCategoria().getId())
                .ifPresent(pPrincipal::setCategoria);
        }
        
        // 🛡️ CORRECCIÓN CLAVE: Evitar error 500 en MySQL convirtiendo "" a null
        if (pPrincipal.getCodigoBarras() != null && pPrincipal.getCodigoBarras().trim().isEmpty()) {
            pPrincipal.setCodigoBarras(null);
        }

        // Validación extra de código duplicado al crear con receta
        if (pPrincipal.getCodigoBarras() != null) {
            Optional<Producto> existente = productoRepository.findByCodigoBarrasAndEmpresaId(pPrincipal.getCodigoBarras(), empresaId);
            if (existente.isPresent()) {
                throw new RuntimeException("Ya existe un producto con el código: " + pPrincipal.getCodigoBarras());
            }
        }
        
        Producto nuevoProducto = productoRepository.save(pPrincipal);

        if (request.getIngredientes() != null && !request.getIngredientes().isEmpty()) {
            for (var item : request.getIngredientes()) {
                Receta vinculo = new Receta();
                vinculo.setProductoPrincipal(nuevoProducto); 
                Producto insumo = productoRepository.findById(item.getInsumoId())
                    .orElseThrow(() -> new RuntimeException("Insumo no existe con ID: " + item.getInsumoId()));
                vinculo.setInsumo(insumo);
                vinculo.setCantidadUsada(item.getCantidad());
                recetaRepository.save(vinculo);
            }
        }
        return nuevoProducto;
    }
}