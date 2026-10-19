package Colcones_Persinas.proyecto_express.modelo.contabilidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Algo que se debe pagar (a la fábrica, a un proveedor, una cuota…).
 * Se puede pagar de una vez o por partes (abonos): cada pago crea un egreso en Movimientos
 * con el número de esta deuda (MovimientoContable.porPagarId). Cuando lo abonado llega al valor,
 * la deuda queda pagada.
 */
@Entity
@Table(name = "conta_por_pagar")
@Getter @Setter @NoArgsConstructor
public class CuentaPorPagar {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, length = 10)
    private String area = MovimientoContable.ALMACEN;

    /** A quién se le debe. */
    @Column(nullable = false, length = 150)
    private String acreedor = "";

    @Column(length = 300)
    private String concepto = "";

    @Column(nullable = false)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    private boolean pagada;

    /** Fecha del pago con el que se terminó de pagar. */
    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    /** El último egreso con el que se pagó (en deudas viejas, el único). */
    @Column(name = "movimiento_id")
    private Integer movimientoId;

    /**
     * Cuánto se ha pagado ya (suma de los abonos). Lo calcula ContabilidadServicio.recalcularDeuda.
     * Vacío en las deudas de antes de los abonos: ahí se toma todo (si está pagada) o nada.
     */
    private BigDecimal abonado;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    /** Lo pagado hasta hoy. */
    public BigDecimal getAbonado() {
        if (abonado != null) return abonado;
        return pagada && valor != null ? valor : BigDecimal.ZERO;
    }

    /** Lo que falta por pagar (nunca negativo). */
    @Transient
    public BigDecimal getSaldoPendiente() {
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        BigDecimal falta = v.subtract(getAbonado());
        return falta.signum() > 0 ? falta : BigDecimal.ZERO;
    }

    /** ¿Ya tiene algún abono? (entonces no se puede eliminar sin borrar antes los abonos) */
    @Transient
    public boolean isConAbonos() {
        return getAbonado().signum() > 0;
    }

    @Transient
    public boolean isVencida() {
        return !pagada && fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now(ZoneId.of("America/Bogota")));
    }

    @Transient
    public String getFechaVencimientoFormateada() {
        return fechaVencimiento != null ? fechaVencimiento.format(FMT) : "";
    }

    @Transient
    public String getFechaPagoFormateada() {
        return fechaPago != null ? fechaPago.format(FMT) : "";
    }

    @Transient
    public String getAreaEtiqueta() {
        return MovimientoContable.etiquetaArea(area);
    }
}