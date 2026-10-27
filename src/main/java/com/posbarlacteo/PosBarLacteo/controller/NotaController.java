package com.posbarlacteo.PosBarLacteo.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Nota;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.repository.NotaRepository;
import com.posbarlacteo.PosBarLacteo.repository.SucursalRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;

@RestController
@RequestMapping("/api/notas")
public class NotaController {

    private final NotaRepository notaRepository;
    private final EmpresaRepository empresaRepository;
    private final SucursalRepository sucursalRepository;

    public NotaController(NotaRepository notaRepository, EmpresaRepository empresaRepository,
                          SucursalRepository sucursalRepository) {
        this.notaRepository = notaRepository;
        this.empresaRepository = empresaRepository;
        this.sucursalRepository = sucursalRepository;
    }

    @GetMapping
    public List<Nota> obtenerNotas() {
        return notaRepository.findByEmpresaIdOrderByFechaHoraDesc(SecurityUtils.actual().empresaId());
    }

    @PostMapping
    @Transactional
    public Nota crearNota(@RequestBody Nota body) {
        AuthPrincipal p = SecurityUtils.actual();
        if (body.getTexto() == null || body.getTexto().isBlank()) {
            throw new NegocioException("La nota no puede estar vacía.");
        }
        if (body.getTexto().length() > 500) {
            throw new NegocioException("La nota no puede superar los 500 caracteres.");
        }
        Nota nota = new Nota();
        nota.setTexto(body.getTexto().trim());
        nota.setEmpresa(empresaRepository.findById(p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada.")));
        if (p.sucursalId() != null) {
            sucursalRepository.findByIdAndEmpresaId(p.sucursalId(), p.empresaId()).ifPresent(nota::setSucursal);
        }
        return notaRepository.save(nota);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void eliminarNota(@PathVariable Long id) {
        Nota nota = notaRepository.findByIdAndEmpresaId(id, SecurityUtils.actual().empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Nota no encontrada."));
        notaRepository.delete(nota);
    }
}
