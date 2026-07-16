package Colcones_Persinas.proyecto_express.modelo;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "detalle_pedido_tienda")
@Data
public class DetallePedidoTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String producto = "";
    private int cantidad = 1;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal subtotal = BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "pedido_tienda_id")
    private PedidoTienda pedidoTienda;
}