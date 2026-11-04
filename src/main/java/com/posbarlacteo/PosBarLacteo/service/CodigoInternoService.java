package com.posbarlacteo.PosBarLacteo.service;

import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;

/**
 * Códigos de barras internos para productos que no traen uno (elaboración propia, granel, sin etiqueta).
 * Son EAN-13 válidos con prefijo "29" (rango que el sistema GS1 reserva para uso interno de cada comercio, así que
 * no chocan con códigos de fabricantes): "29" + correlativo de 10 dígitos + dígito verificador. Se pueden imprimir
 * como etiqueta y leer con cualquier lector.
 */
@Service
public class CodigoInternoService {

    private static final String PREFIJO = "29";
    private static final long MAXIMO = 9_999_999_999L;

    private final ProductoRepository productos;

    public CodigoInternoService(ProductoRepository productos) {
        this.productos = productos;
    }

    /** Último correlativo usado por la empresa (0 si no ha generado ninguno). */
    public long ultimoNumero(Long empresaId) {
        String max = productos.maxCodigoInterno(empresaId);
        if (max == null || max.length() != 13) return 0;
        try {
            return Long.parseLong(max.substring(2, 12));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Arma el EAN-13 de un correlativo. */
    public String formar(long numero) {
        if (numero < 1 || numero > MAXIMO) {
            throw new NegocioException("Se agotaron los códigos internos disponibles.");
        }
        String cuerpo = PREFIJO + String.format("%010d", numero);
        return cuerpo + digitoVerificador(cuerpo);
    }

    /** Siguiente código libre de la empresa. */
    public String siguiente(Long empresaId) {
        long n = ultimoNumero(empresaId);
        String codigo;
        do {
            n++;
            codigo = formar(n);
        } while (productos.findByCodigoBarrasAndEmpresaId(codigo, empresaId).isPresent());
        return codigo;
    }

    /** Dígito de control EAN-13 de los 12 primeros dígitos. */
    public static int digitoVerificador(String doce) {
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            int d = doce.charAt(i) - '0';
            suma += (i % 2 == 0) ? d : d * 3;
        }
        return (10 - (suma % 10)) % 10;
    }
}
