package Colcones_Persinas.proyecto_express.modelo.tienda;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Una cuenta de la contabilidad de la TIENDA VIRTUAL (aparte de la contabilidad de Almacén):
 * un banco, una billetera o una caja donde entra o sale plata de la tienda.
 * Su saldo = saldo inicial + ingresos − egresos.
 *
 * recibeWompi / recibeAddi marcan a qué cuenta entra sola la plata de las compras pagadas
 * con cada medio. Son Boolean (no boolean) para que las columnas nuevas no fallen.
 */
@Entity
@Table(name = "tienda_cuenta")
@Getter @Setter @NoArgsConstructor
public class CuentaTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre = "";

    /** Lo que había en la cuenta el día que se empezó a llevar la contabilidad de la tienda. */
    @Column(name = "saldo_inicial", nullable = false)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    private int orden;
    private boolean activa = true;

    @Column(name = "recibe_wompi") private Boolean recibeWompi;
    @Column(name = "recibe_addi")  private Boolean recibeAddi;

    /** ¿Aquí entra la plata de las compras pagadas con Wompi (PSE, Nequi, tarjeta...)? */
    public boolean isDeWompi() { return Boolean.TRUE.equals(recibeWompi); }

    /** ¿Aquí entra la plata de las compras pagadas a cuotas con Addi? */
    public boolean isDeAddi() { return Boolean.TRUE.equals(recibeAddi); }
}