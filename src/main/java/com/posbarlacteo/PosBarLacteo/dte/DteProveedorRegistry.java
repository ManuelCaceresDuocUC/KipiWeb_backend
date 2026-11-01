package com.posbarlacteo.PosBarLacteo.dte;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Reúne todas las implementaciones de DteProveedor y entrega la que corresponde a cada empresa. */
@Component
public class DteProveedorRegistry {

    private final Map<String, DteProveedor> porCodigo = new LinkedHashMap<>();

    public DteProveedorRegistry(List<DteProveedor> proveedores) {
        for (DteProveedor p : proveedores) {
            porCodigo.put(p.codigo().toLowerCase(), p);
        }
    }

    public Optional<DteProveedor> buscar(String codigo) {
        return codigo == null ? Optional.empty() : Optional.ofNullable(porCodigo.get(codigo.trim().toLowerCase()));
    }

    public List<DteProveedor> todos() {
        return List.copyOf(porCodigo.values());
    }
}
