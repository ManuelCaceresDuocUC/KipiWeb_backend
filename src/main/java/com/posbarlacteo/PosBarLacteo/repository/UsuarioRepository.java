package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.posbarlacteo.PosBarLacteo.model.Usuario;


public interface UsuarioRepository extends JpaRepository<Usuario, Long>{
    

    Optional<Usuario> findByUsuario(String usuario);
    List<Usuario> findByEmpresaId(Long empresaId);
}
