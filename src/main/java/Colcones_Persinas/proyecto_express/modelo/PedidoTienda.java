package Colcones_Persinas.proyecto_express.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "pedido_tienda")
@Data
public class PedidoTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String nombreCliente = "";
    private String direccion = "";
    private String telefono = "";

    private LocalDateTime fechaPedido = LocalDateTime.now();
    private LocalDateTime fechaEntrega = LocalDateTime.now();

    private String descripcion = "";

    private BigDecimal abono = BigDecimal.ZERO;
    private BigDecimal saldo = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    private String fabrica = "";
    private String vendedor = "";
    private String aliado = "";
    private String estado = "Pendiente";
    private String metodoPago = "";

    @OneToMany(mappedBy = "pedidoTienda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedidoTienda> detalles = new ArrayList<>();

    // Método útil para agregar detalles manteniendo la relación bidireccional
    public void agregarDetalle(DetallePedidoTienda detalle) {
        detalles.add(detalle);
        detalle.setPedidoTienda(this);
    }
}