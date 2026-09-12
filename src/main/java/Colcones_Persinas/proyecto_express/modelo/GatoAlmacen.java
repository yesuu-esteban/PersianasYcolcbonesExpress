package Colcones_Persinas.proyecto_express.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "gato_almacen")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatoAlmacen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String categoria;

    @Builder.Default
    private String descripcion = "";

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal monto = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "registrado_por", nullable = false)
    @Builder.Default
    private String registradoPor = "";

    @PrePersist
    protected void alCrear() {
        if (this.fecha == null) {
            this.fecha = LocalDate.now();
        }
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transient
    public String getFechaFormateada() {
        return this.fecha != null ? this.fecha.format(FMT) : "";
    }
}