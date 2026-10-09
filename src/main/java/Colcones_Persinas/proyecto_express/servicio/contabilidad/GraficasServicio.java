package Colcones_Persinas.proyecto_express.servicio.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CuentaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.MovimientoContable;
import Colcones_Persinas.proyecto_express.repository.contabilidad.CuentaContableRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.MovimientoContableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Los números de la pantalla Gráficas (todo en pesos, sin centavos):
 *  - Pastel de gastos por categoría, pastel de ingresos por subcategoría y pastel de saldos por cuenta.
 *  - Velas japonesas de la plata total en las cuentas, por día (del mes) o por semana (12 semanas).
 *  - Ingresos contra gastos de los últimos 12 meses, con la utilidad de cada mes.
 *
 * Devuelve listas y mapas simples para pasarlos tal cual al JavaScript de la pantalla.
 */
@Service
public class GraficasServicio {

    /** Un pastel muestra máximo 6 tajadas; el resto se junta en "Otros". */
    private static final int MAX_TAJADAS = 6;
    private static final String[] MESES_CORTOS = {"Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"};
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final LocalDate DESDE_SIEMPRE = LocalDate.of(2000, 1, 1);

    private final ContabilidadServicio contabilidad;
    private final CuentaContableRepository cuentaRepository;
    private final MovimientoContableRepository movimientoRepository;

    public GraficasServicio(ContabilidadServicio contabilidad, CuentaContableRepository cuentaRepository,
                            MovimientoContableRepository movimientoRepository) {
        this.contabilidad = contabilidad;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    /**
     * Todos los datos de la pantalla.
     * @param porSemana true = velas por semana (12 semanas); false = una vela por día del mes
     */
    @Transactional(readOnly = true)
    public Map<String, Object> datos(YearMonth mes, boolean porSemana) {
        Map<String, Object> d = new LinkedHashMap<>();
        ContabilidadServicio.Resultado r = contabilidad.resultado(mes.atDay(1), mes.atEndOfMonth(), null);

        // ── Pastel: gastos por categoría (costos de venta, gastos y "no es gasto") ──
        List<ContabilidadServicio.Grupo> egresos = new ArrayList<>();
        egresos.addAll(r.getCostos());
        egresos.addAll(r.getGastos());
        egresos.addAll(r.getOtros());
        List<Map<String, Object>> gastos = new ArrayList<>();
        for (ContabilidadServicio.Grupo g : egresos) {
            List<Map<String, Object>> detalle = new ArrayList<>();
            for (ContabilidadServicio.Linea l : g.getLineas()) {
                if (pesos(l.getValor()) > 0) detalle.add(tajada(l.getNombre(), pesos(l.getValor()), null));
            }
            if (pesos(g.getTotal()) > 0) gastos.add(tajada(g.getNombre(), pesos(g.getTotal()), detalle));
        }
        d.put("gastos", juntarResto(gastos));

        // ── Pastel: ingresos por subcategoría ──
        Map<String, Long> porSub = new LinkedHashMap<>();
        for (ContabilidadServicio.Grupo g : r.getIngresos()) {
            for (ContabilidadServicio.Linea l : g.getLineas()) porSub.merge(l.getNombre(), pesos(l.getValor()), Long::sum);
        }
        List<Map<String, Object>> ingresos = new ArrayList<>();
        porSub.forEach((nombre, valor) -> { if (valor > 0) ingresos.add(tajada(nombre, valor, null)); });
        d.put("ingresos", juntarResto(ingresos));

        // ── Pastel: dónde está la plata hoy (solo saldos positivos; los negativos se avisan aparte) ──
        Map<Integer, BigDecimal> saldos = contabilidad.saldosPorCuenta();
        List<Map<String, Object>> cuentas = new ArrayList<>();
        List<Map<String, Object>> enRojo = new ArrayList<>();
        for (CuentaContable c : cuentaRepository.findAllByOrderByOrdenAscNombreAsc()) {
            long saldo = pesos(saldos.get(c.getId()));
            if (saldo > 0) cuentas.add(tajada(c.getNombre(), saldo, null));
            else if (saldo < 0) enRojo.add(tajada(c.getNombre(), saldo, null));
        }
        d.put("cuentas", juntarResto(cuentas));
        d.put("cuentasEnRojo", enRojo);

        // ── Velas japonesas de la plata total ──
        d.put("velas", velas(mes, porSemana));

        // ── Ingresos contra gastos, últimos 12 meses ──
        List<String> etiquetas = new ArrayList<>();
        List<Long> ing = new ArrayList<>(), gas = new ArrayList<>(), util = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth m = mes.minusMonths(i);
            ContabilidadServicio.Resultado rm = contabilidad.resultado(m.atDay(1), m.atEndOfMonth(), null);
            etiquetas.add(MESES_CORTOS[m.getMonthValue() - 1] + " " + String.valueOf(m.getYear()).substring(2));
            ing.add(pesos(rm.getTotalIngresos()));
            gas.add(pesos(rm.getTotalCostos().add(rm.getTotalGastos())));
            util.add(pesos(rm.getUtilidadNeta()));
        }
        Map<String, Object> meses = new LinkedHashMap<>();
        meses.put("etiquetas", etiquetas);
        meses.put("ingresos", ing);
        meses.put("gastos", gas);
        meses.put("utilidad", util);
        d.put("meses", meses);
        return d;
    }

