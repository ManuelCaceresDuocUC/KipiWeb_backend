package com.posbarlacteo.PosBarLacteo.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.posbarlacteo.PosBarLacteo.repository.ProductoRepository;
import com.posbarlacteo.PosBarLacteo.repository.VentaRepository;
import com.posbarlacteo.PosBarLacteo.security.SecurityUtils;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;

    public AdminController(VentaRepository ventaRepository, ProductoRepository productoRepository) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
    }

    // ==========================================
    // 📊 ENDPOINTS PARA LAS MÉTRICAS REALES
    // ==========================================

    @GetMapping("/metricas")
    public ResponseEntity<Map<String, Object>> obtenerMetricas() {
        Long empresaId = SecurityUtils.actual().empresaId();
        Map<String, Object> metricas = new HashMap<>();

        // 🕒 Configuración de tiempos reales dinámicos
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioHoy = hoy.atStartOfDay(); 
        LocalDateTime finHoy = hoy.atTime(LocalTime.MAX); 
        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay(); 

        // 📊 Consultas directas a la Base de Datos
        Double ventasHoy = ventaRepository.sumarVentasDelDia(empresaId, inicioHoy, finHoy);
        Double ventasMes = ventaRepository.sumarVentasDelMes(empresaId, inicioMes);
        Double ticketPromedio = ventaRepository.obtenerTicketPromedioDelDia(empresaId, inicioHoy, finHoy);
        Double costoInventario = productoRepository.calcularValorTotalInventario(empresaId);
        
        // Validamos nulos por si no hay ventas aún
        ventasHoy = (ventasHoy != null) ? ventasHoy : 0.0;
        ventasMes = (ventasMes != null) ? ventasMes : 0.0;
        ticketPromedio = (ticketPromedio != null) ? ticketPromedio : 0.0;
        costoInventario = (costoInventario != null) ? costoInventario : 0.0;

        // 🇨🇱 Cálculo del IVA chileno (19%)
        Double ivaMesCalculado = ventasMes * 19 / 119;

        // 📦 Empaquetamos todo
        metricas.put("ventasHoy", Math.round(ventasHoy));
        metricas.put("ivaMes", Math.round(ivaMesCalculado));
        metricas.put("ticketPromedio", Math.round(ticketPromedio));
        metricas.put("costoInventario", Long.valueOf(Math.round(costoInventario)));
        
        return ResponseEntity.ok(metricas);
    }

    @GetMapping("/productos-mas-vendidos")
    public ResponseEntity<List<Map<String, Object>>> obtenerMasVendidos(
            @RequestParam(defaultValue = "dia") String periodo) {
        Long empresaId = SecurityUtils.actual().empresaId();

        LocalDateTime desde;
        LocalDate hoy = LocalDate.now();

        // 🕒 Calculamos el inicio del tiempo según el botón presionado
        switch (periodo.toLowerCase()) {
            case "semana":
                desde = hoy.minusDays(7).atStartOfDay();
                break;
            case "mes":
                desde = hoy.withDayOfMonth(1).atStartOfDay();
                break;
            case "dia":
            default:
                desde = hoy.atStartOfDay();
                break;
        }

        // Pedimos solo los 5 primeros resultados de la base de datos
        List<Object[]> resultados = ventaRepository.obtenerProductosMasVendidos(empresaId, desde, PageRequest.of(0, 5));
        
        // Mapeamos el resultado de Object[] a un JSON limpio [{nombre: "Lacteo", total: 12}]
        List<Map<String, Object>> listaFinal = new ArrayList<>();
        for (Object[] fila : resultados) {
            Map<String, Object> map = new HashMap<>();
            map.put("nombre", fila[0]);
            map.put("total", fila[1]);
            listaFinal.add(map);
        }

        return ResponseEntity.ok(listaFinal);
    }
}