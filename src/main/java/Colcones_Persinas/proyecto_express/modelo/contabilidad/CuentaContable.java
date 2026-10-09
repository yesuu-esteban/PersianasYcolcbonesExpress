package Colcones_Persinas.proyecto_express.modelo.contabilidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Dónde está la plata: un banco, una billetera o una caja (ej. BANCOLOMBIA, NEQUI, CAJA).
 * Su saldo = saldo inicial + ingresos − egresos ± traslados.
 */
@Entity
@Table(name = "conta_cuenta")
@Getter @Setter @NoArgsConstructor
public class CuentaContable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre = "";

    /** Lo que había en la cuenta cuando se empezó a llevar la contabilidad en el sistema. */
    @Column(name = "saldo_inicial", nullable = false)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    private int orden;
    private boolean activa = true;

    /**
     * true en la cuenta a la que entra la plata de la tienda virtual (Wompi y Addi).
     * Solo una cuenta la tiene. Es Boolean (no boolean) para que la columna nueva acepte
     * las cuentas que ya existían en la base de datos.
     */
    @Column(name = "recibe_tienda")
    private Boolean recibeTienda;

    /** ¿Aquí entra la plata de la tienda virtual? */
    public boolean isDeLaTienda() {
        return Boolean.TRUE.equals(recibeTienda);
    }

    public CuentaContable(String nombre, int orden) {
        this.nombre = nombre;
        this.orden = orden;
    }
}