package Colcones_Persinas.proyecto_express.servicio.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.MovimientoContable;
import Colcones_Persinas.proyecto_express.repository.almacen.PedidoTiendaVentasRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.MovimientoAbonosRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.MovimientoContableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Los pedidos de Almacén dentro de la contabilidad (pantalla "Ventas"):
 *  - Qué se vendió en un mes, cuánto costó en fábrica, la utilidad, lo abonado y lo que falta cobrar.
 *  - Qué abonos de esos pedidos todavía NO están anotados en contabilidad (por ejemplo, pedidos
 *    hechos antes de empezar la contabilidad), y anotarlos eligiendo la cuenta.
 *
 * Los abonos NUEVOS que se registran en Almacén se anotan solos (ver PedidoTiendaControlador).
 */
@Service
public class VentasAlmacenServicio {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PedidoTiendaVentasRepository pedidoRepository;
    private final MovimientoAbonosRepository abonosRepository;
    private final MovimientoContableRepository movimientoRepository;
    private final ContabilidadServicio contabilidadServicio;

    public VentasAlmacenServicio(PedidoTiendaVentasRepository pedidoRepository,
                                 MovimientoAbonosRepository abonosRepository,
                                 MovimientoContableRepository movimientoRepository,
                                 ContabilidadServicio contabilidadServicio) {
        this.pedidoRepository = pedidoRepository;
        this.abonosRepository = abonosRepository;
        this.movimientoRepository = movimientoRepository;
        this.contabilidadServicio = contabilidadServicio;
    }

    /** Un pedido de Almacén, ya con sus valores calculados (la pantalla no toca la base de datos). */
    public static class FilaVenta {
        private int id;
        private String fecha = "", fechaIso = "", cliente = "", cedula = "", vendedor = "", estado = "", estadoPago = "";
        private BigDecimal precio = BigDecimal.ZERO, costoFabrica = BigDecimal.ZERO, utilidad = BigDecimal.ZERO,
                abonado = BigDecimal.ZERO, saldo = BigDecimal.ZERO, enContabilidad = BigDecimal.ZERO, falta = BigDecimal.ZERO;

        public int getId() { return id; }
        public String getFecha() { return fecha; }
        /** La fecha del pedido como "2026-10-05", para el campo de fecha al anotar un abono. */
        public String getFechaIso() { return fechaIso; }
        public String getCliente() { return cliente; }
        public String getCedula() { return cedula; }
        public String getVendedor() { return vendedor; }
        public String getEstado() { return estado; }
        public String getEstadoPago() { return estadoPago; }
        public BigDecimal getPrecio() { return precio; }
        public BigDecimal getCostoFabrica() { return costoFabrica; }
        public BigDecimal getUtilidad() { return utilidad; }
        public BigDecimal getAbonado() { return abonado; }
        public BigDecimal getSaldo() { return saldo; }
        /** Lo que ya está anotado como ingreso en contabilidad para este pedido. */
        public BigDecimal getEnContabilidad() { return enContabilidad; }
        /** Abonos del pedido que todavía no están en contabilidad. */
        public BigDecimal getFalta() { return falta; }
        public boolean isFaltaAnotar() { return falta.signum() > 0; }
    }

    /** Los pedidos de un mes con sus totales. */
    public static class Ventas {
        private final List<FilaVenta> filas = new ArrayList<>();
        private BigDecimal vendido = BigDecimal.ZERO, costoFabrica = BigDecimal.ZERO, utilidad = BigDecimal.ZERO,
                abonado = BigDecimal.ZERO, saldo = BigDecimal.ZERO, faltaAnotar = BigDecimal.ZERO,
                abonosDelMes = BigDecimal.ZERO;
        private int pedidosSinAnotar;

