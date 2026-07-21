package Colcones_Persinas.proyecto_express.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

@Entity
@Table(name = "pedido_tienda")
@Data
public class PedidoTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String nombreCliente = "";
    private String cedula = "";
    private String direccion = "";
    private String telefono = "";

    private LocalDateTime fechaPedido = LocalDateTime.now();

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime fechaEntrega;

    private String descripcion = "";

    private BigDecimal abono = BigDecimal.ZERO;
    private BigDecimal saldo = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    private String fabrica = "";
    private String vendedor = "";
    private String aliado = "";

    /** Estados posibles: "Pedido", "En Bodega", "Instalado", "Terminado". */
    private String estado = "Pedido";

    private String metodoPago = "";

    @OneToMany(mappedBy = "pedidoTienda", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DetallePedidoTienda> detalles = new ArrayList<>();

    public void agregarDetalle(DetallePedidoTienda detalle) {
        detalles.add(detalle);
        detalle.setPedidoTienda(this);
    }

    /** "Pagado Completo" si el saldo es 0 o negativo, "Pendiente" en cualquier otro caso. */
    @Transient
    public String getEstadoPago() {
        if (saldo == null) return "Pendiente";
        return saldo.compareTo(BigDecimal.ZERO) <= 0 ? "Pagado Completo" : "Pendiente";
    }
}