package Colcones_Persinas.proyecto_express.modelo.contabilidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Los datos de contabilidad de una venta de Almacén que no están en el pedido:
 * factura, IVA, retención en la fuente, proveedor, costo real, gasto, instalación,
 * punto de venta y quién instaló (las columnas de la planilla "Ventas" de contabilidad).
 *
 * Va en su propia tabla (una fila por pedido de Almacén) para no tocar el pedido.
 * Si un pedido no tiene fila, se usan los valores por defecto: sin factura, sin IVA,
 * sin retención, costo = el de fábrica del pedido y gasto = 0.
 */
@Entity
@Table(name = "conta_datos_venta",
       uniqueConstraints = @UniqueConstraint(name = "uk_conta_datos_venta_pedido", columnNames = "pedido_tienda_id"))
@Getter @Setter @NoArgsConstructor
public class DatosVenta {

    // Estado de la factura
    public static final String FACTURA_PENDIENTE = "PENDIENTE";
    public static final String FACTURADA = "FACTURADA";
    public static final String FACTURA_NO_REQUIERE = "NO_REQUIERE";

    // Estado de la instalación ("Finalizado" en la planilla)
    public static final String NO_INSTALADO = "NO_INSTALADO";
    public static final String INSTALADO = "INSTALADO";
    public static final String ENTREGADO = "ENTREGADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "pedido_tienda_id", nullable = false)
    private Integer pedidoTiendaId;

    /** Número de la factura, p. ej. "FECL 436". */
    @Column(name = "numero_factura", length = 40)
    private String numeroFactura = "";

    @Column(name = "estado_factura", length = 20)
    private String estadoFactura = FACTURA_PENDIENTE;

    /** true = la venta lleva IVA. El precio del pedido YA lo incluye; aquí solo se separa. */
    @Column(name = "lleva_iva")
    private Boolean llevaIva = false;

    @Column(name = "porcentaje_iva", precision = 5, scale = 2)
    private BigDecimal porcentajeIva = new BigDecimal("19");

    /** Retención en la fuente (%) sobre el subtotal. 0 = no hay retención. */
    @Column(name = "porcentaje_retencion", precision = 5, scale = 2)
    private BigDecimal porcentajeRetencion = BigDecimal.ZERO;

    /** Vacío = el "Fabricante" del pedido de Almacén. */
    @Column(length = 120)
    private String proveedor;

    /** Costo real de la venta. Vacío (null) = el costo de fábrica que calcula el pedido. */
    @Column(precision = 15, scale = 2)
    private BigDecimal costo;

    /** Gastos de la venta: instalación, transporte, comisiones… */
    @Column(precision = 15, scale = 2)
    private BigDecimal gasto = BigDecimal.ZERO;

    @Column(name = "estado_instalacion", length = 20)
    private String estadoInstalacion = NO_INSTALADO;

    @Column(name = "punto_venta", length = 40)
    private String puntoVenta = "";

    @Column(name = "quien_instalo", length = 80)
    private String quienInstalo = "";

    @Column(name = "actualizado_por", length = 80)
    private String actualizadoPor = "";

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
}