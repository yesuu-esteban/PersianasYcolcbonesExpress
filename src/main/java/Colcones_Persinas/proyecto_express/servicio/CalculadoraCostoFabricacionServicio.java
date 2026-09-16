package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.*;
import Colcones_Persinas.proyecto_express.repository.PrecioItemFabricacionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Calcula el COSTO REAL DE FABRICACIÓN de un pedido de Taller (distinto
 * del "Precio de Venta" que ya existía, que en realidad es lo que se le
 * cobra al distribuidor por m² según el ancho de tela).
 *
 * Cada componente (tela, tubo, pesa, cuerda, mecanismo, tapas, pitillo)
 * usa las MISMAS cantidades que el sistema ya calcula normalmente para
 * la ficha técnica (Pedido.getCorteTelaAncho(), getCantidadTapas(), etc.),
 * multiplicadas por el precio unitario que el jefe define libremente en
 * /inventario/precios — así que si sube el precio de la tela, el costo
 * de todos los pedidos se actualiza solo, sin tocar código.
 */
@Service
public class CalculadoraCostoFabricacionServicio {

    private final PrecioItemFabricacionRepository precioRepository;

    public CalculadoraCostoFabricacionServicio(PrecioItemFabricacionRepository precioRepository) {
        this.precioRepository = precioRepository;
    }

