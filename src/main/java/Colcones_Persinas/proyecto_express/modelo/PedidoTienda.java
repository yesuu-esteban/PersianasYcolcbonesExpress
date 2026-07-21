package Colcones_Persinas.proyecto_express.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;

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

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime fechaEntrega = LocalDateTime.now();

    private String descripcion = "";

    private BigDecimal abono = BigDecimal.ZERO;
    private BigDecimal saldo = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    private String fabrica = "";
    private String vendedor = "";
    private String aliado = "";

    private String estado = "pendiente";
    private String metodoPago = "";

    @OneToMany(mappedBy = "pedidoTienda", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DetallePedidoTienda> detalles = new ArrayList<>();

    public void agregarDetalle(DetallePedidoTienda detalle) {
        detalles.add(detalle);
        detalle.setPedidoTienda(this);
    }

    /**
     * Estado de pago calculado automáticamente a partir del saldo.
     * No se persiste en BD — siempre refleja el saldo real.
     */
    @Transient
    public String getEstadoPago() {
        if (saldo == null) {
            return "Saldo Pendiente";
        }
        return saldo.compareTo(BigDecimal.ZERO) <= 0 ? "Pagado Completo" : "Saldo Pendiente";
    }
}