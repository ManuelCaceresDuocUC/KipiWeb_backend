package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.model.Receta;
import com.posbarlacteo.PosBarLacteo.repository.RecetaRepository;

@RestController
@RequestMapping("/api/recetas")
@CrossOrigin(origins = {
    "http://posbarlacteo-manuel-2026.s3-website-us-east-1.amazonaws.com", // Producción AWS
    "http://localhost:5173",
    "http://34.203.91.138",
    "https://ordpos.duckdns.org",                                             // PC Local
    "http://192.168.100.85:5173"                                         // Tu Celular
})
public class RecetaController {

    @Autowired
    private RecetaRepository recetaRepository;

    @GetMapping
    public List<Receta> obtenerTodas() {
        return recetaRepository.findAll();
    }

    @PostMapping
    public Receta guardar(@RequestBody Receta receta) {
        return recetaRepository.save(receta);
    }
    
    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        recetaRepository.deleteById(id);
    }
}