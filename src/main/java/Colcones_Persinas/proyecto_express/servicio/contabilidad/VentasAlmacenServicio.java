package Colcones_Persinas.proyecto_express.servicio.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.CuentaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.DatosVenta;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.MovimientoContable;
import Colcones_Persinas.proyecto_express.repository.almacen.PedidoTiendaVentasRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.CuentaContableRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.DatosVentaRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.MovimientoAbonosRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.MovimientoContableRepository;
import Colcones_Persinas.proyecto_express.servicio.tienda.TiendaServicio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Los pedidos de Almacén dentro de la contabilidad (pantalla "Ventas"), con las mismas columnas de la
 * planilla de ventas de contabilidad:
 *
 *   SUBTOTAL · IVA · RTF · TOTAL VENTA · ABONOS (abono 1, abono 2…) · POR COBRAR · PROVEEDOR · COSTO ·
 *   GASTO · FINALIZADO · UTILIDAD · PUNTO DE VENTA · QUIÉN INSTALÓ
 *
 * Cómo se calcula cada venta:
 *  - TOTAL VENTA = el precio al cliente del pedido (si lleva IVA, el precio YA lo incluye).
 *  - Si lleva IVA: SUBTOTAL = total ÷ (1 + 19 %) y IVA = total − subtotal. Sin IVA: subtotal = total.
 *  - RTF (retención en la fuente) = subtotal × % de retención. El cliente la descuenta de lo que paga.
 *  - POR COBRAR = total venta − RTF − abonos. Negativo = el cliente pagó de más (saldo a favor).
 *  - COSTO = el costo escrito en Ventas; si no se escribió, el costo de fábrica del pedido.
 *  - UTILIDAD = total venta − costo − gasto (igual que la planilla).
 *
 * También:
 *  - Registrar un pago desde aquí: se suma al abono del pedido (igual que el "+" de Almacén), queda como
 *    ingreso en la cuenta elegida y se dice cuánto queda debiendo.
 *  - Qué abonos todavía NO están anotados en contabilidad (pedidos de antes de la contabilidad) y anotarlos.
 *  - A qué cuenta entró cada abono anotado, y cambiarla si se eligió mal.
 *
 * Los pedidos que creó la tienda virtual en Almacén (vendedor "Tienda virtual") no salen aquí:
 * la tienda tiene su propia contabilidad. Si se inició la contabilidad en cero, los pedidos de antes
 * no salen y no se pueden anotar pagos con fecha anterior al inicio.
 */
@Service
public class VentasAlmacenServicio {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal IVA_POR_DEFECTO = new BigDecimal("19");

    /** Puntos de venta que se sugieren en la lista (se puede escribir otro). */
    public static final List<String> PUNTOS_DE_VENTA = List.of("Redes", "Almacén", "Recomendado", "Distribuidor", "Página web", "Otro");

    /** Porcentajes de retención en la fuente más usados (se puede escribir otro). */
    public static final List<String> RETENCIONES = List.of("0", "1", "2", "2.5", "3.5", "4", "6", "10", "11");

    private static final Set<String> ESTADOS_FACTURA = Set.of(
            DatosVenta.FACTURA_PENDIENTE, DatosVenta.FACTURADA, DatosVenta.FACTURA_NO_REQUIERE);
    private static final Set<String> ESTADOS_INSTALACION = Set.of(
            DatosVenta.NO_INSTALADO, DatosVenta.INSTALADO, DatosVenta.ENTREGADO);

    private final PedidoTiendaVentasRepository pedidoRepository;
    private final MovimientoAbonosRepository abonosRepository;
    private final MovimientoContableRepository movimientoRepository;
    private final CuentaContableRepository cuentaRepository;
    private final DatosVentaRepository datosRepository;
    private final ContabilidadServicio contabilidadServicio;

