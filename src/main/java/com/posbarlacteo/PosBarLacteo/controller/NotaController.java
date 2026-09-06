package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.model.Nota;
import com.posbarlacteo.PosBarLacteo.repository.NotaRepository;

@RestController
@RequestMapping("/api/notas")
@CrossOrigin(originPatterns = "*")
public class NotaController {

    @Autowired
    private NotaRepository notaRepository;

    // ✨ ACTUALIZADO: Recibe el parámetro empresaId
    @GetMapping
    public ResponseEntity<List<Nota>> obtenerNotas(@RequestParam Long empresaId) {
        return ResponseEntity.ok(notaRepository.findByEmpresaIdOrderByFechaHoraDesc(empresaId));
    }

    @PostMapping
    public Nota crearNota(@RequestBody Nota nota) {
        return notaRepository.save(nota);
    }

    @DeleteMapping("/{id}")
    public void eliminarNota(@PathVariable Long id) {
        notaRepository.deleteById(id);
    }
}