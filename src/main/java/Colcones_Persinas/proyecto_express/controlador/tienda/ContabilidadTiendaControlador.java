package Colcones_Persinas.proyecto_express.controlador.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.CuentaTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.MovimientoTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.CuentaTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.MovimientoTiendaRepository;
import Colcones_Persinas.proyecto_express.servicio.tienda.ContabilidadTiendaServicio;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Contabilidad PROPIA de la tienda virtual (TIENDA_ADMIN y ADMIN), aparte de la de Almacén.
 *
 *   /tienda-admin/contabilidad              resumen del mes: lo vendido, ingresos, gastos, resultado,
 *                                           saldos de las cuentas y ventas sin anotar
 *   /tienda-admin/contabilidad/movimientos  ingresos y gastos del mes; agregar, corregir y borrar
 *   /tienda-admin/contabilidad/cuentas      cuentas de la tienda y a cuál entra la plata de Wompi y de Addi
 */
@Controller
@RequestMapping("/tienda-admin/contabilidad")
@PreAuthorize("hasAnyRole('TIENDA_ADMIN','ADMIN')")
public class ContabilidadTiendaControlador {

    private static final Locale CO = new Locale("es", "CO");

    private final ContabilidadTiendaServicio servicio;
    private final CuentaTiendaRepository cuentaRepository;
    private final MovimientoTiendaRepository movimientoRepository;

