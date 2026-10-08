package Colcones_Persinas.proyecto_express.modelo.contabilidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Categoría del plan de cuentas (ej. "GASTOS ADMINISTRATIVOS").
 * La "clase" dice cómo cuenta en el resultado del mes:
 *   INGRESO → suma a los ingresos
 *   COSTO   → costo de venta (se resta para la utilidad bruta)
 *   GASTO   → gasto (se resta para la utilidad neta)
 *   OTRO    → sale plata pero no es gasto (compra de activos, pago de préstamos, retiros del dueño)
 */
@Entity
@Table(name = "conta_categoria")
@Getter @Setter @NoArgsConstructor
public class CategoriaContable {

    public static final String INGRESO = "INGRESO";
    public static final String COSTO = "COSTO";
    public static final String GASTO = "GASTO";
    public static final String OTRO = "OTRO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true, length = 120)
    private String nombre = "";

    @Column(nullable = false, length = 20)
    private String clase = GASTO;

    private int orden;
    private boolean activa = true;

    @OneToMany(mappedBy = "categoria")
    @OrderBy("orden ASC, nombre ASC")
    private List<SubcategoriaContable> subcategorias = new ArrayList<>();

    public CategoriaContable(String nombre, String clase, int orden) {
        this.nombre = nombre;
        this.clase = clase;
        this.orden = orden;
    }

    public boolean isDeIngreso() {
        return INGRESO.equals(clase);
    }

    public String getClaseEtiqueta() {
        return etiquetaClase(clase);
    }

    public static String etiquetaClase(String clase) {
        if (INGRESO.equals(clase)) return "Ingreso";
        if (COSTO.equals(clase)) return "Costo de venta";
        if (GASTO.equals(clase)) return "Gasto";
        return "No es gasto";
    }
}