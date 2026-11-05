package Colcones_Persinas.proyecto_express.modelo.recibos;

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

/**
 * Recibo de caja de la sede Sabana (La Calera). Va en su propia tabla y lleva su
 * propia numeración (0001, 0002…), aparte de los recibos de Almacén y de Fábrica.
 * Solo lo ve el administrador Fabián.
 */
@Entity
@Table(name = "recibo_sabana")
@Getter 
@Setter @NoArgsConstructor
public class ReciboSabana {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /** Número del recibo dentro de Sabana (1, 2, 3…). No cambia aunque se borren otros. */
    @Column(nullable = false)
    private int numero;

    @Column(length = 150)
    private String cliente = "";

    @Column(length = 200)
    private String direccion = "";

    @Column(length = 40)
    private String cedula = "";

    @Column(length = 40)
    private String telefono = "";

    /** Suma de los productos (antes del descuento). */
    @Column(precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal abono = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;

    private LocalDateTime fecha = LocalDateTime.now(ZONA);

    @Column(name = "creado_por", length = 80)
    private String creadoPor = "";

    /** Firma como imagen PNG en base64 (dibujada o escrita). */
    @Column(columnDefinition = "TEXT")
    private String firma;

    @Column(name = "firma_fecha")
    private LocalDateTime firmaFecha;

    @OneToMany(mappedBy = "recibo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ReciboSabanaItem> items = new ArrayList<>();

    public void agregarItem(ReciboSabanaItem item) {
        item.setRecibo(this);
        items.add(item);
    }

    /** 1 → "0001". */
    public String getNumeroFormateado() {
        return String.format("%04d", numero);
    }

    public String getFechaFormateada() {
        return fecha != null ? fecha.format(FMT) : "";
    }

    public String getFirmaFechaFormateada() {
        return firmaFecha != null ? firmaFecha.format(FMT) : "";
    }

    public boolean isFirmado() {
        return firma != null && !firma.isBlank();
    }

    /** Total menos el descuento. */
    public BigDecimal getTotalConDescuento() {
        BigDecimal t = total != null ? total : BigDecimal.ZERO;
        BigDecimal d = descuento != null ? descuento : BigDecimal.ZERO;
        return t.subtract(d);
    }
}