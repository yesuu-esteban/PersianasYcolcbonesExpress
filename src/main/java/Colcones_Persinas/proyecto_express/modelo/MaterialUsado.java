package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro histórico de qué material exacto se usó en cada pedido:
 * de qué rollo de tela o pieza de insumo salió, cuántos metros se
 * cortaron y cuánto quedó disponible después del corte.
 *
 * Esto es lo que permite saber en cualquier momento qué se ha
 * consumido, de dónde, y qué queda disponible para el futuro
 * (incluyendo los retazos que el jefe decida reutilizar).
 */
@Entity
@Table(name = "material_usado")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MaterialUsado {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /**
     * Id del pedido al que pertenece este registro. Se guarda como entero
     * simple (sin relación @ManyToOne) porque Pedido.id es un "int" primitivo
     * y declarar la relación como entidad generaba un conflicto de tipos
     * en la foreign key (Hibernate la inferia como BIGINT, incompatible con
     * el INT de la tabla pedido).
     */
    @Column(name = "pedido_id", nullable = false)
    private int pedidoId;

    /** Tipo de material: "TELA", "RETAZO", "TUBO", "CUERDA", "PESA", "CONTROL", etc. */
    private String tipoMaterial = "";

    /** Si salió de un RolloTela, aquí queda el id de ese rollo. Null si fue de un insumo. */
    @Column(name = "rollo_tela_id")
    private Integer rolloTelaId;

    /** Si salió de una PiezaInsumo, aquí queda el id de esa pieza. Null si fue de tela. */
    @Column(name = "pieza_insumo_id")
    private Integer piezaInsumoId;

    /** Descripción legible de la fuente, para mostrar en pantalla sin tener que hacer joins. Ej: "Rollo Blanco 1.83m (#12)". */
    @Column(name = "fuente_descripcion")
    private String fuenteDescripcion = "";

    /** Cuántos metros (lineales) se cortaron de esa pieza/rollo para este pedido. */
    private double metrosUsados = 0.0;

    /** Cuánto quedó disponible en esa pieza/rollo después de este corte. */
    private double metrosSobrantes = 0.0;

    /**
     * Solo aplica cuando tipoMaterial es "TELA" o "RETAZO": área real de tela
     * consumida en este pedido, en metros cuadrados (ancho de corte x alto de
     * corte). Null para el resto de materiales (tubo, cuerda, pesa, etc.).
     */
    @Column(name = "metros_cuadrados")
    private Double metrosCuadrados;

    /** True si el jefe eligió manualmente esta fuente (por ejemplo, un retazo) en vez de la sugerida por el algoritmo. */
    @Column(name = "seleccion_manual")
    private Boolean seleccionManual = false;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @PrePersist
    protected void alCrear() {
        if (this.fecha == null) {
            this.fecha = LocalDateTime.now();
        }
    }
}