    public VentasAlmacenServicio(PedidoTiendaVentasRepository pedidoRepository,
                                 MovimientoAbonosRepository abonosRepository,
                                 MovimientoContableRepository movimientoRepository,
                                 CuentaContableRepository cuentaRepository,
                                 DatosVentaRepository datosRepository,
                                 ContabilidadServicio contabilidadServicio) {
        this.pedidoRepository = pedidoRepository;
        this.abonosRepository = abonosRepository;
        this.movimientoRepository = movimientoRepository;
        this.cuentaRepository = cuentaRepository;
        this.datosRepository = datosRepository;
        this.contabilidadServicio = contabilidadServicio;
    }

    // ═══════════════════════════════════════════════════════════════
    // Lo que ve la pantalla
    // ═══════════════════════════════════════════════════════════════

    /** Un abono de un pedido que ya está anotado en contabilidad (un ingreso en una cuenta). */
    public static class AbonoAnotado {
        private int movimientoId, cuentaId;
        private String fecha = "", cuenta = "";
        private BigDecimal valor = BigDecimal.ZERO;
        public int getMovimientoId() { return movimientoId; }
        public int getCuentaId() { return cuentaId; }
        public String getFecha() { return fecha; }
        public String getCuenta() { return cuenta; }
        public BigDecimal getValor() { return valor; }
    }

    /** Un pago en la columna de abonos: "Abono 1 · 05/10 · NEQUI · $240.000 → queda $250.000". */
    public static class Pago {
        private int numero;
        private String fecha = "", cuenta = "";
        private BigDecimal valor = BigDecimal.ZERO, quedaDebiendo = BigDecimal.ZERO;
        private boolean anotado = true;
        public int getNumero() { return numero; }
        public String getFecha() { return fecha; }
        public String getCuenta() { return cuenta; }
        public BigDecimal getValor() { return valor; }
        /** Lo que quedó debiendo el cliente después de este pago. */
        public BigDecimal getQuedaDebiendo() { return quedaDebiendo; }
        /** false = abono hecho en Almacén que todavía no está anotado en contabilidad. */
        public boolean isAnotado() { return anotado; }
    }

    /** Un pedido de Almacén con todas las columnas de la planilla ya calculadas. */
    public static class FilaVenta {
        private int id;
        private String fecha = "", fechaIso = "", cliente = "", cedula = "", direccion = "", vendedor = "",
                estado = "", estadoPago = "";
        // Datos de contabilidad (lo que se edita en Ventas)
        private String factura = "", estadoFactura = DatosVenta.FACTURA_PENDIENTE, proveedor = "", proveedorPedido = "",
                estadoInstalacion = DatosVenta.NO_INSTALADO, puntoVenta = "", quienInstalo = "";
        private boolean llevaIva, costoManual;
        private BigDecimal porcentajeIva = IVA_POR_DEFECTO, porcentajeRetencion = BigDecimal.ZERO;
        // Valores
        private BigDecimal precio = BigDecimal.ZERO, subtotal = BigDecimal.ZERO, iva = BigDecimal.ZERO,
                retencion = BigDecimal.ZERO, totalVenta = BigDecimal.ZERO, aCobrar = BigDecimal.ZERO,
                costoFabrica = BigDecimal.ZERO, costo = BigDecimal.ZERO, gasto = BigDecimal.ZERO,
                utilidad = BigDecimal.ZERO, abonado = BigDecimal.ZERO, saldo = BigDecimal.ZERO,
                enContabilidad = BigDecimal.ZERO, falta = BigDecimal.ZERO;
        private final List<AbonoAnotado> abonos = new ArrayList<>();
        private final List<Pago> pagos = new ArrayList<>();

        public int getId() { return id; }
        public String getFecha() { return fecha; }
        /** La fecha del pedido como "2026-10-05", para los campos de fecha. */
        public String getFechaIso() { return fechaIso; }
        public String getCliente() { return cliente; }
        public String getCedula() { return cedula; }
        public String getDireccion() { return direccion; }
        public String getVendedor() { return vendedor; }
        public String getEstado() { return estado; }
        public String getEstadoPago() { return estadoPago; }

