package Colcones_Persinas.proyecto_express.modelo.tienda;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compra hecha en la tienda virtual.
 *
 * Tiene DOS estados:
 *  - estado: el del PAGO (lo pone Wompi o Addi). Nace en PENDIENTE al ir a pagar y pasa a APROBADA
 *    cuando se confirma el pago.
 *  - estadoPedido: en qué va el pedido ya pagado (lo cambia la tienda a mano en "Pedidos"):
 *    Nuevo → En fabricación → Listo → Despachado → Entregado, o Cancelado.
 *    El cliente ve ese avance en la página de rastreo.
 *
 * La tienda es un módulo aparte: sus pedidos ya NO se crean en el listado de Almacén.
 * pedidoTiendaId solo queda en las compras viejas, que sí se crearon allá.
 */
@Entity
@Table(name = "tienda_orden")
@Getter @Setter @NoArgsConstructor
public class OrdenTienda {

    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    // ── Estado del PAGO ──
    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA  = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String ANULADA   = "ANULADA";
    public static final String ERROR     = "ERROR";

    // ── Estado del PEDIDO (solo para compras pagadas) ──
    public static final String NUEVO          = "NUEVO";
    public static final String EN_FABRICACION = "EN_FABRICACION";
    public static final String LISTO          = "LISTO";
    public static final String DESPACHADO     = "DESPACHADO";
    public static final String ENTREGADO      = "ENTREGADO";
    public static final String CANCELADO      = "CANCELADO";

    /** Los estados del pedido en el orden en que avanzan, con el nombre que se ve en pantalla. */
    public static final Map<String, String> ESTADOS_PEDIDO;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(NUEVO, "Nuevo");
        m.put(EN_FABRICACION, "En fabricación");
        m.put(LISTO, "Listo");
        m.put(DESPACHADO, "Despachado");
        m.put(ENTREGADO, "Entregado");
        m.put(CANCELADO, "Cancelado");
        ESTADOS_PEDIDO = java.util.Collections.unmodifiableMap(m);
    }

    /** Pedidos pagados que todavía hay que atender (ni entregados ni cancelados). */
    public static final List<String> ESTADOS_ACTIVOS = List.of(NUEVO, EN_FABRICACION, LISTO, DESPACHADO);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /** Referencia única que viaja a Wompi, ej: PCX261005143210-K7Q2M. */
    @Column(nullable = false, unique = true)
    private String referencia;

    @Column(nullable = false)
    private String estado = PENDIENTE;

    @Column(name = "nombre_cliente", nullable = false) private String nombreCliente = "";
    private String cedula = "";
    private String email = "";
    private String telefono = "";
    private String direccion = "";
    private String ciudad = "";
    @Column(length = 1000) private String notas = "";

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "wompi_transaccion_id") private String wompiTransaccionId;
    @Column(name = "metodo_pago")          private String metodoPago;

    @Column(name = "fecha_creacion") private LocalDateTime fechaCreacion;
    @Column(name = "fecha_pago")     private LocalDateTime fechaPago;

    /** Solo en compras viejas: el pedido que se creó en el listado de Almacén. Las nuevas no lo usan. */
    @Column(name = "pedido_tienda_id") private Integer pedidoTiendaId;

    /**
     * En qué va el pedido pagado (NUEVO, EN_FABRICACION, LISTO, DESPACHADO, ENTREGADO o CANCELADO).
     * Vacío mientras la compra no esté pagada.
     */
    @Column(name = "estado_pedido", length = 20) private String estadoPedido;

    /** Cuándo se le cambió por última vez el estado del pedido. */
    @Column(name = "fecha_estado_pedido") private LocalDateTime fechaEstadoPedido;

    /** Transportadora y número de guía cuando se despacha (opcional). El cliente lo ve en el rastreo. */
    @Column(name = "guia_envio", length = 200) private String guiaEnvio;

    /**
     * true en las compras de antes de separar la tienda cuya plata ya quedó anotada en la
     * contabilidad de Almacén: no se vuelven a anotar en la contabilidad de la tienda.
     */
    @Column(name = "venta_en_conta_almacen") private Boolean ventaEnContaAlmacen;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ItemOrdenTienda> items = new ArrayList<>();

    @PrePersist
    protected void alCrear() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now(ZONA_COLOMBIA);
    }

    public void agregarItem(ItemOrdenTienda item) {
        item.setOrden(this);
        items.add(item);
    }

    /** Wompi trabaja en centavos: $161.400 → 16140000. */
    @Transient
    public long getTotalEnCentavos() {
        return total.movePointRight(2).longValueExact();
    }

    @Transient
    public boolean isPagada() {
        return APROBADA.equals(estado);
    }

    @Transient
    public String getEstadoEtiqueta() {
        switch (estado) {
            case APROBADA:  return "Pagada";
            case RECHAZADA: return "Rechazada";
            case ANULADA:   return "Anulada";
            case ERROR:     return "Con error";
            default:        return "Esperando pago";
        }
    }

    /** "Nuevo", "En fabricación"... Si es una compra pagada sin estado todavía, cuenta como Nuevo. */
    @Transient
    public String getEstadoPedidoEtiqueta() {
        return ESTADOS_PEDIDO.getOrDefault(getEstadoPedidoActual(), "");
    }

    /** El estado del pedido; una compra pagada sin estado guardado cuenta como NUEVO. */
    @Transient
    public String getEstadoPedidoActual() {
        if (!isPagada()) return "";
        return estadoPedido == null || estadoPedido.isBlank() ? NUEVO : estadoPedido;
    }

    /**
     * Paso que ve el cliente en el rastreo: 1 Nuevo (pago recibido), 2 En fabricación, 3 Listo,
     * 4 Despachado, 5 Entregado. Cancelado o sin pagar → 0.
     */
    @Transient
    public int getPasoPedido() {
        switch (getEstadoPedidoActual()) {
            case NUEVO:          return 1;
            case EN_FABRICACION: return 2;
            case LISTO:          return 3;
            case DESPACHADO:     return 4;
            case ENTREGADO:      return 5;
            default:             return 0;
        }
    }

    /** ¿Su plata quedó en la contabilidad de Almacén (compra de antes de separar la tienda)? */
    @Transient
    public boolean isAnotadaEnAlmacen() {
        return Boolean.TRUE.equals(ventaEnContaAlmacen);
    }

    @Transient
    public boolean isCancelada() {
        return CANCELADO.equals(getEstadoPedidoActual());
    }

    /** ¿Está pagado, no cancelado y tiene productos de Dropi que todavía no se han pedido allá? */
    @Transient
    public boolean isPorPedirEnDropi() {
        return isPagada() && !isCancelada() && items.stream().anyMatch(ItemOrdenTienda::isPorPedirEnDropi);
    }

    @Transient
    public String getPrimerNombre() {
        if (nombreCliente == null || nombreCliente.isBlank()) return "";
        return nombreCliente.trim().split("\\s+")[0];
    }

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", new Locale("es", "CO"));

    @Transient
    public String getFechaCreacionFormateada() {
        return fechaCreacion != null ? fechaCreacion.format(FMT) : "";
    }

    @Transient
    public String getFechaPagoFormateada() {
        return fechaPago != null ? fechaPago.format(FMT) : "";
    }

    @Transient
    public String getFechaEstadoPedidoFormateada() {
        return fechaEstadoPedido != null ? fechaEstadoPedido.format(FMT) : "";
    }
}