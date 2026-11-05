package Colcones_Persinas.proyecto_express.modelo.fabrica;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Precio de venta "estándar" que se puede cambiar a mano y queda guardado.
 *
 * Claves que se usan:
 *  - PERSIANA_1.83 / PERSIANA_2.50 / PERSIANA_3.00 → valor del m² de la persiana blackout
 *    (todo incluido), según el ancho del rollo que se usa.
 *  - TELA_1.83 / TELA_2.50 / TELA_3.00 → valor del m² cuando se vende SOLO la tela.
 *  - INSUMO_{id} → valor de un insumo en venta directa (por unidad, o por metro si se vende por medida).
 */
@Entity
@Table(name = "precio_venta_estandar")
@Getter @Setter @NoArgsConstructor
public class PrecioVentaEstandar {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true, length = 60)
    private String clave;

    private String nombre;

    /** Valor en pesos (por m², por unidad o por metro, según la clave). */
    private Double valor;

    @Column(name = "actualizado_por")
    private String actualizadoPor;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist @PreUpdate
    void alGuardar() {
        fechaActualizacion = LocalDateTime.now(ZoneId.of("America/Bogota"));
    }

    @Transient
    public String getFechaActualizacionFormateada() {
        return fechaActualizacion != null ? fechaActualizacion.format(FORMATO) : "";
    }
}