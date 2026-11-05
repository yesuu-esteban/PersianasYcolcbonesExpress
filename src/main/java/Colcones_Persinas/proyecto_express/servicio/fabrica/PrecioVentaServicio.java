package Colcones_Persinas.proyecto_express.servicio.fabrica;

import Colcones_Persinas.proyecto_express.modelo.fabrica.Insumo;
import Colcones_Persinas.proyecto_express.modelo.fabrica.PrecioVentaEstandar;
import Colcones_Persinas.proyecto_express.repository.fabrica.InsumoRepository;
import Colcones_Persinas.proyecto_express.repository.fabrica.PrecioVentaEstandarRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Precios de venta estándar de Fábrica. Se pueden cambiar a mano (en la pantalla
 * "Precios de venta" o al hacer una venta directa) y el valor nuevo queda guardado.
 *
 *  - Persiana blackout (todo incluido): un valor por m² según el ancho del rollo.
 *  - Solo tela: otro valor por m², también según el ancho del rollo.
 *  - Insumos en venta directa: un valor por unidad (o por metro si se vende por medida).
 *    La lista de insumos es la misma del inventario: si un insumo se borra del
 *    inventario, su precio de venta también se borra.
 *
 * OJO: esto es el precio de VENTA. El costo de FABRICACIÓN es otra cosa y está en
 * "Precios" (/inventario/precios).
 */
@Service
public class PrecioVentaServicio {

    /** Anchos comerciales de rollo, en orden. */
    public static final double[] ANCHOS = {1.83, 2.50, 3.00};

    /** Los valores que tenía el programa fijos antes de que se pudieran cambiar. */
    private static final double[] PERSIANA_POR_DEFECTO = {51200.0, 53800.0, 74250.0};

    private final PrecioVentaEstandarRepository repositorio;
    private final InsumoRepository insumoRepository;

    public PrecioVentaServicio(PrecioVentaEstandarRepository repositorio, InsumoRepository insumoRepository) {
        this.repositorio = repositorio;
        this.insumoRepository = insumoRepository;
    }

    // ─── Claves ─────────────────────────────────────────────────────────────

    /** Ancho de rollo que necesita un corte de este alto (igual que en las persianas). */
    public static double anchoComercialPara(double alto) {
        if (alto <= 1.83 + 0.0001) return 1.83;
        if (alto <= 2.50 + 0.0001) return 2.50;
        return 3.00;
    }

    private static String codigoAncho(double ancho) {
        return String.format(Locale.ROOT, "%.2f", anchoComercialPara(ancho));
    }

    public static String clavePersiana(double anchoComercial) { return "PERSIANA_" + codigoAncho(anchoComercial); }
    public static String claveTela(double anchoComercial)     { return "TELA_" + codigoAncho(anchoComercial); }
    public static String claveInsumo(int insumoId)            { return "INSUMO_" + insumoId; }

    /** "INSUMO_12" → 12; cualquier otra clave → null. */
    public static Integer idInsumoDe(String clave) {
        if (clave == null || !clave.startsWith("INSUMO_")) return null;
        try {
            return Integer.parseInt(clave.substring(7));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String nombrePersiana(double ancho) { return "Persiana blackout · rollo " + codigoAncho(ancho) + " m (por m²)"; }
    public static String nombreTela(double ancho)     { return "Solo tela · rollo " + codigoAncho(ancho) + " m (por m²)"; }

    // ─── Lectura ────────────────────────────────────────────────────────────

    /** El valor guardado; si nunca se guardó: el de siempre para persianas, null para lo demás. */
    public Double valor(String clave) {
        return repositorio.findByClave(clave)
                .map(PrecioVentaEstandar::getValor)
                .orElseGet(() -> porDefecto(clave));
    }

    private static Double porDefecto(String clave) {
        for (int i = 0; i < ANCHOS.length; i++) {
            if (clavePersiana(ANCHOS[i]).equals(clave)) return PERSIANA_POR_DEFECTO[i];
        }
        return null;
    }

    /** Valor del m² de persiana para ese ancho de rollo (nunca null). */
    public double precioPersianaM2(double anchoComercial) {
        Double v = valor(clavePersiana(anchoComercial));
        return v != null ? v : 0.0;
    }

    /**
     * Borra el precio de venta de los insumos que ya no existen en el inventario.
     * Se llama cada vez que se muestran los precios, así un insumo borrado no
     * deja su precio "huérfano". Devuelve cuántos borró.
     */
    @Transactional
    public int limpiarInsumosBorrados() {
        Set<Integer> existentes = new HashSet<>();
        for (Insumo i : insumoRepository.findAll()) existentes.add(i.getId());
        int borrados = 0;
        for (PrecioVentaEstandar p : repositorio.findAllByOrderByClaveAsc()) {
            Integer id = idInsumoDe(p.getClave());
            if (id != null && !existentes.contains(id)) {
                repositorio.delete(p);
                borrados++;
            }
        }
        return borrados;
    }

    /** Todos los precios (clave → valor) para la pantalla; incluye los de persiana aunque no se hayan guardado. */
    public Map<String, Double> mapa() {
        limpiarInsumosBorrados();
        Map<String, Double> m = new LinkedHashMap<>();
        for (double a : ANCHOS) {
            m.put(clavePersiana(a), precioPersianaM2(a));
            m.put(claveTela(a), null);
        }
        for (PrecioVentaEstandar p : repositorio.findAllByOrderByClaveAsc()) {
            m.put(p.getClave(), p.getValor());
        }
        return m;
    }

    public List<PrecioVentaEstandar> guardados() {
        return repositorio.findAllByOrderByClaveAsc();
    }

    // ─── Escritura ──────────────────────────────────────────────────────────

    /**
     * Guarda el valor como nuevo estándar. No hace nada si el valor es vacío,
     * negativo o igual al que ya estaba. Devuelve true si cambió algo.
     */
    @Transactional
    public boolean guardar(String clave, String nombre, Double valor, String usuario) {
        if (clave == null || clave.isBlank() || valor == null || valor < 0 || valor.isNaN()) return false;
        // Solo insumos que existan en el inventario
        Integer idInsumo = idInsumoDe(clave);
        if (clave.startsWith("INSUMO_") && (idInsumo == null || insumoRepository.findById(idInsumo).isEmpty())) return false;
        double redondeado = Math.round(valor * 100.0) / 100.0;
        Double actual = valor(clave);
        PrecioVentaEstandar p = repositorio.findByClave(clave).orElse(null);
        if (p != null && actual != null && Math.abs(actual - redondeado) < 0.005) return false;
        if (p == null) {
            if (actual != null && Math.abs(actual - redondeado) < 0.005) return false;
            p = new PrecioVentaEstandar();
            p.setClave(clave);
        }
        if (nombre != null && !nombre.isBlank()) p.setNombre(nombre.trim());
        p.setValor(redondeado);
        p.setActualizadoPor(usuario);
        repositorio.save(p);
        return true;
    }
}