    public ResultadoCostoFabricacion calcular(Pedido pedido) {
        List<LineaCostoFabricacion> lineas = new ArrayList<>();
        List<String> faltantes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // Venta Directa y Riel de Onda Serena no usan esta fórmula (por ahora):
        // no tienen tela/tubo/pesa/pitillo en el mismo sentido que fabricación normal.
        if (pedido.isVentaDirecta() || pedido.isRielOndaSerena()) {
            return new ResultadoCostoFabricacion(lineas, BigDecimal.ZERO, faltantes);
        }

        // ── Tela: ancho de corte × alto de corte × precio por m² ──
        total = total.add(agregarPorArea(lineas, faltantes, "Tela",
                pedido.getCorteTelaAncho(), pedido.getCorteTelaAlto()));

        // ── Tubo: el nombre depende de si el pedido usa R16, R24 u R8 ──
        String nombreTubo = "Tubo " + pedido.getTuboRecomendado();
        total = total.add(agregarPorAncho(lineas, faltantes, nombreTubo, pedido.getCorteTuberia()));

        // ── Pesa: acompaña al tubo, mismo ancho de corte ──
        total = total.add(agregarPorAncho(lineas, faltantes, "Pesa", pedido.getCorteTuberia()));

        // ── Cuerda / Cadenilla (Blackout): metros de cuerda (3 o 4 según altura) ──
        total = total.add(agregarPorLargo(lineas, faltantes, "Cuerda / Cadenilla (Blackout)",
                pedido.getMetrosCuerda()));

        // ── Mecanismo (Control y accesorios): precio fijo por pedido ──
        total = total.add(agregarFijo(lineas, faltantes, "Mecanismo (Control y accesorios)"));

        // ── Tapas: cantidad de tapas que use este pedido en particular ──
        total = total.add(agregarPorCantidad(lineas, faltantes, "Tapas", pedido.getCantidadTapas()));

        // ── Pitillo: mismo ancho de corte de tela ──
        total = total.add(agregarPorAncho(lineas, faltantes, "Pitillo", pedido.getCortePitilloPesa()));

        return new ResultadoCostoFabricacion(lineas, total, faltantes);
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS — cada uno busca el precio por nombre; si no existe,
    // agrega el nombre a "faltantes" y devuelve 0 sin romper el cálculo.
    // ═══════════════════════════════════════════════════════════════

    private BigDecimal agregarPorArea(List<LineaCostoFabricacion> lineas, List<String> faltantes,
                                       String nombre, double ancho, double alto) {
        Optional<PrecioItemFabricacion> item = precioRepository.findByNombreIgnoreCase(nombre);
        if (item.isEmpty()) {
            faltantes.add(nombre);
            lineas.add(new LineaCostoFabricacion(nombre, "Sin precio definido", BigDecimal.ZERO, false));
            return BigDecimal.ZERO;
        }
        BigDecimal precio = item.get().getPrecioUnitario();
        BigDecimal valor = BigDecimal.valueOf(ancho * alto).multiply(precio).setScale(0, RoundingMode.HALF_UP);
        String formula = String.format("%.3f m × %.3f m × $%,.0f", ancho, alto, precio);
        lineas.add(new LineaCostoFabricacion(nombre, formula, valor, true));
        return valor;
    }

    private BigDecimal agregarPorAncho(List<LineaCostoFabricacion> lineas, List<String> faltantes,
                                        String nombre, double ancho) {
        Optional<PrecioItemFabricacion> item = precioRepository.findByNombreIgnoreCase(nombre);
        if (item.isEmpty()) {
            faltantes.add(nombre);
            lineas.add(new LineaCostoFabricacion(nombre, "Sin precio definido", BigDecimal.ZERO, false));
            return BigDecimal.ZERO;
        }
        BigDecimal precio = item.get().getPrecioUnitario();
        BigDecimal valor = BigDecimal.valueOf(ancho).multiply(precio).setScale(0, RoundingMode.HALF_UP);
        String formula = String.format("%.3f m × $%,.0f", ancho, precio);
        lineas.add(new LineaCostoFabricacion(nombre, formula, valor, true));
        return valor;
    }

    private BigDecimal agregarPorLargo(List<LineaCostoFabricacion> lineas, List<String> faltantes,
                                        String nombre, double largo) {
        Optional<PrecioItemFabricacion> item = precioRepository.findByNombreIgnoreCase(nombre);
        if (item.isEmpty()) {
            faltantes.add(nombre);
            lineas.add(new LineaCostoFabricacion(nombre, "Sin precio definido", BigDecimal.ZERO, false));
            return BigDecimal.ZERO;
        }
        BigDecimal precio = item.get().getPrecioUnitario();
        BigDecimal valor = BigDecimal.valueOf(largo).multiply(precio).setScale(0, RoundingMode.HALF_UP);
        String formula = String.format("%.1f m × $%,.0f", largo, precio);
        lineas.add(new LineaCostoFabricacion(nombre, formula, valor, true));
        return valor;
    }

    private BigDecimal agregarPorCantidad(List<LineaCostoFabricacion> lineas, List<String> faltantes,
                                           String nombre, int cantidad) {
        Optional<PrecioItemFabricacion> item = precioRepository.findByNombreIgnoreCase(nombre);
        if (item.isEmpty()) {
            faltantes.add(nombre);
            lineas.add(new LineaCostoFabricacion(nombre, "Sin precio definido", BigDecimal.ZERO, false));
            return BigDecimal.ZERO;
        }
        BigDecimal precio = item.get().getPrecioUnitario();
        BigDecimal valor = BigDecimal.valueOf(cantidad).multiply(precio).setScale(0, RoundingMode.HALF_UP);
        String formula = String.format("%d und. × $%,.0f", cantidad, precio);
        lineas.add(new LineaCostoFabricacion(nombre, formula, valor, true));
        return valor;
    }

    private BigDecimal agregarFijo(List<LineaCostoFabricacion> lineas, List<String> faltantes, String nombre) {
        Optional<PrecioItemFabricacion> item = precioRepository.findByNombreIgnoreCase(nombre);
        if (item.isEmpty()) {
            faltantes.add(nombre);
            lineas.add(new LineaCostoFabricacion(nombre, "Sin precio definido", BigDecimal.ZERO, false));
            return BigDecimal.ZERO;
        }
        BigDecimal precio = item.get().getPrecioUnitario();
        lineas.add(new LineaCostoFabricacion(nombre, "Precio fijo", precio, true));
        return precio;
    }
}