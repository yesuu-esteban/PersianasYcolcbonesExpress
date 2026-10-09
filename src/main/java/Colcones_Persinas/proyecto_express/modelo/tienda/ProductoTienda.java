package Colcones_Persinas.proyecto_express.modelo.tienda;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Producto que se muestra en la tienda virtual (/tienda).
 *
 * Dos formas de cobrar:
 *  - M2:     persianas y cortinas a la medida. El precio sale de la TELA elegida y del
 *            ROLLO del que se corta (1,83 / 2,50 / 3,00 m), igual que en fábrica.
 *  - UNIDAD: productos de precio fijo (accesorios, etc.).
 *
 * Opciones de enrollable (solo productos a la medida, se activan en el admin):
 *  - Cabezal: el cliente puede pedirlo con cabezal; se cobra un valor por metro de ancho.
 *  - Enrollado al contrario: la tela cae por delante del tubo. No cambia el precio.
 */
@Entity
@Table(name = "tienda_producto")
@Getter @Setter @NoArgsConstructor
public class ProductoTienda {

    public static final String PRECIO_M2 = "M2";
    public static final String PRECIO_UNIDAD = "UNIDAD";

    /** Categorías visibles en la tienda (clave guardada → texto que ve el cliente). */
    public static final Map<String, String> CATEGORIAS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("PERSIANAS", "Persianas");
        m.put("CORTINAS", "Cortinas");
        m.put("TOLDOS", "Toldos");
        m.put("OTROS", "Otros");
        CATEGORIAS = Collections.unmodifiableMap(m);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private String nombre = "";

    /** Parte de la URL: /tienda/producto/{slug}. Se genera sola a partir del nombre. */
    @Column(nullable = false, unique = true)
    private String slug = "";

    @Column(nullable = false)
    private String categoria = "PERSIANAS";

    @Column(name = "descripcion_corta", length = 300)
    private String descripcionCorta = "";

    @Column(columnDefinition = "TEXT")
    private String descripcion = "";

    @Column(name = "tipo_precio", nullable = false)
    private String tipoPrecio = PRECIO_M2;

    /** Solo para tipoPrecio = UNIDAD. */
    @Column(name = "precio_unidad")
    private BigDecimal precioUnidad;

    /** Área mínima que se cobra (ej: 1 m² aunque la persiana sea más pequeña). */
    @Column(name = "m2_minimo", nullable = false)
    private double m2Minimo = 1.0;

    @Column(name = "ancho_min_cm", nullable = false) private int anchoMinCm = 30;
    @Column(name = "ancho_max_cm", nullable = false) private int anchoMaxCm = 300;
    @Column(name = "alto_min_cm", nullable = false)  private int altoMinCm = 30;
    @Column(name = "alto_max_cm", nullable = false)  private int altoMaxCm = 280;

    /** Si el cliente debe elegir el lado del mando (izquierda / derecha). */
    @Column(name = "con_mando", nullable = false)
    private boolean conMando = true;

    /**
     * Si el cliente puede pedirlo con cabezal. Es Boolean (no boolean) para que la
     * columna nueva acepte los productos que ya existían en la base de datos.
     */
    @Column(name = "ofrece_cabezal")
    private Boolean ofreceCabezal;

    /** Lo que cuesta el cabezal por cada metro de ancho de la persiana. */
    @Column(name = "precio_cabezal_metro")
    private BigDecimal precioCabezalMetro;

    /** Si el cliente puede pedirlo enrollado al contrario (la tela cae por delante del tubo). */
    @Column(name = "ofrece_enrollado_contrario")
    private Boolean ofreceEnrolladoContrario;

    @Column(name = "imagen_id")
    private Integer imagenId;

    @Column(nullable = false)
    private boolean activo = true;

    /** Los destacados salen primero en el catálogo. */
    @Column(nullable = false)
    private boolean destacado = false;

    @Column(nullable = false)
    private int orden = 0;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<TelaTienda> telas = new ArrayList<>();

    // ─── Helpers ────────────────────────────────────────────────────────

    @Transient
    public boolean isPorMetro() {
        return PRECIO_M2.equals(tipoPrecio);
    }

    /** ¿El cliente puede elegir "con cabezal"? (solo productos a la medida) */
    @Transient
    public boolean isCabezalDisponible() {
        return isPorMetro() && Boolean.TRUE.equals(ofreceCabezal);
    }

    /** ¿El cliente puede elegir "enrollado al contrario"? (solo productos a la medida) */
    @Transient
    public boolean isContrarioDisponible() {
        return isPorMetro() && Boolean.TRUE.equals(ofreceEnrolladoContrario);
    }

    @Transient
    public List<TelaTienda> getTelasActivas() {
        return telas.stream().filter(TelaTienda::isActiva).collect(Collectors.toList());
    }

    /** Precio más bajo para mostrar "Desde $X" en el catálogo. */
    @Transient
    public BigDecimal getPrecioDesde() {
        if (!isPorMetro()) return precioUnidad;
        return getTelasActivas().stream()
                .map(TelaTienda::getPrecioMinimo)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    @Transient
    public String getCategoriaEtiqueta() {
        return CATEGORIAS.getOrDefault(categoria, categoria);
    }

    public void agregarTela(TelaTienda tela) {
        tela.setProducto(this);
        telas.add(tela);
    }
}