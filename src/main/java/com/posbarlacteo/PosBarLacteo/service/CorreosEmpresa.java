package com.posbarlacteo.PosBarLacteo.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.posbarlacteo.PosBarLacteo.model.Usuario;
import com.posbarlacteo.PosBarLacteo.repository.UsuarioRepository;
import com.posbarlacteo.PosBarLacteo.security.Roles;

/** A quién se le escribe por la cuenta de una empresa: sus administradores con un correo real. */
@Component
public class CorreosEmpresa {

    private final UsuarioRepository usuarioRepository;

    public CorreosEmpresa(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Correos de los administradores (sin repetidos). Se omiten los correos ficticios "@...local" de empleados sin correo. */
    public List<String> administradores(Long empresaId) {
        Set<String> correos = new LinkedHashSet<>();
        for (Usuario u : usuarioRepository.findByEmpresaId(empresaId)) {
            String c = u.getCorreo() == null ? "" : u.getCorreo().trim();
            if (Roles.esAdmin(u.getRol()) && !c.isEmpty() && !c.toLowerCase().endsWith(".local")) {
                correos.add(c);
            }
        }
        return List.copyOf(correos);
    }

    /** Todos los correos reales de la empresa (administradores y empleados): para limpiar sus verificaciones al borrarla. */
    public List<String> todos(Long empresaId) {
        Set<String> correos = new LinkedHashSet<>();
        for (Usuario u : usuarioRepository.findByEmpresaId(empresaId)) {
            if (u.getCorreo() != null && !u.getCorreo().isBlank()) {
                correos.add(u.getCorreo().trim());
            }
        }
        return List.copyOf(correos);
    }
}
