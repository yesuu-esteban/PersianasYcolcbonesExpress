package Colcones_Persinas.proyecto_express.servicio.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CategoriaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.CuentaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.MovimientoContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.SubcategoriaContable;
import Colcones_Persinas.proyecto_express.repository.contabilidad.CategoriaContableRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.CuentaContableRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.MovimientoContableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Arma los reportes de la contabilidad (pantalla "Reportes"):
 *  - Resumen general: ingresos, costos, gastos y utilidad del periodo, más los saldos de hoy,
 *    lo que falta por cobrar y lo que falta por pagar.
 *  - Personalizado: solo las categorías y subcategorías que se escojan (por ejemplo, solo NÓMINA),
 *    y si se quiere, solo de algunas cuentas.
 *
 * Los dos traen los totales por categoría y subcategoría y la lista de cada movimiento.
 * Los traslados entre cuentas no salen (no son ingreso ni gasto). Lo de antes del inicio en cero tampoco.
 * El Excel y el PDF salen de este mismo reporte (ReporteContableExcel y ReporteContablePdf).
 */
@Service
public class ReporteContableServicio {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<String> ORDEN_CLASES = List.of(CategoriaContable.INGRESO, CategoriaContable.COSTO,
            CategoriaContable.GASTO, CategoriaContable.OTRO);

    private final ContabilidadServicio contabilidad;
    private final MovimientoContableRepository movimientoRepository;
    private final CategoriaContableRepository categoriaRepository;
    private final CuentaContableRepository cuentaRepository;

    public ReporteContableServicio(ContabilidadServicio contabilidad, MovimientoContableRepository movimientoRepository,
                                   CategoriaContableRepository categoriaRepository, CuentaContableRepository cuentaRepository) {
        this.contabilidad = contabilidad;
        this.movimientoRepository = movimientoRepository;
        this.categoriaRepository = categoriaRepository;
        this.cuentaRepository = cuentaRepository;
    }

    // ═══════════════════════════════════════════════════════════════
    // LO QUE SE PIDE Y LO QUE SALE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lo que se escogió en la pantalla.
     * @param general       true = resumen general (todo); false = solo las subcategorías escogidas
     * @param subcategorias ids de las subcategorías escogidas (solo para el personalizado)
     * @param cuentas       ids de las cuentas (vacío = todas; solo para el personalizado)
     * @param titulo        nombre que se le quiere dar al resumen (vacío = se pone solo)
     */
    public record Filtro(boolean general, LocalDate desde, LocalDate hasta,
                         Set<Integer> subcategorias, Set<Integer> cuentas, String titulo) {}

    /** Una subcategoría con lo que se movió en el periodo. */
    public static class LineaSub {
        private final String nombre;
        private BigDecimal total = BigDecimal.ZERO;
        private int cantidad;
        LineaSub(String nombre) { this.nombre = nombre; }
        public String getNombre() { return nombre; }
        public BigDecimal getTotal() { return total; }
        public int getCantidad() { return cantidad; }
    }

    /** Una categoría con sus subcategorías. */
    public static class GrupoCat {
        private final String nombre;
        private BigDecimal total = BigDecimal.ZERO;
        private int cantidad;
        private final List<LineaSub> subs = new ArrayList<>();
        GrupoCat(String nombre) { this.nombre = nombre; }
        public String getNombre() { return nombre; }
        public BigDecimal getTotal() { return total; }
        public int getCantidad() { return cantidad; }
        public List<LineaSub> getSubs() { return subs; }
    }

    /** Ingresos, costos de venta, gastos o "no es gasto", con sus categorías. */
    public static class Seccion {
        private final String clase, nombre;
        private BigDecimal total = BigDecimal.ZERO;
        private int cantidad;
        private final List<GrupoCat> grupos = new ArrayList<>();
        Seccion(String clase, String nombre) { this.clase = clase; this.nombre = nombre; }
        public String getClase() { return clase; }
        public String getNombre() { return nombre; }
        public BigDecimal getTotal() { return total; }
        public int getCantidad() { return cantidad; }
        public List<GrupoCat> getGrupos() { return grupos; }
        public boolean isDeIngreso() { return CategoriaContable.INGRESO.equals(clase); }
    }

