package Colcones_Persinas.proyecto_express.modelo.tienda;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tela de un producto de la tienda (ej: "Blackout liso", "Screen 5%").
 * Cada tela tiene un precio por m² DISTINTO según el ancho del rollo del que se corta.
 * Si un precio está vacío, ese rollo no está disponible para esa tela.
 */
@Entity
@Table(name = "tienda_tela")
@Getter @Setter @NoArgsConstructor
public class TelaTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoTienda producto;

    @Column(nullable = false)
    private String nombre = "";

    /** Colores separados por coma: "Blanco, Gris, Fawn". */
    @Column(length = 500)
    private String colores = "";

    @Column(name = "precio_rollo_183") private BigDecimal precioRollo183;
    @Column(name = "precio_rollo_250") private BigDecimal precioRollo250;
    @Column(name = "precio_rollo_300") private BigDecimal precioRollo300;

    @Column(nullable = false)
    private boolean activa = true;

    public TelaTienda(String nombre, String colores, Integer p183, Integer p250, Integer p300) {
        this.nombre = nombre;
        this.colores = colores;
        this.precioRollo183 = p183 != null ? BigDecimal.valueOf(p183) : null;
        this.precioRollo250 = p250 != null ? BigDecimal.valueOf(p250) : null;
        this.precioRollo300 = p300 != null ? BigDecimal.valueOf(p300) : null;
    }

    @Transient
    public List<String> getListaColores() {
        if (colores == null || colores.isBlank()) return List.of();
        return Arrays.stream(colores.split(",")).map(String::trim).filter(c -> !c.isEmpty()).collect(Collectors.toList());
    }

    /** Precio por m² para un ancho de rollo (1.83, 2.50 o 3.00). Null si no está disponible. */
    public BigDecimal precioParaRollo(double rollo) {
        if (Math.abs(rollo - 1.83) < 0.001) return precioRollo183;
        if (Math.abs(rollo - 2.50) < 0.001) return precioRollo250;
        if (Math.abs(rollo - 3.00) < 0.001) return precioRollo300;
        return null;
    }

    @Transient
    public BigDecimal getPrecioMinimo() {
        return Stream.of(precioRollo183, precioRollo250, precioRollo300)
                .filter(Objects::nonNull)
                .filter(p -> p.signum() > 0)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }
}