        public String getFactura() { return factura; }
        public String getEstadoFactura() { return estadoFactura; }
        public String getEstadoFacturaTexto() { return textoEstadoFactura(estadoFactura); }
        public boolean isLlevaIva() { return llevaIva; }
        public BigDecimal getPorcentajeIva() { return porcentajeIva; }
        public BigDecimal getPorcentajeRetencion() { return porcentajeRetencion; }
        /** Proveedor escrito en Ventas o, si no hay, el "Fabricante" del pedido. */
        public String getProveedor() { return proveedor; }
        /** El "Fabricante" que dice el pedido de Almacén (para mostrarlo como sugerencia). */
        public String getProveedorPedido() { return proveedorPedido; }
        public String getEstadoInstalacion() { return estadoInstalacion; }
        public String getEstadoInstalacionTexto() { return textoInstalacion(estadoInstalacion); }
        public String getPuntoVenta() { return puntoVenta; }
        public String getQuienInstalo() { return quienInstalo; }

        /** Precio al cliente tal como está en el pedido de Almacén. */
        public BigDecimal getPrecio() { return precio; }
        public BigDecimal getSubtotal() { return subtotal; }
        public BigDecimal getIva() { return iva; }
        /** Retención en la fuente (RTF), en pesos. */
        public BigDecimal getRetencion() { return retencion; }
        public BigDecimal getTotalVenta() { return totalVenta; }
        /** Lo que el cliente tiene que pagar de verdad: total venta − retención (RTF). */
        public BigDecimal getNeto() { return aCobrar; }
        public BigDecimal getCostoFabrica() { return costoFabrica; }
        public BigDecimal getCosto() { return costo; }
        /** true = el costo lo escribieron en Ventas; false = es el de fábrica del pedido. */
        public boolean isCostoManual() { return costoManual; }
        public BigDecimal getGasto() { return gasto; }
        public BigDecimal getUtilidad() { return utilidad; }
        public BigDecimal getAbonado() { return abonado; }
        /** Lo que falta por cobrar (negativo = saldo a favor del cliente). */
        public BigDecimal getSaldo() { return saldo; }
        public boolean isPagada() { return saldo.signum() <= 0; }
        /** Los pagos uno por uno con lo que quedó debiendo después de cada uno. */
        public List<Pago> getPagos() { return pagos; }
        /** Los abonos de este pedido que ya están en contabilidad, con su cuenta. */
        public List<AbonoAnotado> getAbonos() { return abonos; }
        /** Lo que ya está anotado como ingreso en contabilidad para este pedido. */
        public BigDecimal getEnContabilidad() { return enContabilidad; }
        /** Abonos del pedido que todavía no están en contabilidad. */
        public BigDecimal getFalta() { return falta; }
        public boolean isFaltaAnotar() { return falta.signum() > 0; }
    }

    /** Los pedidos de un mes con sus totales (la fila de totales de arriba de la planilla). */
    public static class Ventas {
        private final List<FilaVenta> filas = new ArrayList<>();
        private BigDecimal subtotal = BigDecimal.ZERO, iva = BigDecimal.ZERO, retencion = BigDecimal.ZERO,
                vendido = BigDecimal.ZERO, costo = BigDecimal.ZERO, gasto = BigDecimal.ZERO,
                utilidad = BigDecimal.ZERO, abonado = BigDecimal.ZERO, saldo = BigDecimal.ZERO,
                faltaAnotar = BigDecimal.ZERO, abonosDelMes = BigDecimal.ZERO;
        private int pedidosSinAnotar, conSaldo;

        public List<FilaVenta> getFilas() { return filas; }
        public int getCantidad() { return filas.size(); }
        public BigDecimal getSubtotal() { return subtotal; }
        public BigDecimal getIva() { return iva; }
        public BigDecimal getRetencion() { return retencion; }
        /** "Ventas totales": la suma de los totales de venta (con IVA). */
        public BigDecimal getVendido() { return vendido; }
        public BigDecimal getCosto() { return costo; }
        /** Igual que getCosto() (nombre viejo que usa el Resumen). */
        public BigDecimal getCostoFabrica() { return costo; }
        public BigDecimal getGasto() { return gasto; }
        public BigDecimal getUtilidad() { return utilidad; }
        public BigDecimal getAbonado() { return abonado; }
        /** Total por cobrar (solo lo que deben; los saldos a favor no restan). */
        public BigDecimal getSaldo() { return saldo; }
        /** Cuántas ventas todavía deben algo. */
        public int getConSaldo() { return conSaldo; }
        /** Abonos de estos pedidos que no están en contabilidad. */
        public BigDecimal getFaltaAnotar() { return faltaAnotar; }
        public int getPedidosSinAnotar() { return pedidosSinAnotar; }
        /** Abonos de Almacén que entraron a las cuentas en el mes (de pedidos de este mes o de antes). */
        public BigDecimal getAbonosDelMes() { return abonosDelMes; }
    }

