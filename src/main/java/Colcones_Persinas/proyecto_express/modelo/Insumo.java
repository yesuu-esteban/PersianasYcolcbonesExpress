package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

/**
 * Catálogo de tipos de insumo que el jefe puede registrar libremente
 * (tubo, cuerda, control, pesa, o cualquier otro material nuevo que llegue).
 *
 * tieneMedida = true  → el insumo se mide por longitud y se gestiona por piezas
 *                       individuales (ver PiezaInsumo), igual que los rollos de tela.
 *                       Ej: "Tubo R24" (llegan barras de 6m que se van cortando).
 *
 * tieneMedida = false → el insumo se gestiona por unidades simples (stockUnidades),
 *                       se descuenta 1 por pedido. Ej: "Control R16".
 */
@Entity
@Table(name = "insumo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Insumo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /** Nombre libre definido por el jefe. Ej: "Tubo R24", "Cuerda 4m", "Control R16". */
    private String nombre = "";

    @Column(name = "tiene_medida", nullable = false)
    private Boolean tieneMedida = false;

    /**
     * Solo aplica cuando tieneMedida = false.
     * Cantidad de unidades disponibles en stock (ej: 12 controles).
     */
    @Column(name = "stock_unidades")
    private Integer stockUnidades = 0;

    /** Descripción libre opcional, para que el jefe aclare detalles. */
    private String descripcion = "";

    /**
     * Umbral personalizado a partir del cual se genera una alerta de stock bajo
     * para ESTE insumo en particular. Si es null, se usa el umbral global por
     * defecto (ver InventarioServicio.UMBRAL_UNIDAD_DEFECTO / UMBRAL_METROS_DEFECTO).
     *
     * - Para insumos POR UNIDAD: cantidad de unidades (ej: 50).
     * - Para insumos POR MEDIDA: cantidad de metros totales restantes (ej: 10.0),
     *   truncado a entero en el formulario por simplicidad.
     */
    @Column(name = "umbral_alerta")
    private Integer umbralAlerta;
}