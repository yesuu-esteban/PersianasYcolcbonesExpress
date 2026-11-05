package Colcones_Persinas.proyecto_express.modelo.fabrica;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una línea de lo que se vendió en una venta directa de Fábrica
 * (tela por ancho × alto, o un insumo), con su precio.
 * Se usa para imprimir el comprobante de la venta y para el total.
 */
@Entity
@Table(name = "venta_directa_linea")
@Getter @Setter @NoArgsConstructor
public class VentaDirectaLinea {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /** El pedido (tipo VENTA_DIRECTA) al que pertenece. */
    @Column(name = "pedido_id", nullable = false)
    private int pedidoId;

    private int orden;

    /** TELA, INSUMO o LIBRE. */
    private String tipo;

    private String descripcion;

    /** Medida de la tela en palabras: "1.50 × 2.00 m" (vacío en insumos). */
    private String medida;

    /** m² (tela), unidades o metros. */
    private double cantidad;

    /** "m²", "und." o "m". */
    private String unidad;

    @Column(name = "precio_unitario")
    private double precioUnitario;

    private double total;
}