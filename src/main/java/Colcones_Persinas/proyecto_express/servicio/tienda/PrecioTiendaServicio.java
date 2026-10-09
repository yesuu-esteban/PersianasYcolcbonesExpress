package Colcones_Persinas.proyecto_express.servicio.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.ItemOrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.TelaTienda;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula el precio de la tienda. ÚNICA fuente de verdad: el navegador solo muestra
 * lo que esto devuelve, y al pagar se vuelve a calcular aquí (nunca se confía en el
 * precio que mande el navegador).
 *
 * Regla (la misma que usa fábrica en Pedido.getPrecioVenta):
 *  1. Corte de alto = alto + 0,20 m.
 *  2. Se usa el rollo más angosto que alcance para ese corte: 1,83 → 2,50 → 3,00 m
 *     (saltando los rollos que esa tela no tenga). Si el corte no cabe en ninguno de sus rollos
 *     (por ejemplo, persianas de más de 2,80 m de alto), se cobra con el rollo más ancho que tenga esa tela.
 *  3. Precio = ancho × alto (m², con un mínimo cobrable) × precio por m² de ese rollo.
 *  4. Si el cliente lo pide con cabezal: + ancho (m) × valor del cabezal por metro.
 *     (Enrollado al contrario no cambia el precio.)
 *
 * Riel de onda serena: ancho (m) × precio por metro del sistema elegido (bastón o control).
 * Se cobra el ancho exacto; hacia dónde abre no cambia el precio.
 *
 * Las medidas llegan y se guardan en centímetros, pero al cliente siempre se le
 * habla en metros (así se mide en el negocio).
 *
 * No hay medida máxima: solo el mínimo de cada producto. Lo de más de 20 m se toma como
 * un error al escribir (por ejemplo "120" pensando en centímetros).
 */
@Service
public class PrecioTiendaServicio {

    public static final double MARGEN_CORTE_ALTO = 0.20;
    public static final double[] ROLLOS = {1.83, 2.50, 3.00};
    /** Más de esto (en cm) seguro es un error al escribir la medida. */
    public static final int MEDIDA_IMPOSIBLE_CM = 2000;

    /**
     * Resultado de una cotización. Si ok = false, "mensaje" explica qué pasó.
     * precioUnitario ya incluye el cabezal; precioCabezal dice cuánto de ese valor es del cabezal (0 si no lleva).
     */
    public record Cotizacion(boolean ok, String mensaje, BigDecimal precioUnitario, BigDecimal subtotal,
                             double m2, double m2Reales, Double rollo, int cantidad, BigDecimal precioCabezal) {
        static Cotizacion error(String mensaje) {
            return new Cotizacion(false, mensaje, null, null, 0, 0, null, 0, BigDecimal.ZERO);
        }
    }

    /** Sin cabezal. */
    public Cotizacion cotizar(ProductoTienda p, TelaTienda tela, Integer anchoCm, Integer altoCm, Integer cantidad) {
        return cotizar(p, tela, anchoCm, altoCm, cantidad, false, null);
    }

    public Cotizacion cotizar(ProductoTienda p, TelaTienda tela, Integer anchoCm, Integer altoCm, Integer cantidad,
                              boolean conCabezal) {
        return cotizar(p, tela, anchoCm, altoCm, cantidad, conCabezal, null);
    }