    public ContabilidadTiendaControlador(ContabilidadTiendaServicio servicio, CuentaTiendaRepository cuentaRepository,
                                         MovimientoTiendaRepository movimientoRepository) {
        this.servicio = servicio;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    // ═══════════════════════════════════════════════════════════════
    // RESUMEN
    // ═══════════════════════════════════════════════════════════════

    @GetMapping({"", "/"})
    public String resumen(@RequestParam(name = "mes", required = false) String mes, Model model) {
        YearMonth periodo = mes(mes);
        datosDelMes(periodo, model);
        model.addAttribute("resumen", servicio.resumen(periodo));
        model.addAttribute("cuentasActivas", cuentaRepository.findByActivaTrueOrderByOrdenAscNombreAsc());
        model.addAttribute("hayCuentas", cuentaRepository.count() > 0);
        model.addAttribute("hoy", ContabilidadTiendaServicio.hoy().toString());
        return "tienda-admin/contabilidad_resumen";
    }

    /** Anota a mano la plata de una compra que quedó sin anotar. */
    @PostMapping("/ventas/{ordenId}/anotar")
    public String anotarVenta(@PathVariable int ordenId, @RequestParam Map<String, String> f, RedirectAttributes ra) {
        try {
            MovimientoTienda m = servicio.anotarVenta(ordenId, entero(f.get("cuentaId")), fecha(f.get("fecha")), usuarioActual());
            ra.addFlashAttribute("mensaje", "Venta anotada: $" + miles(m.getValor()) + " en " + m.getCuenta().getNombre() + ".");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (f.get("mes") != null && !f.get("mes").isBlank()) ra.addAttribute("mes", mes(f.get("mes")).toString());
        return "redirect:/tienda-admin/contabilidad";
    }

    // ═══════════════════════════════════════════════════════════════
    // MOVIMIENTOS
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/movimientos")
    public String movimientos(@RequestParam(name = "mes", required = false) String mes,
                              @RequestParam(name = "editar", required = false) Integer editar,
                              Model model) {
        YearMonth periodo = mes(mes);
        datosDelMes(periodo, model);
        List<MovimientoTienda> lista = servicio.movimientosDelMes(periodo);
        BigDecimal ingresos = BigDecimal.ZERO, egresos = BigDecimal.ZERO;
        for (MovimientoTienda m : lista) {
            if (m.isIngreso()) ingresos = ingresos.add(m.getValor());
            else egresos = egresos.add(m.getValor());
        }
        model.addAttribute("movimientos", lista);
        model.addAttribute("ingresos", ingresos);
        model.addAttribute("egresos", egresos);

        MovimientoTienda enEdicion = editar == null ? null : movimientoRepository.findById(editar).orElse(null);
        model.addAttribute("enEdicion", enEdicion);
        model.addAttribute("cuentasActivas", cuentaRepository.findByActivaTrueOrderByOrdenAscNombreAsc());
        model.addAttribute("hayCuentas", cuentaRepository.count() > 0);
        model.addAttribute("categoriasIngreso", MovimientoTienda.CATEGORIAS_INGRESO_A_MANO);
        model.addAttribute("categoriasEgreso", MovimientoTienda.CATEGORIAS_EGRESO);
        model.addAttribute("hoy", ContabilidadTiendaServicio.hoy().toString());
        return "tienda-admin/contabilidad_movimientos";
    }

    @PostMapping("/movimiento/guardar")
    public String guardarMovimiento(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        Integer id = entero(f.get("id"));
        try {
            BigDecimal valor = ContabilidadTiendaServicio.pesos(f.get("valor"));
            if (valor != null) valor = valor.abs();
            MovimientoTienda m = servicio.guardarMovimiento(id, f.get("tipo"), fecha(f.get("fecha")), f.get("categoria"),
                    entero(f.get("cuentaId")), valor, f.get("descripcion"), usuarioActual());
            ra.addFlashAttribute("mensaje", (m.isIngreso() ? "Ingreso" : "Gasto") + " de $" + miles(m.getValor())
                    + (id == null ? " anotado" : " corregido") + " (" + m.getCategoria() + ").");
            ra.addAttribute("mes", YearMonth.from(m.getFecha()).toString());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("borrador", f);
            if (id != null) ra.addAttribute("editar", id);
            if (f.get("mes") != null && !f.get("mes").isBlank()) ra.addAttribute("mes", mes(f.get("mes")).toString());
        }
        return "redirect:/tienda-admin/contabilidad/movimientos";
    }

    @PostMapping("/movimiento/{id}/eliminar")
    public String eliminarMovimiento(@PathVariable int id, @RequestParam(name = "mes", required = false) String mes,
                                     RedirectAttributes ra) {
        try {
            MovimientoTienda m = servicio.eliminarMovimiento(id);
            ra.addFlashAttribute("mensaje", (m.isIngreso() ? "Ingreso" : "Gasto") + " de $" + miles(m.getValor()) + " borrado."
                    + (m.getOrdenId() != null ? " La venta volvió a \"Ventas sin anotar\" en el resumen." : ""));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (mes != null && !mes.isBlank()) ra.addAttribute("mes", mes(mes).toString());
        return "redirect:/tienda-admin/contabilidad/movimientos";
    }

    // ═══════════════════════════════════════════════════════════════
    // CUENTAS
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/cuentas")
    public String cuentas(Model model) {
        List<ContabilidadTiendaServicio.FilaCuenta> filas = servicio.cuentasConSaldo(true);
        model.addAttribute("filas", filas);
        model.addAttribute("total", filas.stream().filter(f -> f.getCuenta().isActiva())
                .map(ContabilidadTiendaServicio.FilaCuenta::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("cuentasActivas", cuentaRepository.findByActivaTrueOrderByOrdenAscNombreAsc());
        model.addAttribute("cuentaWompi", cuentaRepository.findFirstByRecibeWompiTrue().map(CuentaTienda::getId).orElse(null));
        model.addAttribute("cuentaAddi", cuentaRepository.findFirstByRecibeAddiTrue().map(CuentaTienda::getId).orElse(null));
        return "tienda-admin/contabilidad_cuentas";
    }

    @PostMapping("/cuentas/guardar")
    public String guardarCuenta(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        try {
            CuentaTienda c = servicio.guardarCuenta(entero(f.get("id")), f.get("nombre"),
                    ContabilidadTiendaServicio.pesos(f.get("saldoInicial")));
            ra.addFlashAttribute("mensaje", "Cuenta " + c.getNombre() + " guardada.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tienda-admin/contabilidad/cuentas";
    }

    @PostMapping("/cuentas/{id}/activar")
    public String activarCuenta(@PathVariable int id, RedirectAttributes ra) {
        try {
            CuentaTienda c = servicio.activarODesactivar(id);
            ra.addFlashAttribute("mensaje", "Cuenta " + c.getNombre() + (c.isActiva() ? " activada." : " desactivada."));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tienda-admin/contabilidad/cuentas";
    }

    /** A qué cuenta entra sola la plata de Wompi y a cuál la de Addi. */
    @PostMapping("/cuentas/pagos")
    public String cuentasDePago(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        try {
            servicio.elegirCuentasDePago(entero(f.get("cuentaWompi")), entero(f.get("cuentaAddi")));
            ra.addFlashAttribute("mensaje", "Listo. Desde ahora cada compra pagada se anota sola en la cuenta elegida."
                    + " Las que no tengan cuenta salen en el resumen para anotarlas a mano.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tienda-admin/contabilidad/cuentas";
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    /** Datos para el selector de mes (anterior, siguiente y el nombre del mes). */
    private static void datosDelMes(YearMonth periodo, Model model) {
        YearMonth actual = YearMonth.from(ContabilidadTiendaServicio.hoy());
        String nombre = periodo.getMonth().getDisplayName(TextStyle.FULL, CO);
        model.addAttribute("mes", periodo.toString());
        model.addAttribute("nombreMes", nombre.substring(0, 1).toUpperCase(CO) + nombre.substring(1) + " " + periodo.getYear());
        model.addAttribute("mesAnterior", periodo.minusMonths(1).toString());
        model.addAttribute("mesSiguiente", periodo.isBefore(actual) ? periodo.plusMonths(1).toString() : null);
        model.addAttribute("esMesActual", periodo.equals(actual));
    }

    /** "2026-10" → octubre 2026. Vacío, mal escrito o futuro → el mes actual. */
    static YearMonth mes(String texto) {
        YearMonth actual = YearMonth.from(ContabilidadTiendaServicio.hoy());
        if (texto == null || texto.isBlank()) return actual;
        try {
            YearMonth m = YearMonth.parse(texto.trim());
            return m.isAfter(actual) ? actual : m;
        } catch (Exception e) {
            return actual;
        }
    }

    private static Integer entero(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate fecha(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); } catch (Exception e) { throw new IllegalArgumentException("Revisa la fecha."); }
    }

    private static String miles(BigDecimal v) {
        return String.format(CO, "%,d", v == null ? 0L : v.longValue()).replace(',', '.');
    }

    private static String usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null ? a.getName() : "";
    }
}