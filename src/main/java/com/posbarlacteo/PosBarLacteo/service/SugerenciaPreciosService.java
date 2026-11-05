package com.posbarlacteo.PosBarLacteo.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.exception.NegocioException;
import com.posbarlacteo.PosBarLacteo.model.Producto;
import com.posbarlacteo.PosBarLacteo.repository.AnalisisVentasRepository;
import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/**
 * Sugerencias de precio y ofertas a partir de lo que realmente se vende: productos sin movimiento, margen bajo,
 * más vendidos y stock alto. Solo SUGIERE: el administrador decide si aplica cada precio. Nada cambia solo.
 */
@Service
public class SugerenciaPreciosService {

    private static final int MAX_POR_TIPO = 50;
    private static final double DESCUENTO_SIN_MOVIMIENTO = 0.10;
    private static final double DESCUENTO_STOCK_ALTO = 0.05;
    private static final double DIAS_COBERTURA_ALTA = 90;
    private static final double DIAS_COBERTURA_BAJA = 7;
    /** Una oferta nunca debería dejar el precio por debajo del costo + 5 %. */
    private static final double PISO_SOBRE_COSTO = 1.05;

    private final AnalisisVentasRepository analisis;
    private final ProductoRepository productos;
    private final AuditoriaService auditoria;

