package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.ConflictoException;
import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Categoria;
import com.posbarlacteo.PosBarLacteo.repository.CategoriaRepository;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;
    private final EmpresaRepository empresaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository, EmpresaRepository empresaRepository) {
        this.categoriaRepository = categoriaRepository;
        this.empresaRepository = empresaRepository;
    }

    @GetMapping
    public List<Categoria> obtenerTodas() {
        return categoriaRepository.findByEmpresaIdAndActivoTrue(SecurityUtils.actual().empresaId());
    }

    @PostMapping
    @Transactional
    public Categoria guardar(@RequestBody Categoria body) {
        AuthPrincipal p = SecurityUtils.actual();
        String nombre = nombreValido(body);
        Categoria nueva = new Categoria();
        nueva.setNombre(nombre);
        nueva.setActivo(true);
        nueva.setEmpresa(empresaRepository.findById(p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada.")));
        try {
            return categoriaRepository.saveAndFlush(nueva);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictoException("Ya existe una categoría con ese nombre.");
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public Categoria actualizar(@PathVariable Long id, @RequestBody Categoria body) {
        Categoria categoria = categoriaRepository.findByIdAndEmpresaId(id, SecurityUtils.actual().empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada."));
        categoria.setNombre(nombreValido(body));
        try {
            return categoriaRepository.saveAndFlush(categoria);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictoException("Ya existe una categoría con ese nombre.");
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void eliminar(@PathVariable Long id) {
        Categoria categoria = categoriaRepository.findByIdAndEmpresaId(id, SecurityUtils.actual().empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada."));
        categoria.setActivo(false);
        categoriaRepository.save(categoria);
    }

    private String nombreValido(Categoria body) {
        if (body.getNombre() == null || body.getNombre().isBlank()) {
            throw new NegocioException("El nombre de la categoría es obligatorio.");
        }
        return body.getNombre().trim();
    }
}
