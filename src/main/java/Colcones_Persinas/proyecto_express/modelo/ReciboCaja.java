package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recibo_caja")
@Getter @Setter
public class ReciboCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /** "TIENDA" o "FABRICA": desde qué módulo se generó el recibo. */
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

    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal abono = BigDecimal.ZERO;
    private BigDecimal saldo = BigDecimal.ZERO;

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
        if (this.fecha == null) this.fecha = LocalDateTime.now();
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
     * Número visible del recibo, calculado directamente a partir del id.
     * El primer recibo (id=1) se muestra como "000", el segundo como "001", etc.
     */
    @Transient
    public int getNumero() {
        return Math.max(this.id - 1, 0);
    }

    /** Versión formateada con ceros a la izquierda, mínimo 3 dígitos: 000, 001, 002... */
    @Transient
    public String getNumeroFormateado() {
        return String.format("%03d", getNumero());
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