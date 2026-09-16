package Colcones_Persinas.proyecto_express.modelo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado completo del cálculo de Costo de Fabricación REAL de un
 * pedido: la lista de líneas (una por material) y el total sumado.
 * "itemsFaltantes" avisa qué materiales todavía no tienen precio
 * definido en /inventario/precios, para que el jefe sepa qué le falta
 * cargar y el total no quede engañosamente bajo.
 */
public class ResultadoCostoFabricacion {

    private final List<LineaCostoFabricacion> lineas;
    private final BigDecimal total;
    private final List<String> itemsFaltantes;

    public ResultadoCostoFabricacion(List<LineaCostoFabricacion> lineas, BigDecimal total, List<String> itemsFaltantes) {
        this.lineas = lineas;
        this.total = total;
        this.itemsFaltantes = itemsFaltantes;
    }

    public List<LineaCostoFabricacion> getLineas() { return lineas; }
    public BigDecimal getTotal() { return total; }
    public List<String> getItemsFaltantes() { return itemsFaltantes; }
    public boolean isCompleto() { return itemsFaltantes.isEmpty(); }
}