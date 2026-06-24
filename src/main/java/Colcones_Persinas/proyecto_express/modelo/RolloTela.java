package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Representa un rollo físico de tela en inventario.
 * Todos los rollos nuevos ingresan con 30.0 m de largo.
 * El ancho solo puede ser 1.83, 2.50 o 3.00 (los anchos comerciales disponibles).
 * Cada rollo se va consumiendo de forma independiente: cuando se corta tela para
 * un pedido, se descuenta del largoRestante de UN rollo específico (el elegido
 * automáticamente por el algoritmo, o el que el jefe seleccione manualmente,
 * por ejemplo un retazo con poco sobrante).
 */
@Entity
@Table(name = "rollo_tela")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RolloTela {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String color = "";

    /** Ancho comercial del rollo: 1.83, 2.50 o 3.00 */
    private double ancho = 0.0;

    /** Largo total con el que ingresó el rollo (siempre 30.0 al crear uno nuevo). */
    private double largoInicial = 30.0;

    /** Largo que queda disponible para cortar. Se va descontando con cada pedido. */
    private double largoRestante = 30.0;

    @Column(name = "fecha_ingreso")
    private LocalDateTime fechaIngreso;

    @PrePersist
    protected void alCrear() {
        if (this.fechaIngreso == null) {
            this.fechaIngreso = LocalDateTime.now();
        }
        if (this.largoRestante == 0.0 && this.largoInicial > 0.0) {
            this.largoRestante = this.largoInicial;
        }
    }

    @Transient
    public boolean isAgotado() {
        return this.largoRestante <= 0.001;
    }

    /**
     * Un rollo se considera "retazo" cuando le queda relativamente poco material.
     * Umbral fijado en 3 metros: por debajo de eso ya no es práctico para
     * pedidos grandes, pero puede servir para pedidos pequeños.
     */
    @Transient
    public boolean isRetazo() {
        return !isAgotado() && this.largoRestante < 3.0;
    }
}