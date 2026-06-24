package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "retazo_tela")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetazoTela {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String color = "";

    /** Ancho del retazo en metros */
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
}