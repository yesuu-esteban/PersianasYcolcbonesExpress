package Colcones_Persinas.proyecto_express.modelo.tienda;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Una línea de una compra de la tienda. Guarda los nombres y precios tal como
 * estaban al momento de comprar (si luego cambian los precios, la orden no cambia).
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
    @Column(nullable = false) private int cantidad = 1;
    private Double m2;
    private Double rollo;
    @Column(name = "precio_unitario", nullable = false) private BigDecimal precioUnitario = BigDecimal.ZERO;
    @Column(nullable = false) private BigDecimal subtotal = BigDecimal.ZERO;

    /**
     * Las medidas se guardan en centímetros, pero siempre se muestran en metros,
     * como en el resto del negocio. Ej: 120 → "1,20".
     */
    public static String enMetros(int cm) {
        return String.format(Locale.ROOT, "%.2f", cm / 100.0).replace('.', ',');
    }

    /** Ej: "Blackout liso, color Gris, 1,50 × 2,00 m, mando a la derecha". */
    @Transient
    public String getDetalle() {
        StringBuilder sb = new StringBuilder();
        if (telaNombre != null && !telaNombre.isBlank()) sb.append(telaNombre);
        if (color != null && !color.isBlank()) sb.append(sb.length() > 0 ? ", " : "").append("color ").append(color);
        if (anchoCm != null && altoCm != null) {
            sb.append(sb.length() > 0 ? ", " : "").append(enMetros(anchoCm)).append(" × ").append(enMetros(altoCm)).append(" m");
        }
        if (ladoMando != null && !ladoMando.isBlank()) {
            sb.append(sb.length() > 0 ? ", " : "").append("mando a la ").append(ladoMando.toLowerCase());
        }
        return sb.toString();
    }
}