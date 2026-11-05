package Colcones_Persinas.proyecto_express.modelo.recibos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Una línea de producto de un recibo de la sede Sabana. */
@Entity
@Table(name = "recibo_sabana_item")
@Getter @Setter @NoArgsConstructor
public class ReciboSabanaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(length = 200)
    private String nombre = "";

    @Column(precision = 15, scale = 2)
    private BigDecimal precio = BigDecimal.ZERO;

    private int cantidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recibo_id")
    private ReciboSabana recibo;

    /** Precio × cantidad. */
    public BigDecimal getTotalLinea() {
        return (precio != null ? precio : BigDecimal.ZERO).multiply(BigDecimal.valueOf(cantidad));
    }
}