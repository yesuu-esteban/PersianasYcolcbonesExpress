package Colcones_Persinas.proyecto_express.servicio.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.*;
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
 *  - Guardar movimientos, pagar cuentas por pagar y anotar solos los abonos de Almacén.
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

    public ContabilidadServicio(CuentaContableRepository cuentaRepository,
                                SubcategoriaContableRepository subcategoriaRepository,
                                CategoriaContableRepository categoriaRepository,
                                MovimientoContableRepository movimientoRepository,
                                CuentaPorPagarRepository porPagarRepository) {
        this.cuentaRepository = cuentaRepository;
        this.subcategoriaRepository = subcategoriaRepository;
        this.categoriaRepository = categoriaRepository;
        this.movimientoRepository = movimientoRepository;
        this.porPagarRepository = porPagarRepository;
    }

    public static LocalDate hoy() { return LocalDate.now(ZONA); }

    // ═══════════════════════════════════════════════════════════════
    // SALDOS
    // ═══════════════════════════════════════════════════════════════

    /** Saldo actual de cada cuenta (por id): saldo inicial + ingresos − egresos − traslados que salen + traslados que entran. */
    @Transactional(readOnly = true)
    public Map<Integer, BigDecimal> saldosPorCuenta() {
        Map<Integer, BigDecimal> saldos = new HashMap<>();
        for (CuentaContable c : cuentaRepository.findAll()) saldos.put(c.getId(), nz(c.getSaldoInicial()));
        for (Object[] fila : movimientoRepository.sumasPorCuentaYTipo()) {
            int cuentaId = ((Number) fila[0]).intValue();
            BigDecimal suma = nz((BigDecimal) fila[2]);
            saldos.merge(cuentaId, MovimientoContable.INGRESO.equals(fila[1]) ? suma : suma.negate(), BigDecimal::add);
        }
        for (Object[] fila : movimientoRepository.trasladosEntrantes()) {
            saldos.merge(((Number) fila[0]).intValue(), nz((BigDecimal) fila[1]), BigDecimal::add);
        }
        return saldos;
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
    }

    /**
     * Resultado entre dos fechas (incluidas). area = null para todo (la contabilidad es solo de Almacén).
     * Los traslados no cuentan: solo cambian la plata de cuenta.
     */
    @Transactional(readOnly = true)
    public Resultado resultado(LocalDate desde, LocalDate hasta, String area) {
        Map<Integer, CategoriaContable> categorias = new HashMap<>();
        Map<Integer, Map<String, BigDecimal>> porCategoria = new HashMap<>();
        Resultado r = new Resultado();

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
            if (sub == null) throw new IllegalArgumentException("Elige la categoría.");
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
        return movimientoRepository.save(m);
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
    // CUENTAS POR PAGAR
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
        c.setFechaVencimiento(vence);
        c.setFechaCreacion(LocalDateTime.now(ZONA));
        return porPagarRepository.save(c);
    }

    /** Marca la deuda como pagada y crea el egreso correspondiente. */
    @Transactional
    public MovimientoContable pagarPorPagar(int id, Integer cuentaId, Integer subcategoriaId, LocalDate fecha, String usuario) {
        CuentaPorPagar c = porPagarRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Esa cuenta por pagar ya no existe."));
        if (c.isPagada()) throw new IllegalArgumentException("Esa cuenta ya estaba pagada.");
        MovimientoContable m = guardarMovimiento(new DatosMovimiento(null, MovimientoContable.EGRESO,
                fecha != null ? fecha : hoy(), c.getArea(), cuentaId, null, subcategoriaId, c.getValor(),
                c.getAcreedor(), "Pago: " + c.getConcepto()), usuario);
        c.setPagada(true);
        c.setFechaPago(m.getFecha());
        c.setMovimientoId(m.getId());
        porPagarRepository.save(c);
        return m;
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

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String texto(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}