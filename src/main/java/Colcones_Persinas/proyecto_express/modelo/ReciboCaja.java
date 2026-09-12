package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recibo_caja")
@Getter @Setter
public class ReciboCaja {

    /** Zona horaria fija de Colombia, sin importar dónde esté físicamente el servidor. */
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /** "TIENDA" (Almacén) o "FABRICA": desde qué módulo se generó el recibo. */
    @Column(nullable = false)
    private String origen = "TIENDA";

    @Column(name = "creado_por")
    private String creadoPor = "";

    private String cliente = "";
    private String direccion = "";
    private String cedula = "";
    private String telefono = "";

    @Column(name = "fecha")
    private LocalDateTime fecha;

    /** Suma bruta de todas las líneas (cantidad × precio), ANTES del descuento. */
    private BigDecimal total = BigDecimal.ZERO;

    /** Descuento que la persona que hace el recibo decide aplicarle (jefe, cajero, etc.). */
    @Column(name = "descuento")
    private BigDecimal descuento = BigDecimal.ZERO;

    private BigDecimal abono = BigDecimal.ZERO;

    /** Saldo pendiente = (total - descuento) - abono. Se recalcula siempre en el controlador. */
    private BigDecimal saldo = BigDecimal.ZERO;

    private String fabrica = "";
    private String vendedor = "";
    private String aliado = "";

    /**
     * Número que se muestra al usuario ("000", "001", "002"...). NO se guarda
     * en la base de datos: se calcula cada vez que se necesita, según cuántos
     * recibos con un id MENOR siguen existiendo en este momento.
     */
    @Transient
    private int numeroMostrado = 0;

    /** Imagen PNG de la firma, en base64 (data URL completo: "data:image/png;base64,..."). */
    @Column(name = "firma", columnDefinition = "TEXT")
    private String firma;

    /** Fecha en que se registró/actualizó la firma. Null si el recibo aún no está firmado. */
    @Column(name = "firma_fecha")
    private LocalDateTime firmaFecha;

    @OneToMany(mappedBy = "recibo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReciboCajaItem> items = new ArrayList<>();

    @PrePersist
    protected void alCrear() {
        if (this.fecha == null) this.fecha = LocalDateTime.now(ZONA_COLOMBIA);
    }

    public void agregarItem(ReciboCajaItem item) {
        items.add(item);
        item.setRecibo(this);
    }

    @Transient
    public boolean isFirmado() {
        return this.firma != null && !this.firma.isBlank();
    }

    /**
     * Total real a cobrar después de aplicar el descuento. Nunca queda negativo:
     * si por error el descuento supera el total, simplemente queda en 0.
     */
    @Transient
    public BigDecimal getTotalConDescuento() {
        BigDecimal t = this.total != null ? this.total : BigDecimal.ZERO;
        BigDecimal d = this.descuento != null ? this.descuento : BigDecimal.ZERO;
        BigDecimal resultado = t.subtract(d);
        return resultado.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : resultado;
    }

    /** Versión formateada con ceros a la izquierda, mínimo 3 dígitos: 000, 001, 002... */
    @Transient
    public String getNumeroFormateado() {
        return String.format("%03d", Math.max(this.numeroMostrado, 0));
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transient
    public String getFechaFormateada() {
        return this.fecha != null ? this.fecha.format(FMT) : "";
    }

    @Transient
    public String getFirmaFechaFormateada() {
        return this.firmaFecha != null ? this.firmaFecha.format(FMT) : "";
    }
}