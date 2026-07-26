package Colcones_Persinas.proyecto_express.modelo;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "detalle_pedido_tienda")
@Data
public class DetallePedidoTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String producto = "";
    private String material = "";
    private int cantidad = 1;

    /** Precio unitario de VENTA al cliente. */
    private BigDecimal precioUnitario = BigDecimal.ZERO;

    /** Subtotal de venta = precioUnitario × cantidad. */
    private BigDecimal subtotal = BigDecimal.ZERO;

    /** Precio unitario que cobra la FÁBRICA por esta línea (tu costo). */
    private BigDecimal precioFabricaUnitario = BigDecimal.ZERO;

    /** Subtotal de costo de fábrica = precioFabricaUnitario × cantidad. */
    private BigDecimal subtotalFabrica = BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "pedido_tienda_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PedidoTienda pedidoTienda;
}