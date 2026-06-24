package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pieza física individual de un insumo que se mide por longitud
 * (tubo, cuerda, pesa). Funciona igual que RolloTela: cada pieza
 * tiene su propio largo restante y se va descontando de forma
 * independiente conforme se usa en los pedidos.
 *
 * Solo existen piezas para los Insumo cuyo tieneMedida = true.
 */
@Entity
@Table(name = "pieza_insumo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PiezaInsumo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    /** Largo con el que ingresó esta pieza/barra específica (lo define el jefe al cargarla). */
    @Column(name = "largo_inicial")
    private double largoInicial = 0.0;

    /** Largo que queda disponible para cortar de esta pieza. */
    @Column(name = "largo_restante")
    private double largoRestante = 0.0;

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
    public boolean isAgotada() {
        return this.largoRestante <= 0.001;
    }
}