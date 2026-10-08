package Colcones_Persinas.proyecto_express.modelo.contabilidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Subcategoría del plan de cuentas (ej. "ARRIENDO" dentro de "GASTOS ADMINISTRATIVOS"). */
@Entity
@Table(name = "conta_subcategoria",
        uniqueConstraints = @UniqueConstraint(columnNames = {"categoria_id", "nombre"}))
@Getter @Setter @NoArgsConstructor
public class SubcategoriaContable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaContable categoria;

    @Column(nullable = false, length = 120)
    private String nombre = "";

    private int orden;
    private boolean activa = true;

    public SubcategoriaContable(CategoriaContable categoria, String nombre, int orden) {
        this.categoria = categoria;
        this.nombre = nombre;
        this.orden = orden;
    }
}