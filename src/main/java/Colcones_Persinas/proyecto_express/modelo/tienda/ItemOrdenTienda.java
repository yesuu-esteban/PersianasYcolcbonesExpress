package Colcones_Persinas.proyecto_express.modelo.tienda;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Una línea de una compra de la tienda. Guarda los nombres y precios tal como
 * estaban al momento de comprar (si luego cambian los precios, la orden no cambia).
 *
 * Si el producto es de Dropi, también guarda su código y su costo en Dropi, y el número
 * de pedido o guía que se anota cuando ya se pidió allá.
 */
@Entity
@Table(name = "tienda_orden_item")
@Getter @Setter @NoArgsConstructor
public class ItemOrdenTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "orden_id", nullable = false)
    private OrdenTienda orden;

    @Column(name = "producto_id") private Integer productoId;
    @Column(name = "producto_nombre", nullable = false) private String productoNombre = "";
    @Column(name = "tela_nombre") private String telaNombre = "";
    private String color = "";
    @Column(name = "ancho_cm") private Integer anchoCm;
    @Column(name = "alto_cm")  private Integer altoCm;
    @Column(name = "lado_mando") private String ladoMando = "";
    /** Pedido con cabezal (Boolean para que la columna nueva acepte las compras que ya existían). */
    @Column(name = "con_cabezal") private Boolean conCabezal;
    /** Enrollado al contrario: la tela cae por delante del tubo. */
    @Column(name = "enrollado_contrario") private Boolean enrolladoContrario;
    /** Riel de onda serena: "con bastón" o "con control". */
    @Column(name = "sistema_riel") private String sistemaRiel;
    /** Riel de onda serena: hacia dónde abre (Izquierda, Derecha, Hacia los extremos). */
    @Column(name = "apertura_riel") private String aperturaRiel;
    @Column(nullable = false) private int cantidad = 1;
    private Double m2;
    private Double rollo;
    @Column(name = "precio_unitario", nullable = false) private BigDecimal precioUnitario = BigDecimal.ZERO;
    @Column(nullable = false) private BigDecimal subtotal = BigDecimal.ZERO;

    // ── Productos de Dropi ──
    /** DROPI si el producto lo despacha Dropi; vacío si es nuestro. */
    @Column(name = "proveedor", length = 20) private String proveedor;
    @Column(name = "codigo_proveedor", length = 80) private String codigoProveedor;
    /** Lo que costaba cada unidad en Dropi al momento de la compra. */
    @Column(name = "costo_proveedor") private BigDecimal costoProveedor;
    @Column(name = "enlace_proveedor", length = 500) private String enlaceProveedor;
    /** Número de pedido o guía en Dropi. Vacío = todavía no se ha pedido allá. */
    @Column(name = "pedido_proveedor", length = 200) private String pedidoProveedor;
    @Column(name = "fecha_pedido_proveedor") private LocalDateTime fechaPedidoProveedor;

    /**
     * Las medidas se guardan en centímetros, pero siempre se muestran en metros,
     * como en el resto del negocio. Ej: 120 → "1,20".
     */
    public static String enMetros(int cm) {
        return String.format(Locale.ROOT, "%.2f", cm / 100.0).replace('.', ',');
    }

    /** Texto que se le muestra al cliente y que va al pedido de Almacén cuando la persiana lleva cabezal. */
    public static final String TEXTO_CABEZAL = "con cabezal";
    /** Texto para la persiana enrollada al contrario. */
    public static final String TEXTO_CONTRARIO = "enrollado al contrario (tela por delante)";

    /** "Izquierda" → "abre hacia la izquierda"; "Hacia los extremos" → "abre hacia los extremos". */
    public static String textoApertura(String apertura) {
        if (apertura == null || apertura.isBlank()) return "";
        if (apertura.toLowerCase().startsWith("hacia")) return "abre " + apertura.toLowerCase();
        return "abre hacia la " + apertura.toLowerCase();
    }

    /** ¿Este producto lo despacha Dropi? */
    @Transient
    public boolean isDeDropi() {
        return ProductoTienda.PROVEEDOR_DROPI.equals(proveedor);
    }

    /** ¿Es de Dropi y todavía no se ha pedido allá? */
    @Transient
    public boolean isPorPedirEnDropi() {
        return isDeDropi() && (pedidoProveedor == null || pedidoProveedor.isBlank());
    }

    /** Lo que cuesta en Dropi toda la línea (costo por unidad × cantidad). Null si no se sabe. */
    @Transient
    public BigDecimal getCostoProveedorTotal() {
        return costoProveedor == null ? null : costoProveedor.multiply(BigDecimal.valueOf(cantidad));
    }

    /** Ganancia aproximada de la línea: lo que pagó el cliente − lo que cuesta en Dropi (sin contar envío). */
    @Transient
    public BigDecimal getGananciaProveedor() {
        BigDecimal costo = getCostoProveedorTotal();
        return costo == null || subtotal == null ? null : subtotal.subtract(costo);
    }

    @Transient
    public String getFechaPedidoProveedorFormateada() {
        return fechaPedidoProveedor == null ? ""
                : fechaPedidoProveedor.format(DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", new Locale("es", "CO")));
    }

    /** Ej: "Blackout liso, color Gris, 1,50 × 2,00 m, mando a la derecha, con cabezal". */
    @Transient
    public String getDetalle() {
        StringBuilder sb = new StringBuilder();
        if (telaNombre != null && !telaNombre.isBlank()) sb.append(telaNombre);
        if (color != null && !color.isBlank()) sb.append(sb.length() > 0 ? ", " : "").append("color ").append(color);
        if (anchoCm != null && altoCm != null) {
            sb.append(sb.length() > 0 ? ", " : "").append(enMetros(anchoCm)).append(" × ").append(enMetros(altoCm)).append(" m");
        } else if (anchoCm != null) {
            sb.append(sb.length() > 0 ? ", " : "").append(enMetros(anchoCm)).append(" m de ancho");
        }
        if (sistemaRiel != null && !sistemaRiel.isBlank()) sb.append(sb.length() > 0 ? ", " : "").append(sistemaRiel);
        if (aperturaRiel != null && !aperturaRiel.isBlank()) sb.append(sb.length() > 0 ? ", " : "").append(textoApertura(aperturaRiel));
        if (ladoMando != null && !ladoMando.isBlank()) {
            sb.append(sb.length() > 0 ? ", " : "").append("mando a la ").append(ladoMando.toLowerCase());
        }
        if (Boolean.TRUE.equals(conCabezal)) sb.append(sb.length() > 0 ? ", " : "").append(TEXTO_CABEZAL);
        if (Boolean.TRUE.equals(enrolladoContrario)) sb.append(sb.length() > 0 ? ", " : "").append(TEXTO_CONTRARIO);
        return sb.toString();
    }
}