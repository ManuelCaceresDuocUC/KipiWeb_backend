package com.posbarlacteo.PosBarLacteo.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.Devolucion;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.DevolucionService;
import com.posbarlacteo.PosBarLacteo.service.DevolucionService.LineaDevolucion;

/** Registro de devoluciones y cambios. Cualquier usuario puede registrar; reponer stock exige administrador. */
@RestController
@RequestMapping("/api/devoluciones")
public class DevolucionController {

    private final DevolucionService servicio;

    public DevolucionController(DevolucionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public Devolucion registrar(@RequestBody Map<String, Object> body) {
        Long ventaId = numero(body.get("ventaId"));
        if (ventaId == null) {
            throw new NegocioException("Falta la venta.");
        }
        List<LineaDevolucion> lineas = new ArrayList<>();
        Object items = body.get("items");
        if (items instanceof List<?> lista) {
            for (Object o : lista) {
                if (o instanceof Map<?, ?> m) {
                    Object c = m.get("cantidad");
                    Double cantidad;
                    try {
                        cantidad = c == null ? null : Double.valueOf(String.valueOf(c));
                    } catch (NumberFormatException e) {
                        throw new NegocioException("Hay un producto con cantidad inválida.");
                    }
                    lineas.add(new LineaDevolucion(numero(m.get("productoId")), cantidad));
                }
            }
        }
        return servicio.registrar(SecurityUtils.actual(), ventaId,
                texto(body.get("tipo")), texto(body.get("motivo")), texto(body.get("nota")),
                Boolean.TRUE.equals(body.get("reponerStock")), lineas);
    }

    /** Con ventaId: las de esa venta. Sin él: las 100 más recientes de la empresa. */
    @GetMapping
    public List<Devolucion> listar(@RequestParam(required = false) Long ventaId) {
        return ventaId != null
                ? servicio.deLaVenta(SecurityUtils.actual(), ventaId)
                : servicio.recientes(SecurityUtils.actual());
    }

    private static Long numero(Object o) {
        if (o == null) return null;
        try {
            return Long.valueOf(String.valueOf(o).replaceAll("\\.0+$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String texto(Object o) {
        return o == null ? null : o.toString();
    }
}
