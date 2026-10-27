package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.model.Receta;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.repository.RecetaRepository;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;

/** Recetas de la empresa del token. Escritura solo ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/recetas")
public class RecetaController {

    private final RecetaRepository recetaRepository;
    private final ProductoRepository productoRepository;

    public RecetaController(RecetaRepository recetaRepository, ProductoRepository productoRepository) {
        this.recetaRepository = recetaRepository;
        this.productoRepository = productoRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Receta> obtenerTodas() {
        return recetaRepository.findByEmpresaId(SecurityUtils.actual().empresaId());
    }

    @PostMapping
    @Transactional
    public Receta guardar(@RequestBody Receta body) {
        Long empresaId = SecurityUtils.actual().empresaId();
        if (body.getProductoPrincipal() == null || body.getProductoPrincipal().getId() == null
                || body.getInsumo() == null || body.getInsumo().getId() == null) {
            throw new NegocioException("Debes indicar el producto y el insumo.");
        }
        validarCantidad(body.getCantidadUsada());

        Producto principal = productoRepository.findByIdAndEmpresaId(body.getProductoPrincipal().getId(), empresaId)
                .orElseThrow(() -> new NegocioException("El producto no existe."));
        Producto insumo = productoRepository.findByIdAndEmpresaId(body.getInsumo().getId(), empresaId)
                .orElseThrow(() -> new NegocioException("El insumo no existe."));
        if (principal.getId().equals(insumo.getId())) {
            throw new NegocioException("Un producto no puede ser insumo de sí mismo.");
        }

        Receta nueva = new Receta();
        nueva.setProductoPrincipal(principal);
        nueva.setInsumo(insumo);
        nueva.setCantidadUsada(body.getCantidadUsada());
        return recetaRepository.save(nueva);
    }

    /** El frontend edita con PATCH; PUT se acepta igual. Solo cambia la cantidad usada. */
    @PatchMapping("/{id}")
    @Transactional
    public Receta editarPatch(@PathVariable Long id, @RequestBody Receta body) {
        return editarCantidad(id, body);
    }

    @PutMapping("/{id}")
    @Transactional
    public Receta editarPut(@PathVariable Long id, @RequestBody Receta body) {
        return editarCantidad(id, body);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void eliminar(@PathVariable Long id) {
        Receta receta = recetaRepository.findByIdAndEmpresaId(id, SecurityUtils.actual().empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Receta no encontrada."));
        recetaRepository.delete(receta);
    }

    private Receta editarCantidad(Long id, Receta body) {
        Receta receta = recetaRepository.findByIdAndEmpresaId(id, SecurityUtils.actual().empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Receta no encontrada."));
        validarCantidad(body.getCantidadUsada());
        receta.setCantidadUsada(body.getCantidadUsada());
        return recetaRepository.save(receta);
    }

    private void validarCantidad(Double cantidad) {
        if (cantidad == null || cantidad <= 0 || cantidad.isNaN() || cantidad.isInfinite()) {
            throw new NegocioException("La cantidad usada debe ser mayor a cero.");
        }
    }
}
