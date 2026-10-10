package Colcones_Persinas.proyecto_express.servicio.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.CuentaTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.MovimientoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.CuentaTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.MovimientoTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Contabilidad PROPIA de la tienda virtual, aparte de la contabilidad de Almacén:
 * sus cuentas, sus ingresos y sus gastos.
 *
 *  - Cada compra pagada en línea se anota sola como ingreso ("Venta en línea") en la cuenta
 *    marcada para Wompi o para Addi. Si no hay cuenta marcada, queda en "Ventas sin anotar"
 *    para anotarla a mano.
 *  - Los gastos (fabricación, envíos, publicidad, comisiones...) se anotan a mano.
 */
@Service
public class ContabilidadTiendaServicio {

    private static final Logger log = LoggerFactory.getLogger(ContabilidadTiendaServicio.class);
    private static final Locale CO = new Locale("es", "CO");
    /** Cuántas ventas sin anotar se muestran en el resumen. */
    private static final int MAX_SIN_ANOTAR = 30;

    private final CuentaTiendaRepository cuentaRepository;
    private final MovimientoTiendaRepository movimientoRepository;
    private final OrdenTiendaRepository ordenRepository;
    private final TransactionTemplate transaccionAparte;

    public ContabilidadTiendaServicio(CuentaTiendaRepository cuentaRepository,
                                      MovimientoTiendaRepository movimientoRepository,
                                      OrdenTiendaRepository ordenRepository,
                                      PlatformTransactionManager transactionManager) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.ordenRepository = ordenRepository;
        this.transaccionAparte = new TransactionTemplate(transactionManager);
        this.transaccionAparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static LocalDate hoy() {
        return LocalDate.now(OrdenTienda.ZONA_COLOMBIA);
    }

