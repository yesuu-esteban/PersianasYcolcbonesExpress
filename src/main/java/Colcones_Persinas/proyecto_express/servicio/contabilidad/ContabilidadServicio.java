package Colcones_Persinas.proyecto_express.servicio.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.*;
import Colcones_Persinas.proyecto_express.repository.almacen.PedidoTiendaPorCobrarRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Cuentas de la contabilidad:
 *  - Saldo de cada cuenta (dónde está la plata).
 *  - Resultado de un periodo: ingresos − costos − gastos.
 *  - Guardar movimientos, pagar cuentas por pagar (completas o por abonos) y anotar solos los abonos de Almacén.
 *  - Fecha de inicio: "iniciar la contabilidad en cero" desde un día. Lo anterior queda guardado como
 *    historial, pero ya no cuenta en saldos, resúmenes, deudas por pagar ni pedidos por cobrar. Se puede deshacer.
 *  - Ajustar el saldo de una cuenta al saldo real de hoy y eliminar cuentas sin movimientos.
 */
@Service
public class ContabilidadServicio {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    /** Subcategoría donde caen los abonos que se registran en Almacén. */
    public static final String SUBCATEGORIA_ABONOS = "VENTA DE PRODUCTOS";

    private final CuentaContableRepository cuentaRepository;
    private final SubcategoriaContableRepository subcategoriaRepository;
    private final CategoriaContableRepository categoriaRepository;
    private final MovimientoContableRepository movimientoRepository;
    private final CuentaPorPagarRepository porPagarRepository;
    private final ConfiguracionContableRepository configuracionRepository;
    private final PedidoTiendaPorCobrarRepository porCobrarRepository;

    public ContabilidadServicio(CuentaContableRepository cuentaRepository,
                                SubcategoriaContableRepository subcategoriaRepository,
                                CategoriaContableRepository categoriaRepository,
                                MovimientoContableRepository movimientoRepository,
                                CuentaPorPagarRepository porPagarRepository,
                                ConfiguracionContableRepository configuracionRepository,
                                PedidoTiendaPorCobrarRepository porCobrarRepository) {
        this.cuentaRepository = cuentaRepository;
        this.subcategoriaRepository = subcategoriaRepository;
        this.categoriaRepository = categoriaRepository;
        this.movimientoRepository = movimientoRepository;
        this.porPagarRepository = porPagarRepository;
        this.configuracionRepository = configuracionRepository;
        this.porCobrarRepository = porCobrarRepository;
    }

    public static LocalDate hoy() { return LocalDate.now(ZONA); }

    // ═══════════════════════════════════════════════════════════════
    // FECHA DE INICIO ("iniciar la contabilidad en cero")
    // ═══════════════════════════════════════════════════════════════

    /** Los ajustes guardados (null si nunca se guardaron). */
    @Transactional(readOnly = true)
    public ConfiguracionContable configuracion() {
        return configuracionRepository.findById(ConfiguracionContable.ID).orElse(null);
    }

    /** La fecha de inicio guardada, aunque todavía no haya llegado (null = no hay). */
    @Transactional(readOnly = true)
    public LocalDate fechaInicioGuardada() {
        ConfiguracionContable c = configuracion();
        return c != null ? c.getFechaInicio() : null;
    }

    /**
     * Desde qué día cuenta la contabilidad HOY (null = desde siempre).
     * Si el inicio en cero se programó para un día que todavía no llega (por ejemplo, hoy es 29 de octubre
     * y se programó para el 1 de noviembre), todavía no aplica: hasta ese día todo funciona normal
     * y ese día arranca en cero solo.
     */
    @Transactional(readOnly = true)
    public LocalDate fechaInicio() {
        LocalDate guardada = fechaInicioGuardada();
        return guardada != null && !guardada.isAfter(hoy()) ? guardada : null;
    }

    /** true = el inicio en cero está programado para un día que todavía no llega. */
    @Transactional(readOnly = true)
    public boolean inicioProgramado() {
        LocalDate guardada = fechaInicioGuardada();
        return guardada != null && guardada.isAfter(hoy());
    }

    /**
     * Con qué saldo arranca la cuenta HOY para calcular su saldo: mientras el inicio en cero está
     * programado se usa el saldo de antes (el de arranque se guarda para cuando llegue el día).
     */
    public BigDecimal saldoBase(CuentaContable c) {
        if (inicioProgramado() && c.getSaldoAntesInicio() != null) return nz(c.getSaldoAntesInicio());
        return nz(c.getSaldoInicial());
    }

