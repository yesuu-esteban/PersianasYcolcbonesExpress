package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Retazo de tela suelto. A diferencia de un rollo, tanto el ancho como el
 * alto son medidas variables: son sobrantes de cortes anteriores y pueden
 * llegar en cualquier tamaño, no solo en los anchos comerciales (1.83 / 2.50 / 3.00).
 */
@Entity
@Table(name = "retazo_tela")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetazoTela {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String color = "";

    /** Ancho disponible del retazo en metros (medida variable, no comercial). */
    private double ancho = 0.0;

    /** Alto disponible del retazo en metros */
    private double alto = 0.0;

    @Column(name = "fecha_ingreso")
    private LocalDateTime fechaIngreso;

    @PrePersist
    protected void alCrear() {
        if (this.fechaIngreso == null) {
            this.fechaIngreso = LocalDateTime.now();
        }
    }

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transient
    public String getFechaIngresoFormateada() {
        return this.fechaIngreso != null ? this.fechaIngreso.format(FORMATO) : "";
    }

    @Transient
    public boolean isAgotado() {
        return this.alto <= 0.001;
    }

    /** Área disponible del retazo en metros cuadrados (ancho x alto). */
    @Transient
    public double getArea() {
        return Math.round(this.ancho * this.alto * 1000.0) / 1000.0;
    }
}