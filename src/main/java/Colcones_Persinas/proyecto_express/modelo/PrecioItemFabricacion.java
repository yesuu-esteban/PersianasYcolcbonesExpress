package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Precio unitario de un ítem/material que entra en el costo REAL de
 * fabricación (distinto del "Costo Fábrica" que ya existía, el cual en
 * realidad es el precio de VENTA al distribuidor). Cada fila representa
 * un componente: Tela, Tubo, Pesa, Cuerda, Mecanismo, Tapas, Pitillo,
 * etc. Se pueden agregar más filas a futuro (Tubo R24, Control, Cabezal,
 * Terminal, Soportes...) sin tocar código: solo desde esta pantalla.
 */
@Entity
@Table(name = "precio_item_fabricacion")
@Getter @Setter
public class PrecioItemFabricacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /** Nombre visible en pantalla, ej: "Tela", "Tubo R16", "Pesa". */
    @Column(nullable = false)
    private String nombre;

    /** Nota libre para aclarar a qué aplica (opcional). */
    private String descripcion = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_calculo", nullable = false)
    private TipoCalculoPrecio tipoCalculo = TipoCalculoPrecio.FIJO;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario = BigDecimal.ZERO;

    @Column(name = "actualizado_por")
    private String actualizadoPor = "";

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    @PreUpdate
    protected void alGuardar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transient
    public String getFechaActualizacionFormateada() {
        return this.fechaActualizacion != null ? this.fechaActualizacion.format(FMT) : "";
    }
}