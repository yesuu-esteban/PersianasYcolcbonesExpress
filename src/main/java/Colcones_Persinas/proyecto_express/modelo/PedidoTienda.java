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

    /** Suma automática de venta de los productos (cantidad × precio unitario de cada línea). Informativo. */
    private BigDecimal total = BigDecimal.ZERO;

    /** Precio final que se le cobra al cliente (puede diferir del total sumado, por descuentos, etc). */
    private BigDecimal precioFinal = BigDecimal.ZERO;

    /** Lo que el cliente ha abonado hasta el momento. */
    private BigDecimal abono = BigDecimal.ZERO;

    /** Saldo pendiente = precioFinal - abono. */
    private BigDecimal saldo = BigDecimal.ZERO;

    private String fabrica = "";
    private String vendedor = "";
    private String aliado = "";

    /** Estados posibles: "Pendiente", "Pedido", "En Bodega", "Instalado", "Terminado". */
    private String estado = "Pendiente";

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

    /** Costo total de fábrica = suma de subtotalFabrica de cada línea. Único lugar de cálculo, no se duplica. */
    @Transient
    public BigDecimal getCostoFabricaTotal() {
        if (detalles == null) return BigDecimal.ZERO;
        return detalles.stream()
                .map(d -> d.getSubtotalFabrica() != null ? d.getSubtotalFabrica() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Utilidad estimada = precioFinal - costoFabricaTotal. Útil para ver margen de un vistazo. */
    @Transient
    public BigDecimal getUtilidadEstimada() {
        BigDecimal pf = precioFinal != null ? precioFinal : BigDecimal.ZERO;
        return pf.subtract(getCostoFabricaTotal());
    }
}