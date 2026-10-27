package com.posbarlacteo.PosBarLacteo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.posbarlacteo.PosBarLacteo.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>{
    
    // El nombre de usuario puede repetirse entre empresas en datos antiguos: el login resuelve la ambigüedad.
    List<Usuario> findAllByUsuario(String usuario);

    boolean existsByUsuario(String usuario);
    List<Usuario> findByEmpresaId(Long empresaId);
    
    // ✨ NUEVO MÉTODO PARA FILTRAR POR SUCURSAL
    List<Usuario> findBySucursalId(Long sucursalId);
}