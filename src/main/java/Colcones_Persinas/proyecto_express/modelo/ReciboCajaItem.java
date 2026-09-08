package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "recibo_caja_item")
@Getter @Setter
public class ReciboCajaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String nombre = "";
    private BigDecimal precio = BigDecimal.ZERO;
    private int cantidad = 1;

    @ManyToOne
    @JoinColumn(name = "recibo_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ReciboCaja recibo;

    @Transient
    public BigDecimal getTotalLinea() {
        return precio.multiply(BigDecimal.valueOf(cantidad));
    }
}