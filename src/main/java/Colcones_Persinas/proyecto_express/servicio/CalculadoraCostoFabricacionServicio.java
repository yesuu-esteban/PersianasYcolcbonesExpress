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
 * Calcula el COSTO REAL DE FABRICACIÓN de un pedido de Taller.
 *
 * NOTA sobre "Tope Pesa": ESTE ÍTEM SE ELIMINÓ. Antes existía como un
 * concepto de precio y de inventario separado de "Tapa Perfil", pero eran
 * el mismo accesorio físico. Ahora solo existe "Tapas de Perfil (Pesa)"
 * con 2 unidades, obligatorio en TODO pedido de fabricación.
 *
 * NOTA sobre "Mecanismo (Control y accesorios)": ítem FIJO por pedido.
 * Existen dos precios distintos:
 *   - "Mecanismo (Control y accesorios)"      → Control R16, R8 A, R8 B
 *   - "Mecanismo (Control y accesorios) R24"  → Control R24
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

        if (pedido.isVentaDirecta() || pedido.isRielOndaSerena()) {
            return new ResultadoCostoFabricacion(lineas, BigDecimal.ZERO, faltantes);
        }

        total = total.add(agregarPorArea(lineas, faltantes, "Tela",
                pedido.getCorteTelaAncho(), pedido.getCorteTelaAlto()));

        String nombreTubo = "Tubo " + pedido.getTuboRecomendado();
        total = total.add(agregarPorAncho(lineas, faltantes, nombreTubo, pedido.getCorteTuberia()));

        total = total.add(agregarPorAncho(lineas, faltantes, "Pesa", pedido.getCorteTuberia()));

        // Tapas de Perfil (Pesa): obligatorias, 2 und., con o sin cabezal.
        // (Antes también existía "Tope Pesa" por separado; se eliminó/fusionó aquí.)
        total = total.add(agregarPorCantidad(lineas, faltantes, "Tapas de Perfil (Pesa)", pedido.getCantidadTapasPerfil()));

        total = total.add(agregarPorLargo(lineas, faltantes, "Cuerda / Cadenilla (Blackout)",
                pedido.getMetrosCuerda()));

        String nombreMecanismo = "Control R24".equals(pedido.getTipoControl())
                ? "Mecanismo (Control y accesorios) R24"
                : "Mecanismo (Control y accesorios)";
        total = total.add(agregarFijo(lineas, faltantes, nombreMecanismo));

        total = total.add(agregarPorCantidad(lineas, faltantes, "Tapa Cabezal", pedido.getCantidadTapas()));

        total = total.add(agregarPorAncho(lineas, faltantes, "Pitillo", pedido.getCortePitilloPesa()));

        return new ResultadoCostoFabricacion(lineas, total, faltantes);
    }

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