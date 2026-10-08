package Colcones_Persinas.proyecto_express.modelo.contabilidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Una entrada o salida de plata.
 *   INGRESO  → entra plata a "cuenta" (ej. abono de un cliente a NEQUI)
 *   EGRESO   → sale plata de "cuenta" (ej. pago del arriendo desde BANCOLOMBIA)
 *   TRASLADO → pasa plata de "cuenta" a "cuentaDestino" (ej. consignar la CAJA en BANCOLOMBIA).
 *              No es ingreso ni gasto: solo cambia dónde está la plata.
 */
@Entity
@Table(name = "conta_movimiento", indexes = @Index(name = "idx_conta_movimiento_fecha", columnList = "fecha"))
@Getter @Setter @NoArgsConstructor
public class MovimientoContable {

    public static final String INGRESO = "INGRESO";
    public static final String EGRESO = "EGRESO";
    public static final String TRASLADO = "TRASLADO";

    public static final String ALMACEN = "ALMACEN";
    public static final String FABRICA = "FABRICA";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 10)
    private String tipo = EGRESO;

    /** De qué parte del negocio es: ALMACEN o FABRICA. */
    @Column(nullable = false, length = 10)
    private String area = ALMACEN;

    /** Vacía solo en los traslados. */
    @ManyToOne
    @JoinColumn(name = "subcategoria_id")
    private SubcategoriaContable subcategoria;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentaContable cuenta;

    /** Solo en los traslados: a dónde va la plata. */
    @ManyToOne
    @JoinColumn(name = "cuenta_destino_id")
    private CuentaContable cuentaDestino;

    @Column(nullable = false)
    private BigDecimal valor = BigDecimal.ZERO;

    /** A quién se le pagó o quién pagó. */
    @Column(length = 150)
    private String tercero = "";

    @Column(length = 500)
    private String descripcion = "";

    /** Si salió de un abono de Almacén, el número del pedido. */
    @Column(name = "pedido_tienda_id")
    private Integer pedidoTiendaId;

    @Column(name = "creado_por", length = 80)
    private String creadoPor = "";

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Transient
    public CategoriaContable getCategoria() {
        return subcategoria != null ? subcategoria.getCategoria() : null;
    }

    @Transient
    public String getTipoEtiqueta() {
        if (INGRESO.equals(tipo)) return "Ingreso";
        if (EGRESO.equals(tipo)) return "Egreso";
        return "Traslado";
    }

    @Transient
    public String getAreaEtiqueta() {
        return etiquetaArea(area);
    }

    @Transient
    public String getFechaFormateada() {
        return fecha != null ? fecha.format(FMT) : "";
    }

    public static String etiquetaArea(String area) {
        return FABRICA.equals(area) ? "Fábrica" : "Almacén";
    }
}