    /** @param sistemaRiel solo para rieles: "BASTON" o "CONTROL" */
    public Cotizacion cotizar(ProductoTienda p, TelaTienda tela, Integer anchoCm, Integer altoCm, Integer cantidad,
                              boolean conCabezal, String sistemaRiel) {
        if (p == null || !p.isActivo()) return Cotizacion.error("Este producto no está disponible.");
        int cant = cantidad != null ? cantidad : 1;
        if (cant < 1 || cant > 50) return Cotizacion.error("La cantidad debe estar entre 1 y 50.");

        // ── Riel de onda serena: por metro de ancho ──
        if (p.isRiel()) return cotizarRiel(p, anchoCm, sistemaRiel, cant);

        // ── Precio fijo ──
        if (!p.isPorMetro()) {
            if (p.getPrecioUnidad() == null || p.getPrecioUnidad().signum() <= 0) {
                return Cotizacion.error("Este producto se cotiza con un asesor. Escríbenos por WhatsApp.");
            }
            BigDecimal unit = p.getPrecioUnidad().setScale(0, RoundingMode.HALF_UP);
            return new Cotizacion(true, null, unit, unit.multiply(BigDecimal.valueOf(cant)), 0, 0, null, cant, BigDecimal.ZERO);
        }
        if (conCabezal && !p.isCabezalDisponible()) return Cotizacion.error("Este producto no tiene la opción de cabezal.");

        // ── Por metro cuadrado ──
        if (tela == null || !tela.isActiva() || tela.getProducto() == null || tela.getProducto().getId() != p.getId()) {
            return Cotizacion.error("Elige una tela.");
        }
        if (anchoCm == null || altoCm == null) return Cotizacion.error("Escribe el ancho y el alto en metros.");
        if (anchoCm > MEDIDA_IMPOSIBLE_CM || altoCm > MEDIDA_IMPOSIBLE_CM) {
            return Cotizacion.error("Revisa la medida: escríbela en metros. Por ejemplo, 1 metro con 20 centímetros es 1,20.");
        }
        if (anchoCm < p.getAnchoMinCm()) {
            return Cotizacion.error("El ancho mínimo es " + ItemOrdenTienda.enMetros(p.getAnchoMinCm()) + " m.");
        }
        if (altoCm < p.getAltoMinCm()) {
            return Cotizacion.error("El alto mínimo es " + ItemOrdenTienda.enMetros(p.getAltoMinCm()) + " m.");
        }

        double ancho = anchoCm / 100.0;
        double alto = altoCm / 100.0;
        double corteAlto = alto + MARGEN_CORTE_ALTO;

        // Rollo más angosto que alcance y que esa tela tenga con precio
        Double rollo = null;
        BigDecimal precioM2 = null;
        for (double r : ROLLOS) {
            if (corteAlto <= r + 0.0001) {
                BigDecimal precio = tela.precioParaRollo(r);
                if (precio != null && precio.signum() > 0) {
                    rollo = r;
                    precioM2 = precio;
                    break;
                }
            }
        }
        // No cabe en ningún rollo que tenga esa tela (por ejemplo, más de 2,80 m de alto):
        // se cobra con el rollo más ancho que tenga esa tela
        if (rollo == null) {
            for (int i = ROLLOS.length - 1; i >= 0; i--) {
                BigDecimal precio = tela.precioParaRollo(ROLLOS[i]);
                if (precio != null && precio.signum() > 0) {
                    rollo = ROLLOS[i];
                    precioM2 = precio;
                    break;
                }
            }
        }
        if (rollo == null) {
            return Cotizacion.error("Para esta medida necesitamos asesorarte. Escríbenos por WhatsApp y te cotizamos.");
        }

        double m2Reales = redondear2(ancho * alto);
        double m2 = Math.max(m2Reales, p.getM2Minimo());
        BigDecimal unit = precioM2.multiply(BigDecimal.valueOf(m2)).setScale(0, RoundingMode.HALF_UP);

        // Cabezal: valor por metro de ancho
        BigDecimal cabezal = BigDecimal.ZERO;
        if (conCabezal) {
            BigDecimal porMetro = p.getPrecioCabezalMetro() != null ? p.getPrecioCabezalMetro() : BigDecimal.ZERO;
            cabezal = porMetro.multiply(BigDecimal.valueOf(ancho)).setScale(0, RoundingMode.HALF_UP);
            unit = unit.add(cabezal);
        }
        return new Cotizacion(true, null, unit, unit.multiply(BigDecimal.valueOf(cant)), m2, m2Reales, rollo, cant, cabezal);
    }

    private Cotizacion cotizarRiel(ProductoTienda p, Integer anchoCm, String sistema, int cant) {
        BigDecimal porMetro = p.precioRielPorMetro(sistema);
        if (porMetro == null) {
            return Cotizacion.error(p.isConBaston() && p.isConControl() ? "Elige si lo quieres con bastón o con control."
                    : "Esa opción no está disponible para este riel.");
        }
        if (anchoCm == null) return Cotizacion.error("Escribe el ancho en metros.");
        if (anchoCm > MEDIDA_IMPOSIBLE_CM) {
            return Cotizacion.error("Revisa la medida: escríbela en metros. Por ejemplo, 1 metro con 20 centímetros es 1,20.");
        }
        if (anchoCm < p.getAnchoMinCm()) {
            return Cotizacion.error("El ancho mínimo es " + ItemOrdenTienda.enMetros(p.getAnchoMinCm()) + " m.");
        }
        double ancho = anchoCm / 100.0;
        BigDecimal unit = porMetro.multiply(BigDecimal.valueOf(ancho)).setScale(0, RoundingMode.HALF_UP);
        return new Cotizacion(true, null, unit, unit.multiply(BigDecimal.valueOf(cant)), 0, 0, null, cant, BigDecimal.ZERO);
    }

    private static double redondear2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}