    /** Lo que pasó al registrar un pago. */
    public static class ResultadoPago {
        private final MovimientoContable movimiento;
        private final BigDecimal quedaDebiendo;
        private final String cliente;
        ResultadoPago(MovimientoContable movimiento, BigDecimal quedaDebiendo, String cliente) {
            this.movimiento = movimiento;
            this.quedaDebiendo = quedaDebiendo;
            this.cliente = cliente;
        }
        public MovimientoContable getMovimiento() { return movimiento; }
        public BigDecimal getQuedaDebiendo() { return quedaDebiendo; }
        public String getCliente() { return cliente; }
    }

    // ═══════════════════════════════════════════════════════════════
    // Consultar
    // ═══════════════════════════════════════════════════════════════

    /** Pedidos de Almacén hechos entre dos fechas (incluidas), sin los de antes del inicio en cero. */
    @Transactional(readOnly = true)
    public Ventas ventas(LocalDate desde, LocalDate hasta) {
        Ventas v = new Ventas();
        List<PedidoTienda> pedidos = new ArrayList<>();
        // Si se inició en cero, los pedidos de antes no cuentan (mismo corte que "Por cobrar")
        LocalDateTime desdeHora = desde.atStartOfDay();
        LocalDateTime hastaHora = hasta.atTime(23, 59, 59);
        LocalDateTime corte = contabilidadServicio.corteDeudas();
        if (corte != null && corte.isAfter(desdeHora)) desdeHora = corte;
        if (!desdeHora.isAfter(hastaHora)) {
            for (PedidoTienda p : pedidoRepository.findByFechaPedidoBetweenOrderByFechaPedidoDescIdDesc(desdeHora, hastaHora)) {
                if (!esDeLaTienda(p)) pedidos.add(p);
            }
        }

        // Los abonos ya anotados de estos pedidos y sus datos de contabilidad, agrupados por pedido
        Map<Integer, List<MovimientoContable>> abonosPorPedido = new HashMap<>();
        Map<Integer, DatosVenta> datosPorPedido = new HashMap<>();
        if (!pedidos.isEmpty()) {
            List<Integer> ids = new ArrayList<>();
            for (PedidoTienda p : pedidos) ids.add(p.getId());
            for (MovimientoContable m : abonosRepository.findByTipoAndPedidoTiendaIdInOrderByFechaAscIdAsc(MovimientoContable.INGRESO, ids)) {
                if (m.getPedidoTiendaId() == null) continue;
                abonosPorPedido.computeIfAbsent(m.getPedidoTiendaId(), k -> new ArrayList<>()).add(m);
            }
            for (DatosVenta d : datosRepository.findByPedidoTiendaIdIn(ids)) {
                datosPorPedido.put(d.getPedidoTiendaId(), d);
            }
        }

        for (PedidoTienda p : pedidos) {
            FilaVenta f = calcular(p, datosPorPedido.get(p.getId()), abonosPorPedido.getOrDefault(p.getId(), List.of()));
            v.filas.add(f);

            v.subtotal = v.subtotal.add(f.subtotal);
            v.iva = v.iva.add(f.iva);
            v.retencion = v.retencion.add(f.retencion);
            v.vendido = v.vendido.add(f.totalVenta);
            v.costo = v.costo.add(f.costo);
            v.gasto = v.gasto.add(f.gasto);
            v.utilidad = v.utilidad.add(f.utilidad);
            v.abonado = v.abonado.add(f.abonado);
            if (f.saldo.signum() > 0) {
                v.saldo = v.saldo.add(f.saldo);
                v.conSaldo++;
            }
            v.faltaAnotar = v.faltaAnotar.add(f.falta);
            if (f.isFaltaAnotar()) v.pedidosSinAnotar++;
        }
        // Abonos que entraron a las cuentas en el periodo (solo desde el inicio en cero)
        LocalDate desdeAbonos = contabilidadServicio.desdeInicio(desde);
        v.abonosDelMes = desdeAbonos.isAfter(hasta) ? BigDecimal.ZERO : nz(abonosRepository.abonosEntre(desdeAbonos, hasta));
        return v;
    }