    // ═══════════════════════════════════════════════════════════════
    // VENTAS EN LÍNEA → INGRESOS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Llamar cuando se aprueba el pago de una compra. El ingreso se anota cuando el pago
     * termine de guardarse, y en su propia transacción: si algo falla aquí, el pago no se afecta.
     */
    public void anotarVentaCuandoSeGuarde(int ordenId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    anotarVentaSola(ordenId);
                }
            });
        } else {
            anotarVentaSola(ordenId);
        }
    }

    private void anotarVentaSola(int ordenId) {
        try {
            transaccionAparte.executeWithoutResult(estado -> {
                OrdenTienda orden = ordenRepository.findById(ordenId).orElse(null);
                if (orden == null || !orden.isPagada() || orden.isCancelada() || orden.isAnotadaEnAlmacen()) return;
                if (movimientoRepository.existsByOrdenId(ordenId)) return;   // ya estaba anotada

                CuentaTienda cuenta = (esAddi(orden) ? cuentaRepository.findFirstByRecibeAddiTrue()
                        : cuentaRepository.findFirstByRecibeWompiTrue()).orElse(null);
                if (cuenta == null || !cuenta.isActiva()) {
                    log.info("[Tienda] Compra {}: no hay cuenta marcada para {}; queda en Ventas sin anotar.",
                            orden.getReferencia(), esAddi(orden) ? "Addi" : "Wompi");
                    return;
                }
                LocalDate fecha = orden.getFechaPago() != null ? orden.getFechaPago().toLocalDate() : hoy();
                movimientoRepository.save(movimientoDeVenta(orden, cuenta, fecha, "tienda virtual"));
            });
        } catch (Exception e) {
            log.error("[Tienda] No se pudo anotar la venta de la compra #{}. Se puede anotar a mano en "
                    + "Tienda virtual → Contabilidad.", ordenId, e);
        }
    }

    /** Anota a mano una venta que quedó sin anotar (botón "Anotar" del resumen). */
    @Transactional
    public MovimientoTienda anotarVenta(int ordenId, Integer cuentaId, LocalDate fecha, String usuario) {
        OrdenTienda orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("Esa compra ya no existe."));
        if (!orden.isPagada()) throw new IllegalArgumentException("Esa compra no está pagada.");
        if (orden.isAnotadaEnAlmacen()) {
            throw new IllegalArgumentException("La venta " + orden.getReferencia() + " ya está anotada en la contabilidad de Almacén.");
        }
        if (movimientoRepository.existsByOrdenId(ordenId)) {
            throw new IllegalArgumentException("La venta " + orden.getReferencia() + " ya está anotada.");
        }
        CuentaTienda cuenta = cuentaActiva(cuentaId);
        validarFecha(fecha);
        return movimientoRepository.save(movimientoDeVenta(orden, cuenta, fecha, usuario));
    }

    private MovimientoTienda movimientoDeVenta(OrdenTienda orden, CuentaTienda cuenta, LocalDate fecha, String usuario) {
        MovimientoTienda m = new MovimientoTienda();
        m.setFecha(fecha);
        m.setTipo(MovimientoTienda.INGRESO);
        m.setCategoria(MovimientoTienda.VENTA_EN_LINEA);
        String medio = esAddi(orden) ? "Addi"
                : "Wompi" + (vacio(orden.getMetodoPago()) ? "" : " - " + orden.getMetodoPago());
        m.setDescripcion(recortar("Pedido " + orden.getReferencia() + " · " + texto(orden.getNombreCliente())
                + " (" + medio + ")", 300));
        m.setValor(orden.getTotal());
        m.setCuenta(cuenta);
        m.setOrdenId(orden.getId());
        m.setUsuario(recortar(texto(usuario), 80));
        return m;
    }

    private static boolean esAddi(OrdenTienda orden) {
        return TiendaServicio.MEDIO_ADDI.equals(orden.getMetodoPago());
    }

    // ═══════════════════════════════════════════════════════════════
    // MOVIMIENTOS A MANO
    // ═══════════════════════════════════════════════════════════════

    /** Crea (id null) o corrige un ingreso o un gasto. */
    @Transactional
    public MovimientoTienda guardarMovimiento(Integer id, String tipo, LocalDate fecha, String categoria,
                                              Integer cuentaId, BigDecimal valor, String descripcion, String usuario) {
        MovimientoTienda m = id == null ? new MovimientoTienda()
                : movimientoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Ese movimiento ya no existe."));

        if (!MovimientoTienda.INGRESO.equals(tipo) && !MovimientoTienda.EGRESO.equals(tipo)) {
            throw new IllegalArgumentException("Elige si es un ingreso o un gasto.");
        }
        // Una venta anotada sola sigue siendo ingreso de "Venta en línea": solo se le corrige fecha, cuenta o valor.
        if (m.getOrdenId() != null) {
            tipo = MovimientoTienda.INGRESO;
            categoria = MovimientoTienda.VENTA_EN_LINEA;
        }
        List<String> validas = MovimientoTienda.INGRESO.equals(tipo)
                ? MovimientoTienda.CATEGORIAS_INGRESO : MovimientoTienda.CATEGORIAS_EGRESO;
        if (categoria == null || !validas.contains(categoria)) {
            throw new IllegalArgumentException("Elige la categoría.");
        }
        if (m.getOrdenId() == null && MovimientoTienda.VENTA_EN_LINEA.equals(categoria)) {
            throw new IllegalArgumentException("\"Venta en línea\" se anota sola con cada compra pagada. Para otra plata que entre, usa \"Otro ingreso\".");
        }
        validarFecha(fecha);
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("Escribe el valor.");
        CuentaTienda cuenta = cuentaActivaOLaMisma(cuentaId, m.getCuenta());

        m.setTipo(tipo);
        m.setFecha(fecha);
        m.setCategoria(categoria);
        m.setCuenta(cuenta);
        m.setValor(valor);
        m.setDescripcion(recortar(texto(descripcion).trim(), 300));
        if (id == null) m.setUsuario(recortar(texto(usuario), 80));
        return movimientoRepository.save(m);
    }

    /**
     * Borra un movimiento. Si era la venta de una compra, la compra vuelve a "Ventas sin anotar".
     * @return el movimiento borrado (para el mensaje)
     */
    @Transactional
    public MovimientoTienda eliminarMovimiento(int id) {
        MovimientoTienda m = movimientoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ese movimiento ya no existe."));
        movimientoRepository.delete(m);
        return m;
    }

    // ═══════════════════════════════════════════════════════════════
    // CUENTAS
    // ═══════════════════════════════════════════════════════════════

    /** Una cuenta con su saldo de hoy. */
    public static class FilaCuenta {
        private final CuentaTienda cuenta;
        private final BigDecimal saldo;
        FilaCuenta(CuentaTienda cuenta, BigDecimal saldo) { this.cuenta = cuenta; this.saldo = saldo; }
        public CuentaTienda getCuenta() { return cuenta; }
        public BigDecimal getSaldo() { return saldo; }
    }

    /** Las cuentas con su saldo (saldo inicial + ingresos − gastos). */
    @Transactional(readOnly = true)
    public List<FilaCuenta> cuentasConSaldo(boolean incluirInactivas) {
        Map<Integer, BigDecimal> movimientos = new HashMap<>();
        for (Object[] fila : movimientoRepository.totalesPorCuenta()) {
            int cuentaId = ((Number) fila[0]).intValue();
            BigDecimal suma = fila[2] == null ? BigDecimal.ZERO : new BigDecimal(fila[2].toString());
            if (!MovimientoTienda.INGRESO.equals(fila[1])) suma = suma.negate();
            movimientos.merge(cuentaId, suma, BigDecimal::add);
        }
        List<FilaCuenta> filas = new ArrayList<>();
        for (CuentaTienda c : cuentaRepository.findAllByOrderByOrdenAscNombreAsc()) {
            if (!incluirInactivas && !c.isActiva()) continue;
            BigDecimal saldo = nz(c.getSaldoInicial()).add(movimientos.getOrDefault(c.getId(), BigDecimal.ZERO));
            filas.add(new FilaCuenta(c, saldo));
        }
        return filas;
    }

    @Transactional
    public CuentaTienda guardarCuenta(Integer id, String nombre, BigDecimal saldoInicial) {
        String n = texto(nombre).trim().toUpperCase(CO);
        if (n.isEmpty()) throw new IllegalArgumentException("Escribe el nombre de la cuenta.");
        if (n.length() > 80) throw new IllegalArgumentException("El nombre es demasiado largo.");
        boolean repetida = id == null ? cuentaRepository.existsByNombreIgnoreCase(n)
                : cuentaRepository.existsByNombreIgnoreCaseAndIdNot(n, id);
        if (repetida) throw new IllegalArgumentException("Ya existe una cuenta llamada " + n + ".");

        CuentaTienda c;
        if (id == null) {
            c = new CuentaTienda();
            c.setOrden(cuentaRepository.findAllByOrderByOrdenAscNombreAsc().stream()
                    .mapToInt(CuentaTienda::getOrden).max().orElse(0) + 1);
        } else {
            c = cuentaRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no existe."));
        }
        c.setNombre(n);
        c.setSaldoInicial(saldoInicial == null ? BigDecimal.ZERO : saldoInicial);
        return cuentaRepository.save(c);
    }

    /** Activa o desactiva una cuenta. Una cuenta desactivada deja de recibir sola la plata de Wompi o Addi. */
    @Transactional
    public CuentaTienda activarODesactivar(int id) {
        CuentaTienda c = cuentaRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no existe."));
        c.setActiva(!c.isActiva());
        if (!c.isActiva()) {
            c.setRecibeWompi(null);
            c.setRecibeAddi(null);
        }
        return cuentaRepository.save(c);
    }

    /** Elige a qué cuenta entra sola la plata de Wompi y a cuál la de Addi (null = ninguna, se anota a mano). */
    @Transactional
    public void elegirCuentasDePago(Integer cuentaWompiId, Integer cuentaAddiId) {
        CuentaTienda wompi = cuentaWompiId == null ? null : cuentaActiva(cuentaWompiId);
        CuentaTienda addi = cuentaAddiId == null ? null : cuentaActiva(cuentaAddiId);
        for (CuentaTienda c : cuentaRepository.findAllByOrderByOrdenAscNombreAsc()) {
            boolean esWompi = wompi != null && c.getId() == wompi.getId();
            boolean esAddi = addi != null && c.getId() == addi.getId();
            if (c.isDeWompi() != esWompi || c.isDeAddi() != esAddi) {
                c.setRecibeWompi(esWompi ? Boolean.TRUE : null);
                c.setRecibeAddi(esAddi ? Boolean.TRUE : null);
                cuentaRepository.save(c);
            }
        }
    }

    private CuentaTienda cuentaActiva(Integer cuentaId) {
        CuentaTienda c = cuentaId == null ? null : cuentaRepository.findById(cuentaId).orElse(null);
        if (c == null || !c.isActiva()) throw new IllegalArgumentException("Elige una cuenta activa.");
        return c;
    }

    /** Al corregir un movimiento se acepta dejar su misma cuenta aunque ya esté desactivada. */
    private CuentaTienda cuentaActivaOLaMisma(Integer cuentaId, CuentaTienda actual) {
        if (actual != null && cuentaId != null && actual.getId() == cuentaId) return actual;
        return cuentaActiva(cuentaId);
    }

    // ═══════════════════════════════════════════════════════════════
    // RESUMEN DEL MES
    // ═══════════════════════════════════════════════════════════════

    /** Total de una categoría en el mes. */
    public static class FilaCategoria {
        private final String categoria;
        private final BigDecimal valor;
        private final int porcentaje;
        FilaCategoria(String categoria, BigDecimal valor, int porcentaje) {
            this.categoria = categoria; this.valor = valor; this.porcentaje = porcentaje;
        }
        public String getCategoria() { return categoria; }
        public BigDecimal getValor() { return valor; }
        /** Qué parte del total de su tipo es (0 a 100), para la barrita. */
        public int getPorcentaje() { return porcentaje; }
    }

    public static class Resumen {
        private BigDecimal vendido = BigDecimal.ZERO, canceladoValor = BigDecimal.ZERO,
                ingresos = BigDecimal.ZERO, egresos = BigDecimal.ZERO, totalCuentas = BigDecimal.ZERO;
        private int ventas, canceladas, cantidadMovimientos, ventasEnAlmacen;
        private long ventasSinAnotarTotal;
        private final List<FilaCategoria> ingresosPorCategoria = new ArrayList<>();
        private final List<FilaCategoria> egresosPorCategoria = new ArrayList<>();
        private final List<FilaCuenta> cuentas = new ArrayList<>();
        private final List<OrdenTienda> ventasSinAnotar = new ArrayList<>();

        /** Lo vendido en línea en el mes (compras pagadas, sin las canceladas). */
        public BigDecimal getVendido() { return vendido; }
        public int getVentas() { return ventas; }
        public int getCanceladas() { return canceladas; }
        public BigDecimal getCanceladoValor() { return canceladoValor; }
        /** Ventas del mes de antes de separar la tienda: su plata quedó en la contabilidad de Almacén. */
        public int getVentasEnAlmacen() { return ventasEnAlmacen; }
        public BigDecimal getIngresos() { return ingresos; }
        public BigDecimal getEgresos() { return egresos; }
        /** Ingresos − gastos del mes. */
        public BigDecimal getResultado() { return ingresos.subtract(egresos); }
        public int getCantidadMovimientos() { return cantidadMovimientos; }
        public List<FilaCategoria> getIngresosPorCategoria() { return ingresosPorCategoria; }
        public List<FilaCategoria> getEgresosPorCategoria() { return egresosPorCategoria; }
        public List<FilaCuenta> getCuentas() { return cuentas; }
        public BigDecimal getTotalCuentas() { return totalCuentas; }
        /** Compras pagadas (de cualquier mes) cuya plata no está anotada, las más nuevas primero. */
        public List<OrdenTienda> getVentasSinAnotar() { return ventasSinAnotar; }
        public long getVentasSinAnotarTotal() { return ventasSinAnotarTotal; }
    }

    @Transactional(readOnly = true)
    public Resumen resumen(YearMonth mes) {
        Resumen r = new Resumen();

        for (OrdenTienda o : ordenRepository.findByEstadoAndFechaPagoBetweenOrderByFechaPagoDesc(
                OrdenTienda.APROBADA, mes.atDay(1).atStartOfDay(), mes.atEndOfMonth().atTime(23, 59, 59))) {
            if (o.isCancelada()) {
                r.canceladas++;
                r.canceladoValor = r.canceladoValor.add(nz(o.getTotal()));
            } else {
                r.ventas++;
                r.vendido = r.vendido.add(nz(o.getTotal()));
                if (o.isAnotadaEnAlmacen()) r.ventasEnAlmacen++;
            }
        }

        Map<String, BigDecimal> ingresos = new LinkedHashMap<>(), egresos = new LinkedHashMap<>();
        List<MovimientoTienda> movimientos = movimientosDelMes(mes);
        r.cantidadMovimientos = movimientos.size();
        for (MovimientoTienda m : movimientos) {
            BigDecimal v = nz(m.getValor());
            if (m.isIngreso()) {
                r.ingresos = r.ingresos.add(v);
                ingresos.merge(m.getCategoria(), v, BigDecimal::add);
            } else {
                r.egresos = r.egresos.add(v);
                egresos.merge(m.getCategoria(), v, BigDecimal::add);
            }
        }
        r.ingresosPorCategoria.addAll(porCategoria(ingresos, r.ingresos));
        r.egresosPorCategoria.addAll(porCategoria(egresos, r.egresos));

        r.cuentas.addAll(cuentasConSaldo(false));
        for (FilaCuenta f : r.cuentas) r.totalCuentas = r.totalCuentas.add(f.getSaldo());

        r.ventasSinAnotar.addAll(ordenRepository.ventasSinAnotar(PageRequest.of(0, MAX_SIN_ANOTAR)));
        r.ventasSinAnotarTotal = ordenRepository.contarVentasSinAnotar();
        return r;
    }

    @Transactional(readOnly = true)
    public List<MovimientoTienda> movimientosDelMes(YearMonth mes) {
        return movimientoRepository.findByFechaBetweenOrderByFechaDescIdDesc(mes.atDay(1), mes.atEndOfMonth());
    }

    /** De mayor a menor, con el porcentaje de cada una sobre el total. */
    private static List<FilaCategoria> porCategoria(Map<String, BigDecimal> totales, BigDecimal total) {
        List<Map.Entry<String, BigDecimal>> lista = new ArrayList<>(totales.entrySet());
        lista.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        List<FilaCategoria> filas = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : lista) {
            int pct = total.signum() > 0
                    ? e.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 0, java.math.RoundingMode.HALF_UP).intValue()
                    : 0;
            filas.add(new FilaCategoria(e.getKey(), e.getValue(), pct));
        }
        return filas;
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static void validarFecha(LocalDate fecha) {
        if (fecha == null) throw new IllegalArgumentException("Escribe la fecha.");
        if (fecha.isAfter(hoy())) throw new IllegalArgumentException("La fecha no puede ser futura.");
    }

    /** "1.500.000", "$1,500,000" o "1500000" → 1500000. Con un menos adelante queda negativo. Vacío → null. */
    public static BigDecimal pesos(String s) {
        if (s == null) return null;
        String digitos = s.replaceAll("\\D", "");
        if (digitos.isEmpty()) return null;
        if (digitos.length() > 13) throw new IllegalArgumentException("El valor es demasiado grande.");
        BigDecimal v = new BigDecimal(digitos);
        return s.trim().startsWith("-") ? v.negate() : v;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static String texto(String s) { return s == null ? "" : s; }
    private static boolean vacio(String s) { return s == null || s.isBlank(); }
    private static String recortar(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }
}