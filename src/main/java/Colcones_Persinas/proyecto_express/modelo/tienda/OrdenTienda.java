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
import java.util.List;
import java.util.Locale;

/**
 * Compra hecha en la tienda virtual. Nace en PENDIENTE al ir a pagar; cuando Wompi
 * confirma el pago pasa a APROBADA y se crea automáticamente un pedido en el
 * listado de Almacén (pedidoTiendaId).
 */
@Entity
@Table(name = "tienda_orden")
@Getter @Setter @NoArgsConstructor
public class OrdenTienda {

    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA  = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String ANULADA   = "ANULADA";
    public static final String ERROR     = "ERROR";

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

    /** Pedido creado en el listado de Almacén cuando el pago se aprueba. */
    @Column(name = "pedido_tienda_id") private Integer pedidoTiendaId;

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
    public String getEstadoEtiqueta() {
        switch (estado) {
            case APROBADA:  return "Pagada";
            case RECHAZADA: return "Rechazada";
            case ANULADA:   return "Anulada";
            case ERROR:     return "Con error";
            default:        return "Esperando pago";
        }
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
}