    /** Todas las columnas de un pedido. abonos = sus ingresos ya anotados, del más viejo al más nuevo. */
    FilaVenta calcular(PedidoTienda p, DatosVenta d, List<MovimientoContable> abonos) {
        FilaVenta f = new FilaVenta();
        f.id = p.getId();
        if (p.getFechaPedido() != null) {
            f.fecha = p.getFechaPedido().format(FMT);
            f.fechaIso = p.getFechaPedido().toLocalDate().toString();
        }
        f.cliente = texto(p.getNombreCliente());
        f.cedula = texto(p.getCedula());
        f.direccion = texto(p.getDireccion());
        f.vendedor = texto(p.getVendedor());
        f.estado = texto(p.getEstado());
        f.estadoPago = texto(p.getEstadoPago());
        f.proveedorPedido = texto(p.getFabrica());

        // Datos de contabilidad (o los de por defecto si nunca se editaron)
        if (d != null) {
            f.factura = texto(d.getNumeroFactura());
            f.estadoFactura = ESTADOS_FACTURA.contains(d.getEstadoFactura()) ? d.getEstadoFactura() : DatosVenta.FACTURA_PENDIENTE;
            f.llevaIva = Boolean.TRUE.equals(d.getLlevaIva());
            f.porcentajeIva = d.getPorcentajeIva() != null ? d.getPorcentajeIva() : IVA_POR_DEFECTO;
            f.porcentajeRetencion = nz(d.getPorcentajeRetencion());
            f.proveedor = texto(d.getProveedor());
            f.costoManual = d.getCosto() != null;
            f.gasto = nz(d.getGasto());
            f.estadoInstalacion = ESTADOS_INSTALACION.contains(d.getEstadoInstalacion()) ? d.getEstadoInstalacion() : DatosVenta.NO_INSTALADO;
            f.puntoVenta = texto(d.getPuntoVenta());
            f.quienInstalo = texto(d.getQuienInstalo());
        }
        if (f.proveedor.isBlank()) f.proveedor = f.proveedorPedido;

        // Precio, IVA (incluido en el precio), retención y total
        f.precio = pesos(p.getPrecioCliente());
        f.totalVenta = f.precio;
        if (f.llevaIva && f.porcentajeIva.signum() > 0) {
            BigDecimal divisor = BigDecimal.ONE.add(f.porcentajeIva.divide(CIEN, 6, RoundingMode.HALF_UP));
            f.subtotal = f.totalVenta.divide(divisor, 0, RoundingMode.HALF_UP);
            f.iva = f.totalVenta.subtract(f.subtotal);
        } else {
            f.subtotal = f.totalVenta;
            f.iva = BigDecimal.ZERO;
        }
        f.retencion = pesos(f.subtotal.multiply(f.porcentajeRetencion).divide(CIEN, 6, RoundingMode.HALF_UP));
        f.aCobrar = f.totalVenta.subtract(f.retencion);

        // Costo, gasto y utilidad
        f.costoFabrica = pesos(p.getCostoFabricaTotal());
        f.costo = f.costoManual ? pesos(d.getCosto()) : f.costoFabrica;
        f.gasto = pesos(f.gasto);
        f.utilidad = f.totalVenta.subtract(f.costo).subtract(f.gasto);

        // Abonos: lo que dice el pedido de Almacén es lo que manda
        f.abonado = pesos(p.getAbono());
        f.saldo = f.aCobrar.subtract(f.abonado);

        BigDecimal anotado = BigDecimal.ZERO;
        for (MovimientoContable m : abonos) {
            AbonoAnotado a = new AbonoAnotado();
            a.movimientoId = m.getId();
            a.fecha = m.getFecha() != null ? m.getFecha().format(FMT) : "";
            a.valor = nz(m.getValor());
            if (m.getCuenta() != null) {
                a.cuentaId = m.getCuenta().getId();
                a.cuenta = texto(m.getCuenta().getNombre());
            }
            f.abonos.add(a);
            anotado = anotado.add(a.valor);
        }
        f.enContabilidad = anotado;
        f.falta = f.abonado.subtract(anotado).max(BigDecimal.ZERO);

        // La columna de pagos: primero lo abonado en Almacén que no está anotado (pedidos viejos),
        // luego cada abono anotado, cada uno con lo que quedó debiendo.
        BigDecimal debe = f.aCobrar;
        int numero = 1;
        if (f.falta.signum() > 0) {
            Pago pg = new Pago();
            pg.numero = numero++;
            pg.fecha = f.fecha;
            pg.cuenta = "Sin anotar en contabilidad";
            pg.valor = f.falta;
            pg.anotado = false;
            debe = debe.subtract(pg.valor);
            pg.quedaDebiendo = debe;
            f.pagos.add(pg);
        }
        for (AbonoAnotado a : f.abonos) {
            Pago pg = new Pago();
            pg.numero = numero++;
            pg.fecha = a.fecha;
            pg.cuenta = a.cuenta;
            pg.valor = a.valor;
            debe = debe.subtract(a.valor);
            pg.quedaDebiendo = debe;
            f.pagos.add(pg);
        }
        return f;
    }

