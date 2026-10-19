package Colcones_Persinas.proyecto_express.modelo.contabilidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ajustes generales de la contabilidad (una sola fila, id = 1).
 *
 * fechaInicio: desde qué día cuenta la contabilidad. Todo lo anterior (movimientos, deudas por pagar,
 * pedidos por cobrar) queda guardado como historial, pero ya no suma en saldos ni resúmenes.
 * Vacía = la contabilidad cuenta desde siempre.
 */
@Entity
@Table(name = "conta_configuracion")
@Getter @Setter @NoArgsConstructor
public class ConfiguracionContable {

    public static final int ID = 1;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Id
    private int id = ID;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_cambio")
    private LocalDateTime fechaCambio;

    @Column(name = "cambiado_por", length = 80)
    private String cambiadoPor;

    @Transient
    public String getFechaInicioFormateada() {
        return fechaInicio != null ? fechaInicio.format(FMT) : "";
    }
}