    /**
     * Cambia el saldo inicial que se ve en Cuentas. Mientras el inicio en cero está programado,
     * se cambia el saldo de antes (el que cuenta hoy) y no el de arranque.
     */
    public void cambiarSaldoBase(CuentaContable c, BigDecimal valor) {
        if (inicioProgramado()) {
            if (c.getSaldoInicial() == null) c.setSaldoInicial(BigDecimal.ZERO);   // cuenta nueva: arranca en 0 ese día
            c.setSaldoAntesInicio(nz(valor));
        } else {
            c.setSaldoInicial(nz(valor));
        }
    }

    /** Si "desde" es anterior a la fecha de inicio, devuelve la fecha de inicio; si no, "desde". */
    public LocalDate desdeInicio(LocalDate desde) {
        LocalDate inicio = fechaInicio();
        if (inicio == null) return desde;
        return desde == null || desde.isBefore(inicio) ? inicio : desde;
    }

    /**
     * Desde qué momento cuentan las deudas por pagar y los pedidos por cobrar: lo que ya existía antes
     * no cuenta. Es lo primero entre el día de inicio y el momento en que se inició en cero
     * (así, si se inicia hoy con fecha futura, lo que se anote desde hoy sí aparece).
     * null = cuenta todo.
     */
    @Transactional(readOnly = true)
    public LocalDateTime corteDeudas() {
        ConfiguracionContable c = configuracion();
        if (c == null || c.getFechaInicio() == null || inicioProgramado()) return null;
        LocalDateTime inicio = c.getFechaInicio().atStartOfDay();
        LocalDateTime cambio = c.getFechaCambio();
        return cambio != null && cambio.isBefore(inicio) ? cambio : inicio;
    }

    /** ¿Algo creado en ese momento cuenta (no es anterior al corte)? Sin fecha = es viejo. */
    private static boolean despuesDelCorte(LocalDateTime creado, LocalDateTime corte) {
        if (corte == null) return true;
        return creado != null && !creado.isBefore(corte);
    }

    /**
     * Inicia la contabilidad en cero desde "fecha": cada cuenta arranca con el saldo que se escriba
     * (vacío = 0). Lo anterior no se borra: queda como historial y se puede deshacer.
     */
    @Transactional
    public void iniciarEnCero(LocalDate fecha, Map<Integer, BigDecimal> saldosDeArranque, String usuario) {
        if (fecha == null) throw new IllegalArgumentException("Elige desde qué fecha empieza la contabilidad.");
        if (fecha.isAfter(hoy().plusYears(1))) throw new IllegalArgumentException("La fecha de inicio está demasiado lejos.");
        for (CuentaContable c : cuentaRepository.findAll()) {
            // Se guarda el saldo de antes solo la primera vez, para poder deshacer y volver a como estaba
            if (c.getSaldoAntesInicio() == null) c.setSaldoAntesInicio(nz(c.getSaldoInicial()));
            BigDecimal arranque = saldosDeArranque == null ? null : saldosDeArranque.get(c.getId());
            c.setSaldoInicial(arranque != null ? arranque : BigDecimal.ZERO);
            cuentaRepository.save(c);
        }
        ConfiguracionContable conf = configuracionRepository.findById(ConfiguracionContable.ID).orElseGet(ConfiguracionContable::new);
        conf.setFechaInicio(fecha);
        conf.setFechaCambio(LocalDateTime.now(ZONA));
        conf.setCambiadoPor(texto(usuario, 80));
        configuracionRepository.save(conf);
    }