    // ═══════════════════════════════════════════════════════════════
    // Registrar un pago
    // ═══════════════════════════════════════════════════════════════

    /**
     * Registra un pago del cliente desde Ventas: se suma al abono del pedido (igual que el "+" de Almacén),
     * queda como ingreso en la cuenta elegida y devuelve cuánto queda debiendo.
     */
    @Transactional
    public ResultadoPago registrarPago(int pedidoId, BigDecimal valor, Integer cuentaId, LocalDate fecha, String usuario) {
        PedidoTienda p = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese pedido ya no existe."));
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("Escribe cuánto pagó el cliente.");
        valor = pesos(valor);
        if (!contabilidadServicio.existeCuenta(cuentaId)) {
            throw new IllegalArgumentException("Elige a qué cuenta entró la plata.");
        }
        validarFecha(fecha);

        FilaVenta f = calcular(p, datosRepository.findByPedidoTiendaId(pedidoId).orElse(null), List.of());
        if (f.saldo.signum() <= 0) {
            throw new IllegalArgumentException("La venta #" + pedidoId + " (" + f.cliente + ") ya está pagada completa.");
        }
        if (valor.compareTo(f.saldo) > 0) {
            throw new IllegalArgumentException("El pago ($" + miles(valor) + ") es mayor a lo que debe "
                    + f.cliente + " ($" + miles(f.saldo) + ").");
        }

        // Igual que Almacén: abono += pago, saldo = precio − abono
        BigDecimal abono = nz(p.getAbono()).add(valor);
        p.setAbono(abono);
        p.setSaldo(nz(p.getPrecioCliente()).subtract(abono));
        pedidoRepository.save(p);

        MovimientoContable m = contabilidadServicio.registrarAbonoAlmacen(p, valor, cuentaId, usuario);
        m.setFecha(fecha);
        m = movimientoRepository.save(m);
        return new ResultadoPago(m, f.saldo.subtract(valor), f.cliente);
    }

    // ═══════════════════════════════════════════════════════════════
    // Guardar factura, IVA, retención, costo, gasto…
    // ═══════════════════════════════════════════════════════════════

