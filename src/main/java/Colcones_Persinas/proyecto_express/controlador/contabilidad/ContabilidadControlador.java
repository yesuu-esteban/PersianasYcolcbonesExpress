package Colcones_Persinas.proyecto_express.controlador.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.*;
import Colcones_Persinas.proyecto_express.repository.almacen.PedidoTiendaPorCobrarRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.*;
import Colcones_Persinas.proyecto_express.servicio.contabilidad.ContabilidadServicio;
import Colcones_Persinas.proyecto_express.servicio.contabilidad.VentasAlmacenServicio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Contabilidad (por ahora de Almacén; cada movimiento dice si es de Almacén o de Fábrica).
 * Solo para administradores (ADMIN) y el administrador de Almacén (TIENDA_ADMIN).
 *
 *   /contabilidad              resumen: saldos de las cuentas y resultado del mes
 *   /contabilidad/ventas       pedidos de Almacén del mes (vendido, costo, utilidad, abonos)
 *   /contabilidad/movimientos  ingresos, egresos y traslados (registrar, editar, eliminar)
 *   /contabilidad/por-cobrar   pedidos de Almacén con saldo pendiente
 *   /contabilidad/por-pagar    deudas por pagar
 *   /contabilidad/cuentas      bancos, billeteras y cajas, con su saldo inicial
 *   /contabilidad/categorias   plan de cuentas (categorías y subcategorías)
 *
 * Si algo falla, se muestra una página con el error (contabilidad/error) en vez de mandar al login.
 */
@Controller
@RequestMapping("/contabilidad")
@PreAuthorize("hasAnyRole('ADMIN','TIENDA_ADMIN')")
public class ContabilidadControlador {

    private static final Logger log = LoggerFactory.getLogger(ContabilidadControlador.class);
    private static final int POR_PAGINA = 20;
    private static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
            "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

    private final ContabilidadServicio servicio;
    private final CuentaContableRepository cuentaRepository;
    private final CategoriaContableRepository categoriaRepository;
    private final SubcategoriaContableRepository subcategoriaRepository;
    private final MovimientoContableRepository movimientoRepository;
    private final CuentaPorPagarRepository porPagarRepository;
    private final PedidoTiendaPorCobrarRepository porCobrarRepository;
    private final VentasAlmacenServicio ventasServicio;

    public ContabilidadControlador(ContabilidadServicio servicio, CuentaContableRepository cuentaRepository,
                                   CategoriaContableRepository categoriaRepository,
                                   SubcategoriaContableRepository subcategoriaRepository,
                                   MovimientoContableRepository movimientoRepository,
                                   CuentaPorPagarRepository porPagarRepository,
                                   PedidoTiendaPorCobrarRepository porCobrarRepository,
                                   VentasAlmacenServicio ventasServicio) {
        this.servicio = servicio;
        this.cuentaRepository = cuentaRepository;
        this.categoriaRepository = categoriaRepository;
        this.subcategoriaRepository = subcategoriaRepository;
        this.movimientoRepository = movimientoRepository;
        this.porPagarRepository = porPagarRepository;
        this.porCobrarRepository = porCobrarRepository;
        this.ventasServicio = ventasServicio;
    }

    /** Una cuenta con su saldo actual, para las tablas. */
    public static class FilaCuenta {
        private final CuentaContable cuenta;
        private final BigDecimal saldo;
        FilaCuenta(CuentaContable cuenta, BigDecimal saldo) { this.cuenta = cuenta; this.saldo = saldo; }
        public CuentaContable getCuenta() { return cuenta; }
        public BigDecimal getSaldo() { return saldo; }
    }

    /** Un pedido de Almacén que debe plata, con los días que lleva. */
    public static class FilaCobro {
        private final PedidoTienda pedido;
        private final long dias;
        FilaCobro(PedidoTienda pedido, long dias) { this.pedido = pedido; this.dias = dias; }
        public PedidoTienda getPedido() { return pedido; }
        public long getDias() { return dias; }
    }

    // ═══════════════════════════════════════════════════════════════
    // RESUMEN
    // ═══════════════════════════════════════════════════════════════