    /** Un movimiento ya pasado a textos. El valor va con signo: negativo si es una devolución. */
    public static class FilaDetalle {
        private String fecha = "", tipo = "", categoria = "", subcategoria = "", cuenta = "", tercero = "", descripcion = "";
        private LocalDate fechaDia;
        private BigDecimal valor = BigDecimal.ZERO;
        private boolean deIngreso;
        public String getFecha() { return fecha; }
        public LocalDate getFechaDia() { return fechaDia; }
        public String getTipo() { return tipo; }
        public String getCategoria() { return categoria; }
        public String getSubcategoria() { return subcategoria; }
        public String getCuenta() { return cuenta; }
        public String getTercero() { return tercero; }
        public String getDescripcion() { return descripcion; }
        public BigDecimal getValor() { return valor; }
        /** ¿Es de una categoría de ingresos? (para pintarlo en verde) */
        public boolean isDeIngreso() { return deIngreso; }
    }

    /** Una cuenta con su saldo de hoy (solo en el resumen general). */
    public static class SaldoCuenta {
        private final String nombre;
        private final BigDecimal saldo;
        SaldoCuenta(String nombre, BigDecimal saldo) { this.nombre = nombre; this.saldo = saldo; }
        public String getNombre() { return nombre; }
        public BigDecimal getSaldo() { return saldo; }
    }

    /** El reporte completo, listo para la pantalla, el Excel y el PDF. */
    public static class Reporte {
        private boolean general;
        private String titulo = "", periodoTexto = "", filtrosTexto = "", generado = "", avisoInicio = "";
        private LocalDate desde, hasta;
        private final List<Seccion> secciones = new ArrayList<>();
        private final List<FilaDetalle> detalle = new ArrayList<>();
        private BigDecimal totalIngresos = BigDecimal.ZERO, totalCostos = BigDecimal.ZERO,
                totalGastos = BigDecimal.ZERO, totalOtros = BigDecimal.ZERO;
        // Solo en el resumen general
        private final List<SaldoCuenta> saldos = new ArrayList<>();
        private BigDecimal totalCuentas = BigDecimal.ZERO, porCobrar = BigDecimal.ZERO, porPagar = BigDecimal.ZERO;