    public SugerenciaPreciosService(AnalisisVentasRepository analisis, ProductoRepository productos, AuditoriaService auditoria) {
        this.analisis = analisis;
        this.productos = productos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analizar(AuthPrincipal p, int diasSolicitados, double margenMinimoPct) {
        int dias = Math.max(7, Math.min(diasSolicitados, 180));
        double margenMin = Math.max(0, Math.min(margenMinimoPct, 500));
        LocalDateTime ahora = LocalDateTime.now();

        Map<Long, double[]> vendido = new HashMap<>(); // [unidades, ingresos]
        for (Object[] f : analisis.vendidoDesde(p.empresaId(), ahora.minusDays(dias))) {
            vendido.put((Long) f[0], new double[] {num(f[1]), num(f[2])});
        }
        Map<Long, LocalDateTime> ultima = new HashMap<>();
        for (Object[] f : analisis.ultimaVenta(p.empresaId())) ultima.put((Long) f[0], (LocalDateTime) f[1]);
        Map<Long, Double> stock = new HashMap<>();
        for (Object[] f : analisis.stockTotal(p.empresaId())) stock.put((Long) f[0], num(f[1]));

        List<Map<String, Object>> sinMovimiento = new ArrayList<>();
        List<Map<String, Object>> margenBajo = new ArrayList<>();
        List<Map<String, Object>> masVendidos = new ArrayList<>();
        List<Map<String, Object>> stockAlto = new ArrayList<>();
        int sinCosto = 0;
        int evaluados = 0;

        for (Producto pr : productos.catalogoCompleto(p.empresaId())) {
            if (Boolean.TRUE.equals(pr.getEsInsumo()) || pr.getPrecio() == null || pr.getPrecio() <= 0) continue;
            evaluados++;
            double precio = pr.getPrecio();
            Double costo = pr.getCosto() != null && pr.getCosto() > 0 ? pr.getCosto() : null;
            if (costo == null) sinCosto++;
            double stk = stock.getOrDefault(pr.getId(), 0.0);
            double[] v = vendido.get(pr.getId());
            double unidades = v == null ? 0 : v[0];
            double ingresos = v == null ? 0 : v[1];
            LocalDateTime ult = ultima.get(pr.getId());
            Double margen = costo == null ? null : (precio - costo) / costo * 100;
            Double cobertura = unidades > 0 ? stk / (unidades / dias) : null;

            // 1. Sin movimiento: tiene stock y no se vendió en el período
            if (stk > 0 && unidades <= 0) {
                Long sinVender = ult == null ? null : ChronoUnit.DAYS.between(ult, ahora);
                Double sugerido = rebajar(precio, costo, DESCUENTO_SIN_MOVIMIENTO);
                String msg = (sinVender == null ? "Nunca se ha vendido" : "Hace " + sinVender + " días que no se vende")
                        + " y tiene " + cant(stk) + " en stock."
                        + (sugerido != null ? " Una oferta de " + (int) (DESCUENTO_SIN_MOVIMIENTO * 100) + " % ayudaría a liberarlo."
                                : " Casi no hay espacio para rebajar sobre el costo; considera una promoción por cantidad.");
                sinMovimiento.add(fila("SIN_MOVIMIENTO", pr, precio, costo, margen, stk, unidades, ingresos, cobertura, sugerido, msg,
                        stk * (costo != null ? costo : precio)));
            }

            // 2. Margen bajo: el precio deja menos que el mínimo sobre el costo
            if (margen != null && margen < margenMin) {
                double objetivo = redondearArriba10(costo * (1 + margenMin / 100));
                String msg = String.format("Gana %.0f %% sobre el costo (mínimo %.0f %%).%s", margen, margenMin,
                        unidades > 0 ? " Se vende bien: subir el precio probablemente no afecte las ventas." : "");
                margenBajo.add(fila("MARGEN_BAJO", pr, precio, costo, margen, stk, unidades, ingresos, cobertura,
                        objetivo > precio ? objetivo : null, msg, margen));
            }

            // 3. Más vendidos (se ordenan y recortan después)
            if (unidades > 0) {
                String msg = "Vendió " + cant(unidades) + " en " + dias + " días."
                        + (cobertura != null && cobertura < DIAS_COBERTURA_BAJA
                                ? " Al ritmo actual el stock dura ~" + Math.max(1, Math.round(cobertura)) + " días: conviene reponer."
                                : "");
                masVendidos.add(fila("MAS_VENDIDO", pr, precio, costo, margen, stk, unidades, ingresos, cobertura, null, msg, ingresos));
            }

            // 4. Stock alto respecto a lo que se vende
            if (cobertura != null && cobertura > DIAS_COBERTURA_ALTA && stk >= 10) {
                Double sugerido = rebajar(precio, costo, DESCUENTO_STOCK_ALTO);
                String msg = "Tienes stock para ~" + Math.round(cobertura) + " días de venta. "
                        + (sugerido != null ? "Una promoción suave (" + (int) (DESCUENTO_STOCK_ALTO * 100) + " %) o un precio por cantidad ayudaría a rotarlo."
                                : "Considera una promoción por cantidad y comprar menos la próxima vez.");
                stockAlto.add(fila("STOCK_ALTO", pr, precio, costo, margen, stk, unidades, ingresos, cobertura, sugerido, msg, cobertura));
            }
        }

        ordenar(sinMovimiento);
        margenBajo.sort(Comparator.comparingDouble(m -> (double) m.get("_orden")));
        ordenar(masVendidos);
        ordenar(stockAlto);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dias", dias);
        r.put("margenMinimo", margenMin);
        r.put("productosEvaluados", evaluados);
        r.put("productosSinCosto", sinCosto);
        r.put("sinMovimiento", recortar(sinMovimiento, MAX_POR_TIPO));
        r.put("margenBajo", recortar(margenBajo, MAX_POR_TIPO));
        r.put("masVendidos", recortar(masVendidos, 10));
        r.put("stockAlto", recortar(stockAlto, MAX_POR_TIPO));
        r.put("totales", Map.of("sinMovimiento", sinMovimiento.size(), "margenBajo", margenBajo.size(),
                "stockAlto", stockAlto.size()));
        return r;
    }

    @Transactional
    public Map<String, Object> aplicarPrecio(AuthPrincipal p, Long productoId, Long precioNuevo) {
        Producto pr = productos.findByIdAndEmpresaId(productoId, p.empresaId())
                .orElseThrow(() -> new NegocioException("El producto no existe."));
        if (precioNuevo == null || precioNuevo < 0 || precioNuevo > 1_000_000_000L) {
            throw new NegocioException("El precio no es válido.");
        }
        double antes = pr.getPrecio() == null ? 0 : pr.getPrecio();
        pr.setPrecio(precioNuevo.doubleValue());
        productos.save(pr);
        auditoria.registrar(p, "PRECIO_SUGERIDO", pr.getDescripcion() + ": $" + Math.round(antes) + " → $" + precioNuevo);
        return Map.of("status", "success", "productoId", pr.getId(), "precio", precioNuevo);
    }

    // ------------------------------------------------------------------------------------------

    private Map<String, Object> fila(String tipo, Producto pr, double precio, Double costo, Double margen, double stock,
                                     double unidades, double ingresos, Double cobertura, Double sugerido, String mensaje,
                                     double orden) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("productoId", pr.getId());
        m.put("descripcion", pr.getDescripcion());
        m.put("codigoBarras", pr.getCodigoBarras());
        m.put("unidadMedida", pr.getUnidadMedida());
        m.put("precio", Math.round(precio));
        m.put("costo", costo == null ? null : Math.round(costo));
        m.put("margen", margen == null ? null : Math.round(margen * 10) / 10.0);
        m.put("stock", Math.round(stock * 1000) / 1000.0);
        m.put("unidadesVendidas", Math.round(unidades * 1000) / 1000.0);
        m.put("ingresos", Math.round(ingresos));
        m.put("diasCobertura", cobertura == null ? null : Math.round(cobertura));
        m.put("precioSugerido", sugerido == null ? null : Math.round(sugerido));
        m.put("mensaje", mensaje);
        m.put("_orden", orden);
        return m;
    }

    /** Precio con descuento (redondeado a $10), o null si queda bajo el piso sobre el costo o no cambia. */
    private Double rebajar(double precio, Double costo, double descuento) {
        double nuevo = Math.round(precio * (1 - descuento) / 10.0) * 10.0;
        if (nuevo >= precio) return null;
        if (costo != null && nuevo < costo * PISO_SOBRE_COSTO) return null;
        return nuevo;
    }

    private double redondearArriba10(double v) {
        return Math.ceil(v / 10.0) * 10.0;
    }

    private void ordenar(List<Map<String, Object>> l) {
        l.sort((a, b) -> Double.compare((double) b.get("_orden"), (double) a.get("_orden")));
    }

    private List<Map<String, Object>> recortar(List<Map<String, Object>> l, int max) {
        List<Map<String, Object>> r = new ArrayList<>(l.subList(0, Math.min(l.size(), max)));
        r.forEach(m -> m.remove("_orden"));
        return r;
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static String cant(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(java.util.Locale.forLanguageTag("es-CL"), "%.2f", d);
    }
}
