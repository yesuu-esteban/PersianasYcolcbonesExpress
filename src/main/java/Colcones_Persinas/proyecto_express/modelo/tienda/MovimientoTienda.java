package Colcones_Persinas.proyecto_express.modelo.tienda;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Un ingreso o un gasto de la contabilidad de la TIENDA VIRTUAL.
 *
 * Las ventas pagadas en línea se anotan solas (ordenId = la compra). Los demás
 * (gastos de fabricación, envíos, publicidad, comisiones...) se anotan a mano.
 */
@Entity
@Table(name = "tienda_movimiento", indexes = {
        @Index(name = "idx_tienda_mov_fecha", columnList = "fecha"),
        @Index(name = "idx_tienda_mov_orden", columnList = "orden_id")
})
@Getter @Setter @NoArgsConstructor
public class MovimientoTienda {

    public static final String INGRESO = "INGRESO";
    public static final String EGRESO  = "EGRESO";

    /** Categoría con la que se anotan solas las ventas de la tienda. */
    public static final String VENTA_EN_LINEA = "Venta en línea";

    public static final List<String> CATEGORIAS_INGRESO = List.of(VENTA_EN_LINEA, "Aporte de los dueños", "Otro ingreso");
    /** Las de ingreso que se pueden elegir a mano ("Venta en línea" se anota sola). */
    public static final List<String> CATEGORIAS_INGRESO_A_MANO = List.of("Aporte de los dueños", "Otro ingreso");
    public static final List<String> CATEGORIAS_EGRESO = List.of(
            "Fabricación", "Materiales", "Envíos", "Instalación", "Publicidad",
            "Comisiones Wompi / Addi", "Devoluciones", "Retiro de los dueños", "Otro gasto");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private LocalDate fecha;

    /** INGRESO o EGRESO. */
    @Column(nullable = false, length = 10)
    private String tipo = INGRESO;

    @Column(nullable = false, length = 60)
    private String categoria = "";

    @Column(length = 300)
    private String descripcion = "";

    /** Siempre positivo; el tipo dice si suma o resta. */
    @Column(nullable = false)
    private BigDecimal valor = BigDecimal.ZERO;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentaTienda cuenta;

    /** La compra de la tienda de la que salió este ingreso (solo en las ventas). */
    @Column(name = "orden_id")
    private Integer ordenId;

    @Column(length = 80)
    private String usuario = "";

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;

    @PrePersist
    protected void alCrear() {
        if (fechaRegistro == null) fechaRegistro = LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA);
    }

    public boolean isIngreso() { return INGRESO.equals(tipo); }

    /** El valor con signo: positivo si es ingreso, negativo si es gasto. */
    public BigDecimal getValorConSigno() {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor;
        return isIngreso() ? v : v.negate();
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String getFechaFormateada() {
        return fecha != null ? fecha.format(FMT) : "";
    }
}