    /**
     * Una vela por periodo con la plata total de todas las cuentas:
     * abre = cómo empezó, cierra = cómo terminó, alto/bajo = lo más alto y lo más bajo que llegó.
     * Los traslados entre cuentas no cambian el total, así que no mueven la vela.
     */
    private Map<String, Object> velas(YearMonth mes, boolean porSemana) {
        LocalDate hoy = ContabilidadServicio.hoy();
        LocalDate fin = mes.atEndOfMonth().isAfter(hoy) ? hoy : mes.atEndOfMonth();

        // Periodos: días del mes, o 12 semanas (lunes a domingo) que terminan en la semana de "fin"
        List<LocalDate[]> periodos = new ArrayList<>();
        List<String> etiquetas = new ArrayList<>();
        if (porSemana) {
            LocalDate lunes = fin.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11);
            for (int i = 0; i < 12; i++) {
                LocalDate desde = lunes.plusWeeks(i), hasta = desde.plusDays(6);
                if (hasta.isAfter(fin)) hasta = fin;
                periodos.add(new LocalDate[]{desde, hasta});
                etiquetas.add(desde.format(DIA));
            }
        } else if (!mes.atDay(1).isAfter(fin)) {
            for (LocalDate dia = mes.atDay(1); !dia.isAfter(fin); dia = dia.plusDays(1)) {
                periodos.add(new LocalDate[]{dia, dia});
                etiquetas.add(dia.format(DIA));
            }
        }

        // Plata total antes del primer periodo = saldos iniciales + todo lo anterior
        long total = 0;
        for (CuentaContable c : cuentaRepository.findAll()) total += pesos(c.getSaldoInicial());
        List<MovimientoContable> movs = new ArrayList<>(movimientoRepository.findByFechaBetweenOrderByFechaDescIdDesc(DESDE_SIEMPRE, fin));
        movs.sort(Comparator.comparing(MovimientoContable::getFecha).thenComparingInt(MovimientoContable::getId));

        List<long[]> datos = new ArrayList<>();          // [abre, cierra, bajo, alto]
        List<Map<String, Object>> detalle = new ArrayList<>();
        int i = 0;
        LocalDate primero = periodos.isEmpty() ? fin.plusDays(1) : periodos.get(0)[0];
        for (; i < movs.size() && movs.get(i).getFecha().isBefore(primero); i++) total += efecto(movs.get(i));

        for (LocalDate[] p : periodos) {
            long abre = total, alto = total, bajo = total, entradas = 0, salidas = 0;
            int cantidad = 0;
            for (; i < movs.size() && !movs.get(i).getFecha().isAfter(p[1]); i++) {
                MovimientoContable m = movs.get(i);
                long e = efecto(m);
                total += e;
                if (e > 0) entradas += e; else salidas -= e;
                alto = Math.max(alto, total);
                bajo = Math.min(bajo, total);
                if (!MovimientoContable.TRASLADO.equals(m.getTipo())) cantidad++;
            }
            datos.add(new long[]{abre, total, bajo, alto});
            Map<String, Object> det = new LinkedHashMap<>();
            det.put("desde", p[0].format(DIA));
            det.put("hasta", p[1].format(DIA));
            det.put("entradas", entradas);
            det.put("salidas", salidas);
            det.put("movimientos", cantidad);
            detalle.add(det);
        }

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("porSemana", porSemana);
        v.put("etiquetas", etiquetas);
        v.put("datos", datos);
        v.put("detalle", detalle);
        return v;
    }

    /** Cómo cambia la plata total con un movimiento: ingreso suma, egreso resta, traslado no cambia el total. */
    private static long efecto(MovimientoContable m) {
        long v = pesos(m.getValor());
        if (MovimientoContable.INGRESO.equals(m.getTipo())) return v;
        if (MovimientoContable.EGRESO.equals(m.getTipo())) return -v;
        return 0;
    }

    /** Ordena de mayor a menor y deja máximo 6 tajadas; las demás se suman en "Otros". */
    private static List<Map<String, Object>> juntarResto(List<Map<String, Object>> tajadas) {
        tajadas.sort((a, b) -> Long.compare((Long) b.get("valor"), (Long) a.get("valor")));
        if (tajadas.size() <= MAX_TAJADAS) return tajadas;
        List<Map<String, Object>> salida = new ArrayList<>(tajadas.subList(0, MAX_TAJADAS - 1));
        long resto = 0;
        List<Map<String, Object>> detalle = new ArrayList<>();
        for (Map<String, Object> t : tajadas.subList(MAX_TAJADAS - 1, tajadas.size())) {
            resto += (Long) t.get("valor");
            detalle.add(tajada((String) t.get("nombre"), (Long) t.get("valor"), null));
        }
        salida.add(tajada("Otros", resto, detalle));
        return salida;
    }

    private static Map<String, Object> tajada(String nombre, long valor, List<Map<String, Object>> detalle) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("nombre", nombre == null ? "" : nombre);
        t.put("valor", valor);
        t.put("detalle", detalle == null ? List.of() : detalle);
        return t;
    }

    /** Pesos enteros (los valores se guardan sin centavos). */
    private static long pesos(BigDecimal v) {
        return v == null ? 0 : v.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }
}