        public boolean isGeneral() { return general; }
        public String getTitulo() { return titulo; }
        public String getPeriodoTexto() { return periodoTexto; }
        /** Qué se escogió (categorías y cuentas), en una línea. */
        public String getFiltrosTexto() { return filtrosTexto; }
        /** Cuándo y quién lo sacó. */
        public String getGenerado() { return generado; }
        /** Si el periodo empezaba antes del inicio en cero, se explica aquí (vacío si no). */
        public String getAvisoInicio() { return avisoInicio; }
        public LocalDate getDesde() { return desde; }
        public LocalDate getHasta() { return hasta; }
        public List<Seccion> getSecciones() { return secciones; }
        public List<FilaDetalle> getDetalle() { return detalle; }
        public int getCantidad() { return detalle.size(); }
        public boolean isVacio() { return detalle.isEmpty(); }
        public BigDecimal getTotalIngresos() { return totalIngresos; }
        public BigDecimal getTotalCostos() { return totalCostos; }
        public BigDecimal getTotalGastos() { return totalGastos; }
        public BigDecimal getTotalOtros() { return totalOtros; }
        /** Todo lo que salió: costos + gastos + "no es gasto". */
        public BigDecimal getTotalEgresos() { return totalCostos.add(totalGastos).add(totalOtros); }
        public BigDecimal getUtilidadBruta() { return totalIngresos.subtract(totalCostos); }
        public BigDecimal getUtilidadNeta() { return getUtilidadBruta().subtract(totalGastos); }
        /** Lo que quedó de plata después de todo (incluye lo que "no es gasto": compras de activos, préstamos, retiros). */
        public BigDecimal getResultadoEnCaja() { return getUtilidadNeta().subtract(totalOtros); }
        /** Ingresos − todo lo que salió (para el personalizado). */
        public BigDecimal getNeto() { return totalIngresos.subtract(getTotalEgresos()); }
        public boolean isHayIngresos() { return secciones.stream().anyMatch(Seccion::isDeIngreso); }
        public boolean isHayEgresos() { return secciones.stream().anyMatch(s -> !s.isDeIngreso()); }
        public List<SaldoCuenta> getSaldos() { return saldos; }
        public BigDecimal getTotalCuentas() { return totalCuentas; }
        public BigDecimal getPorCobrar() { return porCobrar; }
        public BigDecimal getPorPagar() { return porPagar; }
        public BigDecimal getPosicionNeta() { return totalCuentas.add(porCobrar).subtract(porPagar); }
        /** Nombre para el archivo: "Resumen_general_2026-11-01_a_2026-11-30". */
        public String getNombreArchivo() {
            String base = java.text.Normalizer.normalize(titulo, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (base.isEmpty()) base = "Resumen";
            if (base.length() > 60) base = base.substring(0, 60);
            return base + "_" + desde + "_a_" + hasta;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ARMAR EL REPORTE
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Reporte generar(Filtro f, String usuario) {
        if (f.desde() == null || f.hasta() == null) throw new IllegalArgumentException("Escoge desde qué día y hasta qué día.");
        if (f.desde().isAfter(f.hasta())) throw new IllegalArgumentException("La fecha \"desde\" no puede ser después de la fecha \"hasta\".");
        Set<Integer> subsElegidas = f.subcategorias() == null ? Set.of() : f.subcategorias();
        Set<Integer> cuentasElegidas = f.cuentas() == null ? Set.of() : f.cuentas();
        if (!f.general() && subsElegidas.isEmpty()) {
            throw new IllegalArgumentException("Escoge al menos una categoría o subcategoría para el resumen.");
        }

        Reporte r = new Reporte();
        r.general = f.general();
        r.hasta = f.hasta();
        // Lo de antes del inicio en cero no cuenta
        r.desde = contabilidad.desdeInicio(f.desde());
        if (r.desde.isAfter(f.desde()) && contabilidad.configuracion() != null) {
            r.avisoInicio = "La contabilidad empieza el " + contabilidad.configuracion().getFechaInicioFormateada()
                    + ": el reporte cuenta desde ese día.";
        }
        r.periodoTexto = "Del " + r.desde.format(FMT) + " al " + r.hasta.format(FMT);
        r.generado = "Generado el " + LocalDateTime.now(ZoneId.of("America/Bogota")).format(FMT_HORA)
                + (usuario == null || usuario.isBlank() ? "" : " por " + usuario);

        // Catálogo: para el orden de categorías y subcategorías, y para los textos del filtro
        List<CategoriaContable> categorias = categoriaRepository.findAllByOrderByOrdenAscNombreAsc();
        Map<Integer, Integer> ordenCategoria = new HashMap<>();
        Map<Integer, Integer> ordenSub = new HashMap<>();
        int i = 0;
        for (CategoriaContable c : categorias) {
            ordenCategoria.put(c.getId(), i++);
            int j = 0;
            for (SubcategoriaContable s : c.getSubcategorias()) ordenSub.put(s.getId(), j++);
        }

        // Los movimientos del periodo que entran en el reporte
        List<MovimientoContable> movs = new ArrayList<>();
        if (!r.desde.isAfter(r.hasta)) {
            for (MovimientoContable m : movimientoRepository.findByFechaBetweenOrderByFechaDescIdDesc(r.desde, r.hasta)) {
                if (MovimientoContable.TRASLADO.equals(m.getTipo())) continue;
                if (m.getSubcategoria() == null || m.getSubcategoria().getCategoria() == null) continue;
                if (!f.general()) {
                    if (!subsElegidas.contains(m.getSubcategoria().getId())) continue;
                    if (!cuentasElegidas.isEmpty() && (m.getCuenta() == null || !cuentasElegidas.contains(m.getCuenta().getId()))) continue;
                }
                movs.add(m);
            }
        }
        movs.sort(Comparator.comparing(MovimientoContable::getFecha).thenComparingInt(MovimientoContable::getId));

        // Totales por sección → categoría → subcategoría (en el orden del plan de cuentas)
        Map<String, Seccion> secciones = new LinkedHashMap<>();
        Map<Integer, GrupoCat> grupos = new HashMap<>();
        Map<Integer, LineaSub> lineas = new HashMap<>();
        Map<Integer, CategoriaContable> categoriaDeGrupo = new HashMap<>();
        Map<Integer, SubcategoriaContable> subDeLinea = new HashMap<>();
        for (MovimientoContable m : movs) {
            SubcategoriaContable s = m.getSubcategoria();
            CategoriaContable c = s.getCategoria();
            BigDecimal valor = m.getValor() == null ? BigDecimal.ZERO : m.getValor();
            // Igual que el Resumen: un ingreso en una categoría de egresos (o al revés) es una devolución y resta
            if (MovimientoContable.INGRESO.equals(m.getTipo()) != c.isDeIngreso()) valor = valor.negate();

            String clase = ORDEN_CLASES.contains(c.getClase()) ? c.getClase() : CategoriaContable.OTRO;
            Seccion sec = secciones.computeIfAbsent(clase, k -> new Seccion(k, nombreSeccion(k)));
            GrupoCat g = grupos.get(c.getId());
            if (g == null) {
                g = new GrupoCat(texto(c.getNombre()));
                grupos.put(c.getId(), g);
                categoriaDeGrupo.put(c.getId(), c);
                sec.grupos.add(g);
            }
            LineaSub l = lineas.get(s.getId());
            if (l == null) {
                l = new LineaSub(texto(s.getNombre()));
                lineas.put(s.getId(), l);
                subDeLinea.put(s.getId(), s);
                g.subs.add(l);
            }
            l.total = l.total.add(valor);
            l.cantidad++;
            g.total = g.total.add(valor);
            g.cantidad++;
            sec.total = sec.total.add(valor);
            sec.cantidad++;

            FilaDetalle d = new FilaDetalle();
            d.fechaDia = m.getFecha();
            d.fecha = texto(m.getFechaFormateada());
            d.tipo = texto(m.getTipoEtiqueta());
            d.categoria = texto(c.getNombre());
            d.subcategoria = texto(s.getNombre());
            d.cuenta = m.getCuenta() != null ? texto(m.getCuenta().getNombre()) : "";
            d.tercero = texto(m.getTercero());
            d.descripcion = texto(m.getDescripcion());
            d.valor = valor;
            d.deIngreso = c.isDeIngreso();
            r.detalle.add(d);
        }

        // Ordenar como en Categorías
        for (String clase : ORDEN_CLASES) {
            Seccion sec = secciones.get(clase);
            if (sec == null) continue;
            List<Map.Entry<Integer, GrupoCat>> deEsta = new ArrayList<>();
            for (Map.Entry<Integer, GrupoCat> e : grupos.entrySet()) if (sec.grupos.contains(e.getValue())) deEsta.add(e);
            deEsta.sort(Comparator.comparingInt(e -> ordenCategoria.getOrDefault(e.getKey(), Integer.MAX_VALUE)));
            sec.grupos.clear();
            for (Map.Entry<Integer, GrupoCat> e : deEsta) {
                GrupoCat g = e.getValue();
                List<Map.Entry<Integer, LineaSub>> subs = new ArrayList<>();
                for (Map.Entry<Integer, LineaSub> l : lineas.entrySet()) if (g.subs.contains(l.getValue())) subs.add(l);
                subs.sort(Comparator.comparingInt(l -> ordenSub.getOrDefault(l.getKey(), Integer.MAX_VALUE)));
                g.subs.clear();
                for (Map.Entry<Integer, LineaSub> l : subs) g.subs.add(l.getValue());
                sec.grupos.add(g);
            }
            r.secciones.add(sec);
            switch (clase) {
                case CategoriaContable.INGRESO -> r.totalIngresos = sec.total;
                case CategoriaContable.COSTO -> r.totalCostos = sec.total;
                case CategoriaContable.GASTO -> r.totalGastos = sec.total;
                default -> r.totalOtros = sec.total;
            }
        }

        // Textos de lo que se escogió y el título
        if (f.general()) {
            r.titulo = vacio(f.titulo()) ? "Resumen general" : f.titulo().trim();
            r.filtrosTexto = "Todas las categorías y todas las cuentas";
            Map<Integer, BigDecimal> saldos = contabilidad.saldosPorCuenta();
            for (CuentaContable c : cuentaRepository.findAllByOrderByOrdenAscNombreAsc()) {
                BigDecimal saldo = saldos.getOrDefault(c.getId(), BigDecimal.ZERO);
                if (!c.isActiva() && saldo.signum() == 0) continue;
                r.saldos.add(new SaldoCuenta(texto(c.getNombre()), saldo));
                r.totalCuentas = r.totalCuentas.add(saldo);
            }
            r.porCobrar = contabilidad.totalPorCobrar();
            r.porPagar = contabilidad.totalPorPagar();
        } else {
            List<String> partes = new ArrayList<>();
            List<String> categoriasCompletas = new ArrayList<>();
            for (CategoriaContable c : categorias) {
                List<String> escogidas = new ArrayList<>();
                for (SubcategoriaContable s : c.getSubcategorias()) if (subsElegidas.contains(s.getId())) escogidas.add(texto(s.getNombre()));
                if (escogidas.isEmpty()) continue;
                if (escogidas.size() == c.getSubcategorias().size()) {
                    partes.add(texto(c.getNombre()) + " (todas)");
                    categoriasCompletas.add(texto(c.getNombre()));
                } else {
                    partes.add(texto(c.getNombre()) + ": " + String.join(", ", escogidas));
                }
            }
            String cuentasTexto = "todas las cuentas";
            if (!cuentasElegidas.isEmpty()) {
                List<String> nombres = new ArrayList<>();
                for (CuentaContable c : cuentaRepository.findAllByOrderByOrdenAscNombreAsc()) {
                    if (cuentasElegidas.contains(c.getId())) nombres.add(texto(c.getNombre()));
                }
                cuentasTexto = "cuentas: " + String.join(", ", nombres);
            }
            r.filtrosTexto = String.join(" · ", partes) + " — " + cuentasTexto;
            if (!vacio(f.titulo())) {
                r.titulo = f.titulo().trim();
            } else if (partes.size() == 1 && categoriasCompletas.size() == 1) {
                r.titulo = "Resumen de " + categoriasCompletas.get(0);
            } else if (partes.size() == 1) {
                r.titulo = "Resumen de " + partes.get(0).substring(0, partes.get(0).indexOf(':'));
            } else {
                r.titulo = "Resumen personalizado";
            }
        }
        if (r.titulo.length() > 120) r.titulo = r.titulo.substring(0, 120);
        return r;
    }

    private static String nombreSeccion(String clase) {
        return switch (clase) {
            case CategoriaContable.INGRESO -> "Ingresos";
            case CategoriaContable.COSTO -> "Costos de venta";
            case CategoriaContable.GASTO -> "Gastos";
            default -> "No son gasto (activos, préstamos, retiros)";
        };
    }

    private static boolean vacio(String s) { return s == null || s.isBlank(); }

    private static String texto(String s) { return s == null ? "" : s; }
}