        public List<FilaVenta> getFilas() { return filas; }
        public int getCantidad() { return filas.size(); }
        public BigDecimal getVendido() { return vendido; }
        public BigDecimal getCostoFabrica() { return costoFabrica; }
        public BigDecimal getUtilidad() { return utilidad; }
        public BigDecimal getAbonado() { return abonado; }
        public BigDecimal getSaldo() { return saldo; }
        /** Abonos de estos pedidos que no están en contabilidad. */
        public BigDecimal getFaltaAnotar() { return faltaAnotar; }
        public int getPedidosSinAnotar() { return pedidosSinAnotar; }
        /** Abonos de Almacén que entraron a las cuentas en el mes (de pedidos de este mes o de antes). */
        public BigDecimal getAbonosDelMes() { return abonosDelMes; }
    }

    /** Pedidos de Almacén hechos entre dos fechas (incluidas). */
    @Transactional(readOnly = true)
    public Ventas ventas(LocalDate desde, LocalDate hasta) {
        Ventas v = new Ventas();
        List<PedidoTienda> pedidos = pedidoRepository.findByFechaPedidoBetweenOrderByFechaPedidoDescIdDesc(
                desde.atStartOfDay(), hasta.atTime(23, 59, 59));

        Map<Integer, BigDecimal> anotado = new HashMap<>();
        if (!pedidos.isEmpty()) {
            List<Integer> ids = new ArrayList<>();
            for (PedidoTienda p : pedidos) ids.add(p.getId());
            for (Object[] fila : abonosRepository.anotadoPorPedido(ids)) {
                anotado.put(((Number) fila[0]).intValue(), nz((BigDecimal) fila[1]));
            }
        }

        for (PedidoTienda p : pedidos) {
            FilaVenta f = new FilaVenta();
            f.id = p.getId();
            if (p.getFechaPedido() != null) {
                f.fecha = p.getFechaPedido().format(FMT);
                f.fechaIso = p.getFechaPedido().toLocalDate().toString();
            }
            f.cliente = texto(p.getNombreCliente());
            f.cedula = texto(p.getCedula());
            f.vendedor = texto(p.getVendedor());
            f.estado = texto(p.getEstado());
            f.estadoPago = texto(p.getEstadoPago());
            f.precio = nz(p.getPrecioCliente());
            f.costoFabrica = nz(p.getCostoFabricaTotal());
            f.utilidad = nz(p.getUtilidadEstimada());
            f.abonado = nz(p.getAbono());
            f.saldo = nz(p.getSaldo());
            f.enContabilidad = anotado.getOrDefault(p.getId(), BigDecimal.ZERO);
            f.falta = f.abonado.subtract(f.enContabilidad).max(BigDecimal.ZERO);
            v.filas.add(f);

            v.vendido = v.vendido.add(f.precio);
            v.costoFabrica = v.costoFabrica.add(f.costoFabrica);
            v.utilidad = v.utilidad.add(f.utilidad);
            v.abonado = v.abonado.add(f.abonado);
            v.saldo = v.saldo.add(f.saldo);
            v.faltaAnotar = v.faltaAnotar.add(f.falta);
            if (f.isFaltaAnotar()) v.pedidosSinAnotar++;
        }
        v.abonosDelMes = nz(abonosRepository.abonosEntre(desde, hasta));
        return v;
    }

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
        if (fecha == null) throw new IllegalArgumentException("Escribe la fecha en que entró la plata.");
        if (fecha.isAfter(ContabilidadServicio.hoy())) throw new IllegalArgumentException("La fecha no puede ser futura.");

        BigDecimal falta = nz(p.getAbono()).subtract(nz(abonosRepository.anotadoDelPedido(pedidoId)));
        if (falta.signum() <= 0) {
            throw new IllegalArgumentException("El pedido #" + pedidoId + " ya tiene todos sus abonos en contabilidad.");
        }

        MovimientoContable m = contabilidadServicio.registrarAbonoAlmacen(p, falta, cuentaId, usuario);
        m.setFecha(fecha);
        m.setDescripcion("Abono pedido de Almacén #" + pedidoId + " (anotado desde Ventas)");
        return movimientoRepository.save(m);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String texto(String s) { return s == null ? "" : s; }
}