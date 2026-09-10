package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Bitácora de movimientos de inventario: cada vez que se agrega material
 * (rollo, retazo, insumo) o se elimina automáticamente por quedar demasiado
 * pequeño para servir, queda un registro aquí. Es de solo lectura desde la
 * interfaz — nunca se edita, solo se consulta.
 */
@Entity
@Table(name = "movimiento_inventario")
@Getter @Setter
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /** "ENTRADA" (algo se agregó) o "LIMPIEZA_AUTOMATICA" (se eliminó por ser muy pequeño). */
    @Column(nullable = false)
    private String tipoMovimiento;

    /** "ROLLO", "RETAZO", "INSUMO_PIEZA", "INSUMO_UNIDAD". */
    @Column(nullable = false)
    private String categoria;

    /** Texto legible, ej: "Rollo Blanco 1.83m x30m" o "Retazo Gris 1.2m x 0.8m (eliminado, quedaba 0.03m)". */
    @Column(length = 500)
    private String descripcion;

    @Column(name = "creado_por")
    private String creadoPor = "sistema";

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @PrePersist
    protected void alCrear() {
        if (this.fecha == null) this.fecha = LocalDateTime.now();
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transient
    public String getFechaFormateada() {
        return this.fecha != null ? this.fecha.format(FMT) : "";
    }
}