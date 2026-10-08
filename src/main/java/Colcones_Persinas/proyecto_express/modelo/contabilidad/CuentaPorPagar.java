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

/** Algo que se debe pagar (a la fábrica, a un proveedor, una cuota…). Al pagarlo se crea el egreso. */
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

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    /** El egreso que se creó al pagarla. */
    @Column(name = "movimiento_id")
    private Integer movimientoId;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

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