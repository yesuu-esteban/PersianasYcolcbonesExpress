package Colcones_Persinas.proyecto_express.modelo;

import java.math.BigDecimal;

/**
 * Una línea del desglose del costo real de fabricación: qué material fue,
 * la fórmula exacta que se aplicó (para que se vea transparente cómo se
 * llegó al número) y el valor resultante. Si el ítem de precio todavía
 * no existe en /inventario/precios, "disponible" queda en false y el
 * valor en 0, para no inventar un costo que no se ha definido.
 */
public class LineaCostoFabricacion {

    private final String nombre;
    private final String formula;
    private final BigDecimal valor;
    private final boolean disponible;

    public LineaCostoFabricacion(String nombre, String formula, BigDecimal valor, boolean disponible) {
        this.nombre = nombre;
        this.formula = formula;
        this.valor = valor;
        this.disponible = disponible;
    }

    public String getNombre() { return nombre; }
    public String getFormula() { return formula; }
    public BigDecimal getValor() { return valor; }
    public boolean isDisponible() { return disponible; }
}