    /**
     * Guarda los datos de contabilidad de una venta. Campos (todos opcionales):
     * factura, estadoFactura, llevaIva ("true"), porcentajeIva, porcentajeRetencion, proveedor,
     * costo (vacío = el de fábrica), gasto, estadoInstalacion, puntoVenta, quienInstalo.
     */
    @Transactional
    public DatosVenta guardarDatos(int pedidoId, Map<String, String> campos, String usuario) {
        PedidoTienda p = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese pedido ya no existe."));
        DatosVenta d = datosRepository.findByPedidoTiendaId(pedidoId).orElseGet(() -> {
            DatosVenta nuevo = new DatosVenta();
            nuevo.setPedidoTiendaId(pedidoId);
            return nuevo;
        });

        d.setNumeroFactura(corto(campos.get("factura"), 40));
        String estadoFactura = texto(campos.get("estadoFactura")).trim().toUpperCase(Locale.ROOT);
        d.setEstadoFactura(ESTADOS_FACTURA.contains(estadoFactura) ? estadoFactura
                : (d.getNumeroFactura().isBlank() ? DatosVenta.FACTURA_PENDIENTE : DatosVenta.FACTURADA));

        d.setLlevaIva("true".equalsIgnoreCase(texto(campos.get("llevaIva")).trim()));
        BigDecimal pctIva = porcentaje(campos.get("porcentajeIva"), "El IVA", IVA_POR_DEFECTO);
        if (pctIva.compareTo(new BigDecimal("50")) > 0) throw new IllegalArgumentException("El IVA no puede ser mayor a 50 %.");
        d.setPorcentajeIva(pctIva);
        BigDecimal pctRet = porcentaje(campos.get("porcentajeRetencion"), "La retención", BigDecimal.ZERO);
        if (pctRet.compareTo(new BigDecimal("30")) > 0) throw new IllegalArgumentException("La retención no puede ser mayor a 30 %.");
        d.setPorcentajeRetencion(pctRet);

        String proveedor = corto(campos.get("proveedor"), 120);
        // Si dejan el mismo fabricante del pedido no se guarda: así, si cambian el pedido, se ve el nuevo.
        d.setProveedor(proveedor.isBlank() || proveedor.equalsIgnoreCase(texto(p.getFabrica()).trim()) ? null : proveedor);

        d.setCosto(plata(campos.get("costo"), "El costo"));
        BigDecimal gasto = plata(campos.get("gasto"), "El gasto");
        d.setGasto(gasto == null ? BigDecimal.ZERO : gasto);

        String instalacion = texto(campos.get("estadoInstalacion")).trim().toUpperCase(Locale.ROOT);
        d.setEstadoInstalacion(ESTADOS_INSTALACION.contains(instalacion) ? instalacion : DatosVenta.NO_INSTALADO);
        d.setPuntoVenta(corto(campos.get("puntoVenta"), 40));
        d.setQuienInstalo(corto(campos.get("quienInstalo"), 80));

        d.setActualizadoPor(corto(usuario, 80));
        d.setFechaActualizacion(LocalDateTime.now(ContabilidadServicio.ZONA));
        return datosRepository.save(d);
    }

    // ═══════════════════════════════════════════════════════════════
    // Abonos viejos y cuentas
    // ═══════════════════════════════════════════════════════════════

    /**
     * Anota como ingreso lo que le falta en contabilidad a un pedido (abonado − ya anotado).
     * Sirve para pedidos hechos antes de empezar la contabilidad o cuyo abono no se anotó.
     */
    @Transactional
    public MovimientoContable anotarAbonosFaltantes(int pedidoId, Integer cuentaId, LocalDate fecha, String usuario) {
        PedidoTienda p = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese pedido ya no existe."));
        if (!contabilidadServicio.existeCuenta(cuentaId)) {
            throw new IllegalArgumentException("Elige a qué cuenta entró la plata.");
        }
        validarFecha(fecha);

        BigDecimal falta = nz(p.getAbono()).subtract(nz(abonosRepository.anotadoDelPedido(pedidoId)));
        if (falta.signum() <= 0) {
            throw new IllegalArgumentException("El pedido #" + pedidoId + " ya tiene todos sus abonos en contabilidad.");
        }

        MovimientoContable m = contabilidadServicio.registrarAbonoAlmacen(p, falta, cuentaId, usuario);
        m.setFecha(fecha);
        m.setDescripcion("Abono pedido de Almacén #" + pedidoId + " (anotado desde Ventas)");
        return movimientoRepository.save(m);
    }

