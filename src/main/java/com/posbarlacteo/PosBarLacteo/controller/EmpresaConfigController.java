package com.posbarlacteo.PosBarLacteo.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.exception.RecursoNoEncontradoException;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;
import com.posbarlacteo.PosBarLacteo.service.AuditoriaService;

/** Políticas de venta de la empresa. Lectura: cualquier usuario (el POS decide qué medios mostrar). Escritura: ADMIN. */
@RestController
@RequestMapping("/api/empresa")
public class EmpresaConfigController {

    private final EmpresaRepository empresaRepository;
    private final AuditoriaService auditoria;

    public EmpresaConfigController(EmpresaRepository empresaRepository, AuditoriaService auditoria) {
        this.empresaRepository = empresaRepository;
        this.auditoria = auditoria;
    }

    /** Datos del registro de la empresa. El RUT se muestra pero no se puede cambiar. */
    @GetMapping("/datos")
    @Transactional(readOnly = true)
    public Map<String, Object> datos() {
        return datosDe(empresa(SecurityUtils.actual()));
    }

    /**
     * Corrige los datos de la empresa (solo ADMIN, ver SecurityConfig). Estos datos salen impresos en tickets y
     * viajan al proveedor de facturación como datos del emisor, por eso se validan y se deja registro del cambio.
     * El RUT no se edita: identifica a la empresa, su suscripción y sus documentos; para cambiarlo hay que escribir a soporte.
     */
    @PutMapping("/datos")
    @Transactional
    public Map<String, Object> guardarDatos(@RequestBody Map<String, Object> body) {
        AuthPrincipal p = SecurityUtils.actual();
        Empresa e = empresa(p);

        String razon = limpiar(body.get("razonSocial"));
        if (razon.length() < 2) {
            throw new NegocioException("La razón social o nombre del negocio es obligatorio.");
        }
        validarLargo("La razón social", razon, 150);
        String giro = limpiar(body.get("giro"));
        validarLargo("El giro", giro, 80);
        String direccion = limpiar(body.get("direccion"));
        validarLargo("La dirección", direccion, 100);
        String comuna = limpiar(body.get("comuna"));
        validarLargo("La comuna", comuna, 60);

        StringBuilder cambios = new StringBuilder();
        cambios.append(diferencia("razón social", e.getRazonSocial(), razon));
        cambios.append(diferencia("giro", e.getGiro(), giro));
        cambios.append(diferencia("dirección", e.getDireccion(), direccion));
        cambios.append(diferencia("comuna", e.getComuna(), comuna));

        e.setRazonSocial(razon);
        e.setGiro(giro.isEmpty() ? null : giro);
        e.setDireccion(direccion.isEmpty() ? null : direccion);
        e.setComuna(comuna.isEmpty() ? null : comuna);
        empresaRepository.save(e);

        if (cambios.length() > 0) {
            auditoria.registrar(p, "EMPRESA_DATOS", cambios.toString().trim());
        }
        return datosDe(e);
    }

    private static Map<String, Object> datosDe(Empresa e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rutEmpresa", e.getRutEmpresa());
        m.put("razonSocial", e.getRazonSocial());
        m.put("giro", e.getGiro() == null ? "" : e.getGiro());
        m.put("direccion", e.getDireccion() == null ? "" : e.getDireccion());
        m.put("comuna", e.getComuna() == null ? "" : e.getComuna());
        return m;
    }

    private static String limpiar(Object v) {
        return v == null ? "" : v.toString().replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
    }

    private static void validarLargo(String campo, String valor, int max) {
        if (valor.length() > max) {
            throw new NegocioException(campo + " no puede superar los " + max + " caracteres.");
        }
    }

    private static String diferencia(String campo, String antes, String despues) {
        String a = antes == null ? "" : antes;
        return a.equals(despues) ? "" : campo + ": \"" + a + "\" → \"" + despues + "\". ";
    }

    @GetMapping("/config-ventas")
    @Transactional(readOnly = true)
    public Map<String, Object> leer() {
        Empresa e = empresa(SecurityUtils.actual());
        return Map.of("permiteFiado", Boolean.TRUE.equals(e.getPermiteFiado()));
    }

    @PutMapping("/config-ventas")
    @Transactional
    public Map<String, Object> guardar(@RequestBody Map<String, Object> body) {
        Empresa e = empresa(SecurityUtils.actual());
        Object valor = body.get("permiteFiado");
        if (!(valor instanceof Boolean permite)) {
            throw new NegocioException("Indica si se permite o no el fiado.");
        }
        e.setPermiteFiado(permite);
        empresaRepository.save(e);
        return Map.of("permiteFiado", permite);
    }

    private Empresa empresa(AuthPrincipal p) {
        return empresaRepository.findById(p.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }
}
