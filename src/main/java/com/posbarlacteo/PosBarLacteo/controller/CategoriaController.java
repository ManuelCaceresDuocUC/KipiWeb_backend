package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.model.Categoria;
import com.posbarlacteo.PosBarLacteo.repository.CategoriaRepository;

@CrossOrigin(origins = {
    "http://posbarlacteo-manuel-2026.s3-website-us-east-1.amazonaws.com", // Producción AWS
    "http://localhost:5173",
    "http://34.203.91.138",
    "https://ordpos.duckdns.org",                                             // PC Local
    "http://192.168.100.85:5173"                                         // Tu Celular
})
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    @Autowired
    private CategoriaRepository categoriaRepository;

    // Obtener todas las categorías activas
    @GetMapping
    public List<Categoria> obtenerTodas(@RequestParam(required = false) Long empresaId) {
        if (empresaId != null) {
            // ✨ Ahora sí le entregamos a cada empresa únicamente sus datos
            return categoriaRepository.findByEmpresaIdAndActivoTrue(empresaId);
        }
        return categoriaRepository.findByActivoTrue();
    }

    // Crear una nueva categoría
    @PostMapping
    public Categoria guardar(@RequestBody Categoria categoria) {
        categoria.setActivo(true);
        return categoriaRepository.save(categoria);
    }

    // Editar una categoría existente
    @PutMapping("/{id}")
    public Categoria actualizar(@PathVariable Long id, @RequestBody Categoria categoriaActualizada) {
        return categoriaRepository.findById(id)
            .map(categoria -> {
                categoria.setNombre(categoriaActualizada.getNombre());
                // Puedes agregar más campos aquí en el futuro si la categoría crece
                return categoriaRepository.save(categoria);
            })
            .orElseThrow(() -> new RuntimeException("Categoría no encontrada con id: " + id));
    }

    // Eliminación lógica de la categoría
    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        categoriaRepository.findById(id).ifPresent(c -> {
            c.setActivo(false);
            categoriaRepository.save(c);
        });
    }
}