    /** Deshace el inicio en cero: la contabilidad vuelve a contar desde siempre y las cuentas recuperan su saldo inicial. */
    @Transactional
    public void deshacerInicio(String usuario) {
        for (CuentaContable c : cuentaRepository.findAll()) {
            if (c.getSaldoAntesInicio() != null) {
                c.setSaldoInicial(c.getSaldoAntesInicio());
                c.setSaldoAntesInicio(null);
                cuentaRepository.save(c);
            }
        }
        ConfiguracionContable conf = configuracionRepository.findById(ConfiguracionContable.ID).orElse(null);
        if (conf != null) {
            conf.setFechaInicio(null);
            conf.setFechaCambio(LocalDateTime.now(ZONA));
            conf.setCambiadoPor(texto(usuario, 80));
            configuracionRepository.save(conf);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SALDOS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Saldo actual de cada cuenta (por id): saldo inicial + ingresos − egresos − traslados que salen
     * + traslados que entran. Si hay fecha de inicio, solo cuentan los movimientos desde ese día.
     */
    @Transactional(readOnly = true)
    public Map<Integer, BigDecimal> saldosPorCuenta() {
        LocalDate inicio = fechaInicio();
        Map<Integer, BigDecimal> saldos = new HashMap<>();
        for (CuentaContable c : cuentaRepository.findAll()) saldos.put(c.getId(), saldoBase(c));
        List<Object[]> sumas = inicio == null ? movimientoRepository.sumasPorCuentaYTipo()
                : movimientoRepository.sumasPorCuentaYTipoDesde(inicio);
        for (Object[] fila : sumas) {
            int cuentaId = ((Number) fila[0]).intValue();
            BigDecimal suma = nz((BigDecimal) fila[2]);
            saldos.merge(cuentaId, MovimientoContable.INGRESO.equals(fila[1]) ? suma : suma.negate(), BigDecimal::add);
        }
        List<Object[]> entrantes = inicio == null ? movimientoRepository.trasladosEntrantes()
                : movimientoRepository.trasladosEntrantesDesde(inicio);
        for (Object[] fila : entrantes) {
            saldos.merge(((Number) fila[0]).intValue(), nz((BigDecimal) fila[1]), BigDecimal::add);
        }
        return saldos;
    }

    /**
     * Deja la cuenta con el saldo real de hoy: corrige su saldo inicial por la diferencia.
     * @return la diferencia aplicada (positiva si subió, negativa si bajó)
     */
    @Transactional
    public BigDecimal ajustarSaldo(int cuentaId, BigDecimal saldoReal) {
        if (saldoReal == null) throw new IllegalArgumentException("Escribe cuánto hay hoy en la cuenta (puede ser 0).");
        CuentaContable c = cuentaRepository.findById(cuentaId).orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no existe."));
        BigDecimal actual = saldosPorCuenta().getOrDefault(cuentaId, BigDecimal.ZERO);
        BigDecimal diferencia = saldoReal.subtract(actual);
        cambiarSaldoBase(c, saldoBase(c).add(diferencia));
        cuentaRepository.save(c);
        return diferencia;
    }

    /** Elimina una cuenta que nunca se ha usado. Si tiene movimientos, hay que desactivarla. */
    @Transactional
    public CuentaContable eliminarCuenta(int cuentaId) {
        CuentaContable c = cuentaRepository.findById(cuentaId).orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no existe."));
        long usados = movimientoRepository.contarDeCuenta(c);
        if (usados > 0) {
            throw new IllegalArgumentException("No se puede eliminar " + c.getNombre() + ": tiene " + usados
                    + " movimiento(s) anotados (también los de antes de la fecha de inicio). Desactívala: así deja de aparecer y se guarda el historial.");
        }
        if (c.isDeLaTienda()) {
            throw new IllegalArgumentException("No se puede eliminar " + c.getNombre()
                    + ": es la cuenta donde entra la plata de la tienda virtual. Elige otra cuenta para la tienda primero.");
        }
        cuentaRepository.delete(c);
        return c;
    }

    // ═══════════════════════════════════════════════════════════════
    // RESULTADO DE UN PERIODO
    // ═══════════════════════════════════════════════════════════════

    /** Una línea del resultado (una subcategoría y su total). */
    public static class Linea {
        private final String nombre;
        private final BigDecimal valor;
        Linea(String nombre, BigDecimal valor) { this.nombre = nombre; this.valor = valor; }
        public String getNombre() { return nombre; }
        public BigDecimal getValor() { return valor; }
    }

    /** Una categoría del resultado con su total y el detalle por subcategoría. */
    public static class Grupo {
        private final String nombre;
        private final BigDecimal total;
        private final List<Linea> lineas;
        Grupo(String nombre, BigDecimal total, List<Linea> lineas) { this.nombre = nombre; this.total = total; this.lineas = lineas; }
        public String getNombre() { return nombre; }
        public BigDecimal getTotal() { return total; }
        public List<Linea> getLineas() { return lineas; }
    }

    public static class Resultado {
        private final List<Grupo> ingresos = new ArrayList<>(), costos = new ArrayList<>(),
                gastos = new ArrayList<>(), otros = new ArrayList<>();
        private BigDecimal totalIngresos = BigDecimal.ZERO, totalCostos = BigDecimal.ZERO,
                totalGastos = BigDecimal.ZERO, totalOtros = BigDecimal.ZERO;
        private int cantidadMovimientos;

        public List<Grupo> getIngresos() { return ingresos; }
        public List<Grupo> getCostos() { return costos; }
        public List<Grupo> getGastos() { return gastos; }
        public List<Grupo> getOtros() { return otros; }
        public BigDecimal getTotalIngresos() { return totalIngresos; }
        public BigDecimal getTotalCostos() { return totalCostos; }
        public BigDecimal getTotalGastos() { return totalGastos; }
        public BigDecimal getTotalOtros() { return totalOtros; }
        public int getCantidadMovimientos() { return cantidadMovimientos; }
        /** Ingresos − costos de venta. */
        public BigDecimal getUtilidadBruta() { return totalIngresos.subtract(totalCostos); }
        /** Utilidad bruta − gastos. */
        public BigDecimal getUtilidadNeta() { return getUtilidadBruta().subtract(totalGastos); }
        /** Lo que quedó en plata después también de activos, préstamos y retiros. */
        public BigDecimal getResultadoEnCaja() { return getUtilidadNeta().subtract(totalOtros); }

        /** Costo de fábrica de los pedidos de Almacén del periodo que se sumó a los costos (0 = ninguno). */
        private BigDecimal costoFabrica = BigDecimal.ZERO;
        public BigDecimal getCostoFabrica() { return costoFabrica; }

        /**
         * Suma a los costos de venta lo que cobra la fábrica por los pedidos de Almacén del periodo
         * (el "Precio fábrica" de cada producto, o el costo escrito en Ventas). Sale como su propio grupo.
         */
        public void agregarCostoFabrica(BigDecimal costo, int pedidos) {
            if (costo == null || costo.signum() <= 0) return;
            costoFabrica = costoFabrica.add(costo);
            costos.add(0, new Grupo("COSTO DE FÁBRICA (pedidos de Almacén)", costo,
                    List.of(new Linea("Precio fábrica de " + pedidos + " pedido(s) del mes", costo))));
            totalCostos = totalCostos.add(costo);
        }
    }

    /**
     * Resultado entre dos fechas (incluidas). area = null para todo (la contabilidad es solo de Almacén).
     * Los traslados no cuentan: solo cambian la plata de cuenta. Lo anterior a la fecha de inicio tampoco.
     */
    @Transactional(readOnly = true)
    public Resultado resultado(LocalDate desde, LocalDate hasta, String area) {
        Resultado r = new Resultado();
        desde = desdeInicio(desde);
        if (desde != null && hasta != null && desde.isAfter(hasta)) return r;   // todo el periodo es de antes del inicio

        Map<Integer, CategoriaContable> categorias = new HashMap<>();
        Map<Integer, Map<String, BigDecimal>> porCategoria = new HashMap<>();
        for (MovimientoContable m : movimientoRepository.findByFechaBetweenOrderByFechaDescIdDesc(desde, hasta)) {
            if (MovimientoContable.TRASLADO.equals(m.getTipo()) || m.getSubcategoria() == null) continue;
            if (area != null && !area.equals(m.getArea())) continue;
            CategoriaContable c = m.getSubcategoria().getCategoria();
            BigDecimal valor = nz(m.getValor());
            // Si un ingreso quedó en una categoría de egresos (o al revés) se toma como devolución: resta
            if (MovimientoContable.INGRESO.equals(m.getTipo()) != c.isDeIngreso()) valor = valor.negate();
            categorias.put(c.getId(), c);
            porCategoria.computeIfAbsent(c.getId(), k -> new TreeMap<>())
                    .merge(m.getSubcategoria().getNombre(), valor, BigDecimal::add);
            r.cantidadMovimientos++;
        }

        List<CategoriaContable> ordenadas = new ArrayList<>(categorias.values());
        ordenadas.sort(Comparator.comparingInt(CategoriaContable::getOrden).thenComparing(CategoriaContable::getNombre));
        for (CategoriaContable c : ordenadas) {
            List<Linea> lineas = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> e : porCategoria.get(c.getId()).entrySet()) {
                lineas.add(new Linea(e.getKey(), e.getValue()));
                total = total.add(e.getValue());
            }
            lineas.sort((a, b) -> b.getValor().compareTo(a.getValor()));
            Grupo g = new Grupo(c.getNombre(), total, lineas);
            switch (c.getClase()) {
                case CategoriaContable.INGRESO -> { r.ingresos.add(g); r.totalIngresos = r.totalIngresos.add(total); }
                case CategoriaContable.COSTO -> { r.costos.add(g); r.totalCostos = r.totalCostos.add(total); }
                case CategoriaContable.GASTO -> { r.gastos.add(g); r.totalGastos = r.totalGastos.add(total); }
                default -> { r.otros.add(g); r.totalOtros = r.totalOtros.add(total); }
            }
        }
        return r;
    }

    // ═══════════════════════════════════════════════════════════════
    // MOVIMIENTOS
    // ═══════════════════════════════════════════════════════════════

    /** Lo que llega del formulario de movimiento. id = null para uno nuevo. */
    public record DatosMovimiento(Integer id, String tipo, LocalDate fecha, String area, Integer cuentaId,
                                  Integer cuentaDestinoId, Integer subcategoriaId, BigDecimal valor,
                                  String tercero, String descripcion) {}

    @Transactional
    public MovimientoContable guardarMovimiento(DatosMovimiento d, String usuario) {
        MovimientoContable m;
        if (d.id() != null) {
            m = movimientoRepository.findById(d.id()).orElseThrow(() -> new IllegalArgumentException("Ese movimiento ya no existe."));
        } else {
            m = new MovimientoContable();
            m.setCreadoPor(texto(usuario, 80));
            m.setFechaCreacion(LocalDateTime.now(ZONA));
        }

        String tipo = d.tipo() == null ? "" : d.tipo();
        if (!List.of(MovimientoContable.INGRESO, MovimientoContable.EGRESO, MovimientoContable.TRASLADO).contains(tipo)) {
            throw new IllegalArgumentException("Elige si es un ingreso, un egreso o un traslado.");
        }
        if (d.fecha() == null) throw new IllegalArgumentException("Escribe la fecha.");
        if (d.fecha().isAfter(hoy().plusDays(1))) throw new IllegalArgumentException("La fecha no puede ser futura.");
        LocalDate inicio = fechaInicio();
        if (inicio != null && d.fecha().isBefore(inicio)) {
            throw new IllegalArgumentException("La contabilidad empieza el " + configuracion().getFechaInicioFormateada()
                    + ": no se pueden anotar movimientos con fecha anterior.");
        }
        if (d.valor() == null || d.valor().signum() <= 0) throw new IllegalArgumentException("Escribe un valor mayor a 0.");

        CuentaContable cuenta = d.cuentaId() == null ? null : cuentaRepository.findById(d.cuentaId()).orElse(null);
        if (cuenta == null) {
            throw new IllegalArgumentException(MovimientoContable.TRASLADO.equals(tipo)
                    ? "Elige de qué cuenta sale la plata." : "Elige la cuenta.");
        }

        m.setTipo(tipo);
        m.setFecha(d.fecha());
        m.setArea(MovimientoContable.ALMACEN);   // la contabilidad es solo de Almacén (y la tienda virtual)
        m.setCuenta(cuenta);
        m.setValor(d.valor());
        m.setTercero(texto(d.tercero(), 150));
        m.setDescripcion(texto(d.descripcion(), 500));

        if (MovimientoContable.TRASLADO.equals(tipo)) {
            CuentaContable destino = d.cuentaDestinoId() == null ? null : cuentaRepository.findById(d.cuentaDestinoId()).orElse(null);
            if (destino == null) throw new IllegalArgumentException("Elige a qué cuenta va la plata.");
            if (destino.getId() == cuenta.getId()) throw new IllegalArgumentException("La cuenta de origen y la de destino deben ser distintas.");
            m.setCuentaDestino(destino);
            m.setSubcategoria(null);
        } else {
            SubcategoriaContable sub = d.subcategoriaId() == null ? null : subcategoriaRepository.findById(d.subcategoriaId()).orElse(null);
            if (sub == null) throw new IllegalArgumentException("Elige la categoría y lo que hay dentro de ella.");
            boolean esIngreso = MovimientoContable.INGRESO.equals(tipo);
            if (esIngreso && !sub.getCategoria().isDeIngreso()) {
                throw new IllegalArgumentException("\"" + sub.getNombre() + "\" es de egresos. Para un ingreso elige una categoría de ingresos.");
            }
            if (!esIngreso && sub.getCategoria().isDeIngreso()) {
                throw new IllegalArgumentException("\"" + sub.getNombre() + "\" es de ingresos. Para un egreso elige una categoría de gastos o costos.");
            }
            m.setSubcategoria(sub);
            m.setCuentaDestino(null);
        }
        MovimientoContable guardado = movimientoRepository.save(m);
        // Si es el pago de una deuda y le cambiaron el valor, la deuda se vuelve a cuadrar
        recalcularDeudasDe(guardado);
        return guardado;
    }

    /** Elimina un movimiento. Si era el pago (o un abono) de una deuda, esa deuda se vuelve a cuadrar. */
    @Transactional
    public MovimientoContable eliminarMovimiento(int id) {
        MovimientoContable m = movimientoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Ese movimiento ya no existe."));
        Set<CuentaPorPagar> deudas = new LinkedHashSet<>(porPagarRepository.findByMovimientoId(id));
        if (m.getPorPagarId() != null) porPagarRepository.findById(m.getPorPagarId()).ifPresent(deudas::add);
        movimientoRepository.delete(m);
        movimientoRepository.flush();
        for (CuentaPorPagar c : deudas) recalcularDeuda(c);
        return m;
    }

    /**
     * Anota como ingreso un abono registrado en Almacén (también el abono inicial de un pedido nuevo).
     * Antes de llamarlo hay que haber revisado que la cuenta existe (ver existeCuenta).
     */
    @Transactional
    public MovimientoContable registrarAbonoAlmacen(PedidoTienda pedido, BigDecimal monto, int cuentaId, String usuario) {
        CuentaContable cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("Elige a qué cuenta entró el abono."));
        MovimientoContable m = new MovimientoContable();
        m.setTipo(MovimientoContable.INGRESO);
        m.setFecha(hoy());
        m.setArea(MovimientoContable.ALMACEN);
        m.setSubcategoria(subcategoriaDeAbonos());
        m.setCuenta(cuenta);
        m.setValor(monto);
        m.setTercero(texto(pedido.getNombreCliente(), 150));
        m.setDescripcion("Abono pedido de Almacén #" + pedido.getId());
        m.setPedidoTiendaId(pedido.getId());
        m.setCreadoPor(texto(usuario, 80));
        m.setFechaCreacion(LocalDateTime.now(ZONA));
        return movimientoRepository.save(m);
    }