    @GetMapping({"", "/"})
    public String resumen(@RequestParam(required = false) String mes,
                          @RequestParam(required = false) String area, Model model) {
        YearMonth periodo = leerMes(mes);
        String filtroArea = leerArea(area);

        List<FilaCuenta> cuentas = filasCuentas(false);
        BigDecimal totalCuentas = cuentas.stream().map(FilaCuenta::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal porCobrar = porCobrarRepository.findBySaldoGreaterThanOrderByFechaPedidoAsc(BigDecimal.ZERO).stream()
                .map(PedidoTienda::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal porPagar = porPagarRepository.findByPagadaFalseOrderByFechaVencimientoAscIdAsc().stream()
                .map(CuentaPorPagar::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("cuentas", cuentas);
        model.addAttribute("totalCuentas", totalCuentas);
        model.addAttribute("porCobrar", porCobrar);
        model.addAttribute("porPagar", porPagar);
        model.addAttribute("posicionNeta", totalCuentas.add(porCobrar).subtract(porPagar));
        model.addAttribute("resultado", servicio.resultado(periodo.atDay(1), periodo.atEndOfMonth(), filtroArea));
        // Los pedidos de Almacén del mes (no aplica cuando se mira solo Fábrica)
        boolean conVentas = filtroArea == null || MovimientoContable.ALMACEN.equals(filtroArea);
        model.addAttribute("ventas", conVentas ? ventasServicio.ventas(periodo.atDay(1), periodo.atEndOfMonth()) : null);
        ponerPeriodo(model, periodo, filtroArea);
        return "contabilidad/resumen";
    }

    // ═══════════════════════════════════════════════════════════════
    // VENTAS (pedidos de Almacén del mes)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/ventas")
    public String ventas(@RequestParam(required = false) String mes, Model model) {
        YearMonth periodo = leerMes(mes);
        model.addAttribute("ventas", ventasServicio.ventas(periodo.atDay(1), periodo.atEndOfMonth()));
        model.addAttribute("todasLasCuentas", cuentaRepository.findAllByOrderByOrdenAscNombreAsc());
        model.addAttribute("hoy", ContabilidadServicio.hoy().toString());
        ponerPeriodo(model, periodo, MovimientoContable.ALMACEN);
        return "contabilidad/ventas";
    }

    /** Anota en contabilidad los abonos de un pedido que todavía no estaban (pedidos de antes de la contabilidad). */
    @PostMapping("/ventas/{id}/anotar")
    public String anotarAbonoPedido(@PathVariable int id, @RequestParam Map<String, String> f, RedirectAttributes ra) {
        try {
            MovimientoContable m = ventasServicio.anotarAbonosFaltantes(id, entero(f.get("cuentaId")), fecha(f.get("fecha")), usuarioActual());
            ra.addFlashAttribute("mensaje", "Pedido #" + id + ": se anotó un ingreso de $" + miles(m.getValor())
                    + " en " + m.getCuenta().getNombre() + " con fecha " + m.getFechaFormateada() + ".");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addAttribute("mes", leerMes(f.get("mes")).toString());
        return "redirect:/contabilidad/ventas";
    }

    // ═══════════════════════════════════════════════════════════════
    // MOVIMIENTOS
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/movimientos")
    public String movimientos(@RequestParam(required = false) String mes,
                              @RequestParam(required = false) String area,
                              @RequestParam(required = false) String tipo,
                              @RequestParam(required = false) Integer cuentaId,
                              @RequestParam(required = false) Integer categoriaId,
                              @RequestParam(required = false, defaultValue = "0") int pagina,
                              Model model) {
        YearMonth periodo = leerMes(mes);
        String filtroArea = leerArea(area);
        String filtroTipo = List.of(MovimientoContable.INGRESO, MovimientoContable.EGRESO, MovimientoContable.TRASLADO)
                .contains(tipo) ? tipo : null;

        List<MovimientoContable> filtrados = new ArrayList<>();
        BigDecimal ingresos = BigDecimal.ZERO, egresos = BigDecimal.ZERO, traslados = BigDecimal.ZERO;
        for (MovimientoContable m : movimientoRepository.findByFechaBetweenOrderByFechaDescIdDesc(periodo.atDay(1), periodo.atEndOfMonth())) {
            if (filtroArea != null && !filtroArea.equals(m.getArea())) continue;
            if (filtroTipo != null && !filtroTipo.equals(m.getTipo())) continue;
            if (cuentaId != null && m.getCuenta().getId() != cuentaId
                    && (m.getCuentaDestino() == null || m.getCuentaDestino().getId() != cuentaId)) continue;
            if (categoriaId != null && (m.getCategoria() == null || m.getCategoria().getId() != categoriaId)) continue;
            filtrados.add(m);
            switch (m.getTipo()) {
                case MovimientoContable.INGRESO -> ingresos = ingresos.add(m.getValor());
                case MovimientoContable.EGRESO -> egresos = egresos.add(m.getValor());
                default -> traslados = traslados.add(m.getValor());
            }
        }

        int totalPaginas = Math.max(1, (filtrados.size() + POR_PAGINA - 1) / POR_PAGINA);
        int actual = Math.max(0, Math.min(pagina, totalPaginas - 1));
        int desde = actual * POR_PAGINA;

        model.addAttribute("movimientos", filtrados.subList(desde, Math.min(desde + POR_PAGINA, filtrados.size())));
        model.addAttribute("totalFiltrados", filtrados.size());
        model.addAttribute("paginaActual", actual);
        model.addAttribute("totalPaginas", totalPaginas);
        model.addAttribute("sumaIngresos", ingresos);
        model.addAttribute("sumaEgresos", egresos);
        model.addAttribute("sumaTraslados", traslados);
        model.addAttribute("tipo", filtroTipo == null ? "" : filtroTipo);
        model.addAttribute("cuentaId", cuentaId);
        model.addAttribute("categoriaId", categoriaId);
        model.addAttribute("todasLasCuentas", cuentaRepository.findAllByOrderByOrdenAscNombreAsc());
        model.addAttribute("categorias", categoriaRepository.findAllByOrderByOrdenAscNombreAsc());
        ponerPeriodo(model, periodo, filtroArea);
        return "contabilidad/movimientos";
    }

    @GetMapping("/movimiento/nuevo")
    public String nuevoMovimiento(@RequestParam(required = false) String tipo, Model model) {
        MovimientoContable m = new MovimientoContable();
        m.setTipo(List.of(MovimientoContable.INGRESO, MovimientoContable.TRASLADO).contains(tipo) ? tipo : MovimientoContable.EGRESO);
        m.setFecha(ContabilidadServicio.hoy());
        return formularioMovimiento(m, model);
    }

    @GetMapping("/movimiento/{id}/editar")
    public String editarMovimiento(@PathVariable int id, Model model, RedirectAttributes ra) {
        MovimientoContable m = movimientoRepository.findById(id).orElse(null);
        if (m == null) {
            ra.addFlashAttribute("error", "Ese movimiento ya no existe.");
            return "redirect:/contabilidad/movimientos";
        }
        return formularioMovimiento(m, model);
    }

    @PostMapping("/movimiento/guardar")
    public String guardarMovimiento(@RequestParam Map<String, String> f, Model model, RedirectAttributes ra) {
        Integer id = entero(f.get("id"));
        LocalDate fecha = fecha(f.get("fecha"));
        try {
            MovimientoContable m = servicio.guardarMovimiento(new ContabilidadServicio.DatosMovimiento(
                    id, f.get("tipo"), fecha, f.get("area"), entero(f.get("cuentaId")), entero(f.get("cuentaDestinoId")),
                    entero(f.get("subcategoriaId")), ContabilidadServicio.pesos(f.get("valor")),
                    f.get("tercero"), f.get("descripcion")), usuarioActual());
            ra.addFlashAttribute("mensaje", m.getTipoEtiqueta() + " de $" + miles(m.getValor()) + " guardado.");
            ra.addAttribute("mes", YearMonth.from(m.getFecha()).toString());
            ra.addAttribute("area", m.getArea());
            return "redirect:/contabilidad/movimientos";
        } catch (IllegalArgumentException e) {
            // Se vuelve a mostrar el formulario con lo que se escribió
            MovimientoContable m = id != null ? movimientoRepository.findById(id).orElse(new MovimientoContable()) : new MovimientoContable();
            m.setTipo(f.getOrDefault("tipo", MovimientoContable.EGRESO));
            m.setFecha(fecha != null ? fecha : ContabilidadServicio.hoy());
            m.setArea(MovimientoContable.FABRICA.equals(f.get("area")) ? MovimientoContable.FABRICA : MovimientoContable.ALMACEN);
            m.setCuenta(buscarCuenta(f.get("cuentaId")));
            m.setCuentaDestino(buscarCuenta(f.get("cuentaDestinoId")));
            Integer subId = entero(f.get("subcategoriaId"));
            m.setSubcategoria(subId != null ? subcategoriaRepository.findById(subId).orElse(null) : null);
            m.setTercero(f.getOrDefault("tercero", ""));
            m.setDescripcion(f.getOrDefault("descripcion", ""));
            model.addAttribute("valorEscrito", f.getOrDefault("valor", ""));
            model.addAttribute("error", e.getMessage());
            return formularioMovimiento(m, model);
        }
    }

    @PostMapping("/movimiento/{id}/eliminar")
    @Transactional
    public String eliminarMovimiento(@PathVariable int id, RedirectAttributes ra) {
        MovimientoContable m = movimientoRepository.findById(id).orElse(null);
        if (m == null) {
            ra.addFlashAttribute("error", "Ese movimiento ya no existe.");
            return "redirect:/contabilidad/movimientos";
        }
        // Si era el pago de una cuenta por pagar, esa deuda vuelve a quedar pendiente
        for (CuentaPorPagar c : porPagarRepository.findByMovimientoId(id)) {
            c.setPagada(false);
            c.setFechaPago(null);
            c.setMovimientoId(null);
            porPagarRepository.save(c);
        }
        movimientoRepository.delete(m);
        ra.addFlashAttribute("mensaje", m.getTipoEtiqueta() + " de $" + miles(m.getValor()) + " eliminado.");
        ra.addAttribute("mes", YearMonth.from(m.getFecha()).toString());
        ra.addAttribute("area", m.getArea());
        return "redirect:/contabilidad/movimientos";
    }

    private String formularioMovimiento(MovimientoContable m, Model model) {
        model.addAttribute("mov", m);
        model.addAttribute("todasLasCuentas", cuentaRepository.findAllByOrderByOrdenAscNombreAsc());
        model.addAttribute("categorias", categoriaRepository.findAllByOrderByOrdenAscNombreAsc());
        if (!model.containsAttribute("valorEscrito")) {
            model.addAttribute("valorEscrito", m.getValor() != null && m.getValor().signum() > 0 ? miles(m.getValor()) : "");
        }
        return "contabilidad/movimiento_form";
    }

    // ═══════════════════════════════════════════════════════════════
    // CUENTAS POR COBRAR (pedidos de Almacén con saldo)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/por-cobrar")
    public String porCobrar(Model model) {
        LocalDate hoy = ContabilidadServicio.hoy();
        List<FilaCobro> filas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PedidoTienda p : porCobrarRepository.findBySaldoGreaterThanOrderByFechaPedidoAsc(BigDecimal.ZERO)) {
            long dias = p.getFechaPedido() != null ? Math.max(0, ChronoUnit.DAYS.between(p.getFechaPedido().toLocalDate(), hoy)) : 0;
            filas.add(new FilaCobro(p, dias));
            total = total.add(p.getSaldo());
        }
        model.addAttribute("filas", filas);
        model.addAttribute("total", total);
        return "contabilidad/por_cobrar";
    }

    // ═══════════════════════════════════════════════════════════════
    // CUENTAS POR PAGAR
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/por-pagar")
    public String porPagar(Model model) {
        List<CuentaPorPagar> pendientes = porPagarRepository.findByPagadaFalseOrderByFechaVencimientoAscIdAsc();
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("pagadas", porPagarRepository.findTop30ByPagadaTrueOrderByFechaPagoDescIdDesc());
        model.addAttribute("totalPendiente", pendientes.stream().map(CuentaPorPagar::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("todasLasCuentas", cuentaRepository.findAllByOrderByOrdenAscNombreAsc());
        model.addAttribute("categorias", categoriaRepository.findAllByOrderByOrdenAscNombreAsc());
        model.addAttribute("hoy", ContabilidadServicio.hoy().toString());
        return "contabilidad/por_pagar";
    }

    @PostMapping("/por-pagar/guardar")
    public String guardarPorPagar(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        try {
            CuentaPorPagar c = servicio.registrarPorPagar(f.get("area"), f.get("acreedor"), f.get("concepto"),
                    ContabilidadServicio.pesos(f.get("valor")), fecha(f.get("vence")));
            ra.addFlashAttribute("mensaje", "Se anotó la deuda con " + c.getAcreedor() + " por $" + miles(c.getValor()) + ".");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/contabilidad/por-pagar";
    }

    @PostMapping("/por-pagar/{id}/pagar")
    public String pagarPorPagar(@PathVariable int id, @RequestParam Map<String, String> f, RedirectAttributes ra) {
        try {
            MovimientoContable m = servicio.pagarPorPagar(id, entero(f.get("cuentaId")), entero(f.get("subcategoriaId")),
                    fecha(f.get("fecha")), usuarioActual());
            ra.addFlashAttribute("mensaje", "Pago registrado: egreso de $" + miles(m.getValor()) + " desde " + m.getCuenta().getNombre() + ".");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/contabilidad/por-pagar";
    }

    @PostMapping("/por-pagar/{id}/eliminar")
    public String eliminarPorPagar(@PathVariable int id, RedirectAttributes ra) {
        porPagarRepository.findById(id).ifPresent(c -> {
            if (c.isPagada()) {
                ra.addFlashAttribute("error", "Esa deuda ya está pagada. Para deshacerla, elimina su egreso en Movimientos.");
            } else {
                porPagarRepository.delete(c);
                ra.addFlashAttribute("mensaje", "Se eliminó la deuda con " + c.getAcreedor() + ".");
            }
        });
        return "redirect:/contabilidad/por-pagar";
    }

    // ═══════════════════════════════════════════════════════════════
    // CUENTAS (bancos, billeteras, cajas)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/cuentas")
    public String cuentas(Model model) {
        List<FilaCuenta> filas = filasCuentas(true);
        model.addAttribute("filas", filas);
        model.addAttribute("total", filas.stream().map(FilaCuenta::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "contabilidad/cuentas";
    }

    @PostMapping("/cuentas/guardar")
    public String guardarCuenta(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        Integer id = entero(f.get("id"));
        String nombre = f.getOrDefault("nombre", "").trim().toUpperCase(new Locale("es", "CO"));
        BigDecimal saldoInicial;
        try {
            saldoInicial = ContabilidadServicio.pesos(f.get("saldoInicial"));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/contabilidad/cuentas";
        }
        if (saldoInicial == null) saldoInicial = BigDecimal.ZERO;
        if (f.getOrDefault("saldoInicial", "").trim().startsWith("-")) saldoInicial = saldoInicial.negate();

        if (nombre.isEmpty()) {
            ra.addFlashAttribute("error", "Escribe el nombre de la cuenta.");
        } else if (nombre.length() > 80) {
            ra.addFlashAttribute("error", "El nombre es demasiado largo.");
        } else if (id == null ? cuentaRepository.existsByNombreIgnoreCase(nombre) : cuentaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            ra.addFlashAttribute("error", "Ya existe una cuenta llamada " + nombre + ".");
        } else {
            CuentaContable c = id == null ? new CuentaContable() : cuentaRepository.findById(id).orElse(null);
            if (c == null) {
                ra.addFlashAttribute("error", "Esa cuenta ya no existe.");
            } else {
                if (id == null) c.setOrden(cuentaRepository.findAllByOrderByOrdenAscNombreAsc().stream()
                        .mapToInt(CuentaContable::getOrden).max().orElse(0) + 1);
                c.setNombre(nombre);
                c.setSaldoInicial(saldoInicial);
                cuentaRepository.save(c);
                ra.addFlashAttribute("mensaje", "Cuenta " + nombre + " guardada.");
            }
        }
        return "redirect:/contabilidad/cuentas";
    }

    @PostMapping("/cuentas/{id}/activar")
    public String activarCuenta(@PathVariable int id, RedirectAttributes ra) {
        cuentaRepository.findById(id).ifPresent(c -> {
            c.setActiva(!c.isActiva());
            cuentaRepository.save(c);
            ra.addFlashAttribute("mensaje", c.getNombre() + (c.isActiva() ? " vuelve a aparecer para registrar movimientos." : " ya no aparece para registrar movimientos (su historial se conserva)."));
        });
        return "redirect:/contabilidad/cuentas";
    }

    // ═══════════════════════════════════════════════════════════════
    // CATEGORÍAS (plan de cuentas)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/categorias")
    public String categorias(Model model) {
        model.addAttribute("categorias", categoriaRepository.findAllByOrderByOrdenAscNombreAsc());
        Map<String, String> clases = new LinkedHashMap<>();
        for (String clase : List.of(CategoriaContable.INGRESO, CategoriaContable.COSTO, CategoriaContable.GASTO, CategoriaContable.OTRO)) {
            clases.put(clase, CategoriaContable.etiquetaClase(clase));
        }
        model.addAttribute("clases", clases);
        return "contabilidad/categorias";
    }

    @PostMapping("/categorias/guardar")
    public String guardarCategoria(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        Integer id = entero(f.get("id"));
        String nombre = f.getOrDefault("nombre", "").trim().toUpperCase(new Locale("es", "CO"));
        String clase = f.getOrDefault("clase", "");
        if (nombre.isEmpty() || nombre.length() > 120) {
            ra.addFlashAttribute("error", "Escribe el nombre de la categoría (máximo 120 letras).");
        } else if (!List.of(CategoriaContable.INGRESO, CategoriaContable.COSTO, CategoriaContable.GASTO, CategoriaContable.OTRO).contains(clase)) {
            ra.addFlashAttribute("error", "Elige cómo cuenta la categoría en el resultado.");
        } else if (id == null ? categoriaRepository.existsByNombreIgnoreCase(nombre) : categoriaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            ra.addFlashAttribute("error", "Ya existe una categoría llamada " + nombre + ".");
        } else {
            CategoriaContable c = id == null ? new CategoriaContable() : categoriaRepository.findById(id).orElse(null);
            if (c == null) {
                ra.addFlashAttribute("error", "Esa categoría ya no existe.");
            } else {
                if (id == null) c.setOrden(categoriaRepository.findAllByOrderByOrdenAscNombreAsc().stream()
                        .mapToInt(CategoriaContable::getOrden).max().orElse(0) + 1);
                c.setNombre(nombre);
                c.setClase(clase);
                categoriaRepository.save(c);
                ra.addFlashAttribute("mensaje", "Categoría " + nombre + " guardada.");
            }
        }
        return "redirect:/contabilidad/categorias";
    }

    @PostMapping("/categorias/{id}/activar")
    public String activarCategoria(@PathVariable int id, RedirectAttributes ra) {
        categoriaRepository.findById(id).ifPresent(c -> {
            c.setActiva(!c.isActiva());
            categoriaRepository.save(c);
            ra.addFlashAttribute("mensaje", c.getNombre() + (c.isActiva() ? " vuelve a aparecer." : " ya no aparece para registrar (su historial se conserva)."));
        });
        return "redirect:/contabilidad/categorias";
    }

    @PostMapping("/categorias/subcategoria/guardar")
    public String guardarSubcategoria(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        Integer id = entero(f.get("id"));
        Integer categoriaId = entero(f.get("categoriaId"));
        String nombre = f.getOrDefault("nombre", "").trim().toUpperCase(new Locale("es", "CO"));
        SubcategoriaContable s = id != null ? subcategoriaRepository.findById(id).orElse(null) : null;
        CategoriaContable c = s != null ? s.getCategoria()
                : (categoriaId != null ? categoriaRepository.findById(categoriaId).orElse(null) : null);

        if (c == null || (id != null && s == null)) {
            ra.addFlashAttribute("error", "Esa categoría ya no existe.");
        } else if (nombre.isEmpty() || nombre.length() > 120) {
            ra.addFlashAttribute("error", "Escribe el nombre de la subcategoría (máximo 120 letras).");
        } else if (s == null ? subcategoriaRepository.existsByCategoriaAndNombreIgnoreCase(c, nombre)
                : subcategoriaRepository.existsByCategoriaAndNombreIgnoreCaseAndIdNot(c, nombre, s.getId())) {
            ra.addFlashAttribute("error", "En " + c.getNombre() + " ya existe " + nombre + ".");
        } else {
            if (s == null) {
                s = new SubcategoriaContable(c, nombre, (int) subcategoriaRepository.countByCategoria(c) + 1);
            } else {
                s.setNombre(nombre);
            }
            subcategoriaRepository.save(s);
            ra.addFlashAttribute("mensaje", nombre + " guardada en " + c.getNombre() + ".");
        }
        return "redirect:/contabilidad/categorias";
    }

    @PostMapping("/categorias/subcategoria/{id}/activar")
    public String activarSubcategoria(@PathVariable int id, RedirectAttributes ra) {
        subcategoriaRepository.findById(id).ifPresent(s -> {
            s.setActiva(!s.isActiva());
            subcategoriaRepository.save(s);
            ra.addFlashAttribute("mensaje", s.getNombre() + (s.isActiva() ? " vuelve a aparecer." : " ya no aparece para registrar (su historial se conserva)."));
        });
        return "redirect:/contabilidad/categorias";
    }

    // ═══════════════════════════════════════════════════════════════
    // SI ALGO FALLA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Cualquier error en estas pantallas muestra una página con el detalle (y queda en el log),
     * en vez de mandar al login. Los permisos (AccessDeniedException) siguen su camino normal.
     */
    @ExceptionHandler(Exception.class)
    public String errorInesperado(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) throws Exception {
        if (ex instanceof AccessDeniedException) throw ex;
        log.error("[Contabilidad] Error en {}", request.getRequestURI(), ex);
        String detalle = ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
        Throwable causa = ex.getCause();
        while (causa != null && causa.getCause() != null && causa.getCause() != causa) causa = causa.getCause();
        if (causa != null && causa != ex) {
            detalle += " | Causa: " + causa.getClass().getSimpleName() + (causa.getMessage() != null ? ": " + causa.getMessage() : "");
        }
        response.setStatus(500);
        model.addAttribute("ruta", request.getRequestURI());
        model.addAttribute("detalle", detalle.length() > 900 ? detalle.substring(0, 900) + "…" : detalle);
        return "contabilidad/error";
    }

    // ═══════════════════════════════════════════════════════════════
    // AYUDAS
    // ═══════════════════════════════════════════════════════════════

    /** Cuentas con su saldo actual. Sin "todas", se omiten las inactivas que quedaron en 0. */
    private List<FilaCuenta> filasCuentas(boolean todas) {
        Map<Integer, BigDecimal> saldos = servicio.saldosPorCuenta();
        List<FilaCuenta> filas = new ArrayList<>();
        for (CuentaContable c : cuentaRepository.findAllByOrderByOrdenAscNombreAsc()) {
            BigDecimal saldo = saldos.getOrDefault(c.getId(), BigDecimal.ZERO);
            if (todas || c.isActiva() || saldo.signum() != 0) filas.add(new FilaCuenta(c, saldo));
        }
        return filas;
    }

    private void ponerPeriodo(Model model, YearMonth periodo, String area) {
        model.addAttribute("mes", periodo.toString());
        model.addAttribute("mesTexto", MESES[periodo.getMonthValue() - 1] + " " + periodo.getYear());
        model.addAttribute("mesAnterior", periodo.minusMonths(1).toString());
        model.addAttribute("mesSiguiente", periodo.plusMonths(1).toString());
        model.addAttribute("esMesActual", periodo.equals(YearMonth.from(ContabilidadServicio.hoy())));
        model.addAttribute("area", area == null ? "TODAS" : area);
        model.addAttribute("areaTexto", area == null ? "Almacén y Fábrica" : MovimientoContable.etiquetaArea(area));
    }

    /** "2026-10" → octubre de 2026. Vacío o mal escrito → el mes actual. */
    private static YearMonth leerMes(String mes) {
        try {
            return mes == null || mes.isBlank() ? YearMonth.from(ContabilidadServicio.hoy()) : YearMonth.parse(mes.trim());
        } catch (Exception e) {
            return YearMonth.from(ContabilidadServicio.hoy());
        }
    }

    /** Por defecto se ve Almacén. "TODAS" = Almacén y Fábrica juntos (null). */
    private static String leerArea(String area) {
        if ("TODAS".equals(area)) return null;
        return MovimientoContable.FABRICA.equals(area) ? MovimientoContable.FABRICA : MovimientoContable.ALMACEN;
    }

    private CuentaContable buscarCuenta(String id) {
        Integer n = entero(id);
        return n != null ? cuentaRepository.findById(n).orElse(null) : null;
    }

    private static Integer entero(String s) {
        try { return s == null || s.isBlank() ? null : Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate fecha(String s) {
        try { return s == null || s.isBlank() ? null : LocalDate.parse(s.trim()); } catch (Exception e) { return null; }
    }

    private static String miles(BigDecimal v) {
        return java.text.NumberFormat.getIntegerInstance(new Locale("es", "CO")).format(v == null ? BigDecimal.ZERO : v);
    }

    private static String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }
}