    /**
     * Cambia la cuenta a la que entró un abono ya anotado (por si se eligió mal: Nequi en vez de Bancolombia…).
     * El valor y la fecha no cambian; para eso está el lápiz en Movimientos.
     */
    @Transactional
    public MovimientoContable cambiarCuentaAbono(int movimientoId, Integer cuentaId) {
        MovimientoContable m = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese abono ya no está en contabilidad."));
        if (m.getPedidoTiendaId() == null || !MovimientoContable.INGRESO.equals(m.getTipo())) {
            throw new IllegalArgumentException("Ese movimiento no es un abono de un pedido de Almacén.");
        }
        CuentaContable cuenta = cuentaId == null ? null : cuentaRepository.findById(cuentaId).orElse(null);
        if (cuenta == null) throw new IllegalArgumentException("Elige la cuenta a la que entró la plata.");
        m.setCuenta(cuenta);
        return movimientoRepository.save(m);
    }

    // ═══════════════════════════════════════════════════════════════
    // Ayudas
    // ═══════════════════════════════════════════════════════════════

    private void validarFecha(LocalDate fecha) {
        if (fecha == null) throw new IllegalArgumentException("Escribe la fecha en que entró la plata.");
        if (fecha.isAfter(ContabilidadServicio.hoy())) throw new IllegalArgumentException("La fecha no puede ser futura.");
        LocalDate inicio = contabilidadServicio.fechaInicio();
        if (inicio != null && fecha.isBefore(inicio)) {
            throw new IllegalArgumentException("La contabilidad empieza el " + inicio.format(FMT)
                    + ": la fecha del pago no puede ser anterior.");
        }
    }

    private static boolean esDeLaTienda(PedidoTienda p) {
        return p.getVendedor() != null && TiendaServicio.VENDEDOR_TIENDA.equalsIgnoreCase(p.getVendedor().trim());
    }

    static String textoEstadoFactura(String estado) {
        if (DatosVenta.FACTURADA.equals(estado)) return "Facturada";
        if (DatosVenta.FACTURA_NO_REQUIERE.equals(estado)) return "No requiere";
        return "Pendiente facturar";
    }

    static String textoInstalacion(String estado) {
        if (DatosVenta.INSTALADO.equals(estado)) return "Instalado";
        if (DatosVenta.ENTREGADO.equals(estado)) return "Entregado";
        return "No instalado";
    }

    /** "19", "2,5", "3.5 %" → número; vacío → porDefecto. */
    private static BigDecimal porcentaje(String valor, String nombre, BigDecimal porDefecto) {
        String t = texto(valor).replace("%", "").replace(",", ".").trim();
        if (t.isEmpty()) return porDefecto;
        BigDecimal n;
        try {
            n = new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nombre + " no es un porcentaje válido: \"" + valor + "\".");
        }
        if (n.signum() < 0) throw new IllegalArgumentException(nombre + " no puede ser negativo.");
        return n.setScale(2, RoundingMode.HALF_UP);
    }

    /** "$ 1.250.000" → 1250000; vacío → null. */
    private static BigDecimal plata(String valor, String nombre) {
        String t = texto(valor).replace("$", "").replace(" ", "").replace(".", "").replace(",", "").trim();
        if (t.isEmpty()) return null;
        BigDecimal n;
        try {
            n = new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nombre + " no es un valor válido: \"" + valor + "\".");
        }
        if (n.signum() < 0) throw new IllegalArgumentException(nombre + " no puede ser negativo.");
        return n;
    }

    private static BigDecimal pesos(BigDecimal v) { return nz(v).setScale(0, RoundingMode.HALF_UP); }

    private static String miles(BigDecimal v) {
        return String.format(Locale.ROOT, "%,d", pesos(v).longValue()).replace(',', '.');
    }

    private static String corto(String s, int max) {
        String t = texto(s).trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String texto(String s) { return s == null ? "" : s; }
}