    public boolean existeCuenta(Integer cuentaId) {
        return cuentaId != null && cuentaRepository.existsById(cuentaId);
    }

    /** "VENTA DE PRODUCTOS" (de una categoría de ingresos); si la renombraron, la primera subcategoría de ingresos. */
    private SubcategoriaContable subcategoriaDeAbonos() {
        List<SubcategoriaContable> exacta = subcategoriaRepository.findByNombreIgnoreCaseAndCategoria_Clase(
                SUBCATEGORIA_ABONOS, CategoriaContable.INGRESO);
        if (!exacta.isEmpty()) return exacta.get(0);
        for (CategoriaContable c : categoriaRepository.findAllByOrderByOrdenAscNombreAsc()) {
            if (c.isDeIngreso() && !c.getSubcategorias().isEmpty()) return c.getSubcategorias().get(0);
        }
        System.err.println("[Contabilidad] No hay subcategorías de ingresos: el abono queda sin categoría.");
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // CUENTAS POR COBRAR (pedidos de Almacén con saldo)
    // ═══════════════════════════════════════════════════════════════

    /** Pedidos de Almacén que deben plata, del más viejo al más nuevo (sin los de antes del inicio en cero). */
    @Transactional(readOnly = true)
    public List<PedidoTienda> pedidosPorCobrar() {
        LocalDateTime corte = corteDeudas();
        List<PedidoTienda> lista = new ArrayList<>();
        for (PedidoTienda p : porCobrarRepository.findBySaldoGreaterThanOrderByFechaPedidoAsc(BigDecimal.ZERO)) {
            if (despuesDelCorte(p.getFechaPedido(), corte)) lista.add(p);
        }
        return lista;
    }

    @Transactional(readOnly = true)
    public BigDecimal totalPorCobrar() {
        return pedidosPorCobrar().stream().map(PedidoTienda::getSaldo).map(ContabilidadServicio::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ═══════════════════════════════════════════════════════════════
    // CUENTAS POR PAGAR (completas o por abonos)
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public CuentaPorPagar registrarPorPagar(String area, String acreedor, String concepto, BigDecimal valor, LocalDate vence) {
        if (acreedor == null || acreedor.isBlank()) throw new IllegalArgumentException("Escribe a quién se le debe.");
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("Escribe un valor mayor a 0.");
        CuentaPorPagar c = new CuentaPorPagar();
        c.setArea(MovimientoContable.ALMACEN);   // la contabilidad es solo de Almacén (y la tienda virtual)
        c.setAcreedor(texto(acreedor, 150));
        c.setConcepto(texto(concepto, 300));
        c.setValor(valor);
        c.setAbonado(BigDecimal.ZERO);
        c.setFechaVencimiento(vence);
        c.setFechaCreacion(LocalDateTime.now(ZONA));
        return porPagarRepository.save(c);
    }

    /** Deudas pendientes (sin las de antes del inicio en cero), por fecha de pago. */
    @Transactional(readOnly = true)
    public List<CuentaPorPagar> deudasPendientes() {
        LocalDateTime corte = corteDeudas();
        List<CuentaPorPagar> lista = new ArrayList<>();
        for (CuentaPorPagar c : porPagarRepository.findByPagadaFalseOrderByFechaVencimientoAscIdAsc()) {
            if (despuesDelCorte(c.getFechaCreacion(), corte)) lista.add(c);
        }
        return lista;
    }

    /** Las últimas deudas pagadas (máximo "cuantas"), sin las de antes del inicio en cero. */
    @Transactional(readOnly = true)
    public List<CuentaPorPagar> deudasPagadas(int cuantas) {
        LocalDateTime corte = corteDeudas();
        List<CuentaPorPagar> lista = new ArrayList<>();
        for (CuentaPorPagar c : porPagarRepository.findByPagadaTrueOrderByFechaPagoDescIdDesc()) {
            if (lista.size() >= cuantas) break;
            if (despuesDelCorte(c.getFechaCreacion(), corte)) lista.add(c);
        }
        return lista;
    }

    /** Lo que falta por pagar de todas las deudas pendientes. */
    @Transactional(readOnly = true)
    public BigDecimal totalPorPagar() {
        return deudasPendientes().stream().map(CuentaPorPagar::getSaldoPendiente).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Los pagos (abonos) de cada deuda, por número de deuda. */
    @Transactional(readOnly = true)
    public Map<Integer, List<MovimientoContable>> pagosDe(List<CuentaPorPagar> deudas) {
        Map<Integer, List<MovimientoContable>> mapa = new HashMap<>();
        List<Integer> ids = new ArrayList<>();
        for (CuentaPorPagar c : deudas) ids.add(c.getId());
        if (ids.isEmpty()) return mapa;
        for (MovimientoContable m : movimientoRepository.findByPorPagarIdInOrderByFechaAscIdAsc(ids)) {
            mapa.computeIfAbsent(m.getPorPagarId(), k -> new ArrayList<>()).add(m);
        }
        return mapa;
    }

    /**
     * Paga una deuda completa o una parte (abono) y crea el egreso correspondiente.
     * @param monto cuánto se paga ahora; vacío = todo lo que falta
     */
    @Transactional
    public MovimientoContable pagarPorPagar(int id, Integer cuentaId, Integer subcategoriaId, LocalDate fecha,
                                            BigDecimal monto, String usuario) {
        CuentaPorPagar c = porPagarRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Esa cuenta por pagar ya no existe."));
        if (c.isPagada()) throw new IllegalArgumentException("Esa deuda ya estaba pagada.");
        BigDecimal falta = c.getSaldoPendiente();
        BigDecimal pago = monto != null ? monto : falta;
        if (pago.signum() <= 0) throw new IllegalArgumentException("Escribe cuánto vas a pagar.");
        if (pago.compareTo(falta) > 0) {
            throw new IllegalArgumentException("A " + c.getAcreedor() + " solo le faltan $" + miles(falta) + ". Escribe un valor igual o menor.");
        }
        boolean completo = pago.compareTo(falta) == 0;
        String concepto = c.getConcepto() == null || c.getConcepto().isBlank() ? "deuda con " + c.getAcreedor() : c.getConcepto();
        String descripcion = (completo && !c.isConAbonos() ? "Pago: " : (completo ? "Último abono: " : "Abono: ")) + concepto;
        MovimientoContable m = guardarMovimiento(new DatosMovimiento(null, MovimientoContable.EGRESO,
                fecha != null ? fecha : hoy(), c.getArea(), cuentaId, null, subcategoriaId, pago,
                c.getAcreedor(), descripcion), usuario);
        m.setPorPagarId(c.getId());
        movimientoRepository.save(m);
        recalcularDeuda(c);
        return m;
    }

    /** Cuadra una deuda con sus pagos: cuánto se ha abonado y si ya quedó pagada. */
    @Transactional
    public void recalcularDeuda(CuentaPorPagar c) {
        List<MovimientoContable> pagos = new ArrayList<>(movimientoRepository.findByPorPagarIdOrderByFechaAscIdAsc(c.getId()));
        pagos.removeIf(p -> !MovimientoContable.EGRESO.equals(p.getTipo()));
        // Deudas de antes de los abonos: el pago quedó guardado solo en movimientoId
        if (c.getMovimientoId() != null && pagos.stream().noneMatch(p -> p.getId() == c.getMovimientoId())) {
            MovimientoContable viejo = movimientoRepository.findById(c.getMovimientoId()).orElse(null);
            if (viejo != null) pagos.add(viejo);
            else c.setMovimientoId(null);
        }
        BigDecimal abonado = BigDecimal.ZERO;
        MovimientoContable ultimo = null;
        for (MovimientoContable p : pagos) {
            abonado = abonado.add(nz(p.getValor()));
            if (ultimo == null || p.getFecha().isAfter(ultimo.getFecha())
                    || (p.getFecha().isEqual(ultimo.getFecha()) && p.getId() > ultimo.getId())) ultimo = p;
        }
        c.setAbonado(abonado);
        boolean pagada = abonado.compareTo(nz(c.getValor())) >= 0 && abonado.signum() > 0;
        c.setPagada(pagada);
        c.setFechaPago(pagada && ultimo != null ? ultimo.getFecha() : null);
        c.setMovimientoId(ultimo != null ? ultimo.getId() : null);
        porPagarRepository.save(c);
    }

    /** Si el movimiento es el pago de alguna deuda, la vuelve a cuadrar. */
    private void recalcularDeudasDe(MovimientoContable m) {
        Set<CuentaPorPagar> deudas = new LinkedHashSet<>(porPagarRepository.findByMovimientoId(m.getId()));
        if (m.getPorPagarId() != null) porPagarRepository.findById(m.getPorPagarId()).ifPresent(deudas::add);
        for (CuentaPorPagar c : deudas) recalcularDeuda(c);
    }

    /** Elimina una deuda anotada por error. Si ya tiene pagos, primero hay que borrar esos egresos. */
    @Transactional
    public CuentaPorPagar eliminarPorPagar(int id) {
        CuentaPorPagar c = porPagarRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Esa deuda ya no existe."));
        if (c.isPagada() || c.isConAbonos()) {
            throw new IllegalArgumentException("La deuda con " + c.getAcreedor() + " ya tiene pagos. Para eliminarla, borra primero sus pagos en Movimientos.");
        }
        porPagarRepository.delete(c);
        return c;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILIDADES
    // ═══════════════════════════════════════════════════════════════

    /** "1.500.000", "$1,500,000" o "1500000" → 1500000. Vacío o sin números → null. (Valores en pesos, sin centavos.) */
    public static BigDecimal pesos(String s) {
        if (s == null) return null;
        String digitos = s.replaceAll("\\D", "");
        if (digitos.isEmpty()) return null;
        if (digitos.length() > 15) throw new IllegalArgumentException("El valor es demasiado grande.");
        return new BigDecimal(digitos);
    }

    /** Como pesos(), pero respeta el menos adelante ("-200.000" → -200000). */
    public static BigDecimal pesosConSigno(String s) {
        BigDecimal v = pesos(s);
        return v != null && s.trim().startsWith("-") ? v.negate() : v;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String miles(BigDecimal v) {
        return java.text.NumberFormat.getIntegerInstance(new Locale("es", "CO")).format(v == null ? BigDecimal.ZERO : v);
    }

    private static String texto(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}