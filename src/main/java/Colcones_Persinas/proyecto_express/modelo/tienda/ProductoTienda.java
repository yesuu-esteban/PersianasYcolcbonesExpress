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
 * Tres formas de cobrar:
 *  - M2:     persianas y cortinas a la medida. El precio sale de la TELA elegida y del
 *            ROLLO del que se corta (1,83 / 2,50 / 3,00 m), igual que en fábrica.
 *  - RIEL:   riel de onda serena. Se cobra por metro de ancho, con un precio si es con
 *            bastón y otro si es con control.
 *  - UNIDAD: productos de precio fijo (accesorios, etc.).
 *
 * Opciones de enrollable (solo productos a la medida, se activan en el admin):
 *  - Cabezal: el cliente puede pedirlo con cabezal; se cobra un valor por metro de ancho.
 *  - Enrollado al contrario: la tela cae por delante del tubo. No cambia el precio.
 *
 * Conexión con Shopify (pantalla "Conexión con Shopify"):
 *  - Los productos de precio fijo que son NUESTROS (proveedor vacío o PROPIO) se envían a Shopify.
 *  - Los productos que la app de Dropi mete en Shopify llegan solos aquí (proveedor SHOPIFY): nombre,
 *    descripción, foto, precio, costo y stock se toman de Shopify. Si se agotan allá, se ocultan aquí.
 *    Cuando un cliente los compra aquí, el pedido se crea solo en Shopify y Dropi lo despacha.
 */
@Entity
@Table(name = "tienda_producto")
@Getter @Setter @NoArgsConstructor
public class ProductoTienda {

    public static final String PRECIO_M2 = "M2";
    public static final String PRECIO_UNIDAD = "UNIDAD";
    public static final String PRECIO_RIEL = "RIEL";

    /** De dónde viene el producto. Vacío o PROPIO = nuestro; SHOPIFY = viene de Shopify (productos de Dropi). */
    public static final String PROVEEDOR_PROPIO = "PROPIO";
    public static final String PROVEEDOR_SHOPIFY = "SHOPIFY";
    /** Valor de antes (productos de Dropi creados a mano). Ya no se usa: al guardar el producto pasa a PROPIO. */
    public static final String PROVEEDOR_DROPI_A_MANO = "DROPI";

    /** Sistemas del riel de onda serena (lo que se guarda → lo que ve el cliente). */
    public static final String SISTEMA_BASTON = "BASTON";
    public static final String SISTEMA_CONTROL = "CONTROL";
    /** Hacia dónde abre el riel (igual que en fábrica). */
    public static final List<String> APERTURAS_RIEL = List.of("Izquierda", "Derecha", "Hacia los extremos");

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

    /** Riel de onda serena: precio por metro de ancho con bastón (vacío = no se ofrece con bastón). */
    @Column(name = "precio_riel_baston_metro")
    private BigDecimal precioRielBastonMetro;

    /** Riel de onda serena: precio por metro de ancho con control (vacío = no se ofrece con control). */
    @Column(name = "precio_riel_control_metro")
    private BigDecimal precioRielControlMetro;

    /** De dónde viene: vacío/PROPIO (nuestro) o SHOPIFY (viene de Shopify; normalmente productos de Dropi). */
    @Column(name = "proveedor", length = 20)
    private String proveedor;

    /** Productos que vienen de Shopify: lo que nos cuesta cada unidad (el costo que tiene en Shopify). */
    @Column(name = "costo_proveedor")
    private BigDecimal costoProveedor;

    /** ID del producto en Shopify (gid://shopify/Product/...). En los nuestros: cuando ya se envió allá. */
    @Column(name = "shopify_producto_id", length = 100)
    private String shopifyProductoId;

    /** Productos que vienen de Shopify: la variante exacta (gid://shopify/ProductVariant/...). Con esto se crea el pedido allá. */
    @Column(name = "shopify_variante_id", length = 100)
    private String shopifyVarianteId;

    /** Nuestros productos: qué foto se le mandó a Shopify, para no volver a subirla si no cambió. */
    @Column(name = "shopify_imagen_id")
    private Integer shopifyImagenId;

    /** Productos que vienen de Shopify: de qué dirección se bajó la foto, para no bajarla otra vez. */
    @Column(name = "shopify_imagen_url", length = 1000)
    private String shopifyImagenUrl;

    /** Productos que vienen de Shopify: unidades disponibles allá (vacío = Shopify no lleva inventario). */
    @Column(name = "stock_shopify")
    private Integer stockShopify;

    /** true si se ocultó solo porque se agotó en Shopify (vuelve a verse solo cuando llegue stock). */
    @Column(name = "oculto_por_stock")
    private Boolean ocultoPorStock;

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

    /** ¿Es un riel de onda serena (se cobra por metro de ancho)? */
    @Transient
    public boolean isRiel() {
        return PRECIO_RIEL.equals(tipoPrecio);
    }

    /** Precio por metro del riel según el sistema ("BASTON" o "CONTROL"). Null si no se ofrece. */
    @Transient
    public BigDecimal precioRielPorMetro(String sistema) {
        BigDecimal v = SISTEMA_BASTON.equals(sistema) ? precioRielBastonMetro
                : SISTEMA_CONTROL.equals(sistema) ? precioRielControlMetro : null;
        return v != null && v.signum() > 0 ? v : null;
    }

    @Transient
    public boolean isConBaston() {
        return isRiel() && precioRielPorMetro(SISTEMA_BASTON) != null;
    }

    @Transient
    public boolean isConControl() {
        return isRiel() && precioRielPorMetro(SISTEMA_CONTROL) != null;
    }

    /** "con bastón" / "con control" (para los textos del pedido). */
    public static String textoSistema(String sistema) {
        return SISTEMA_CONTROL.equals(sistema) ? "con control" : SISTEMA_BASTON.equals(sistema) ? "con bastón" : "";
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

    /** ¿Viene de Shopify? (normalmente productos de Dropi que la app de Dropi puso en Shopify) */
    @Transient
    public boolean isDeShopify() {
        return PROVEEDOR_SHOPIFY.equals(proveedor);
    }

    /** ¿Es un producto de Dropi creado a mano antes (ya no se usa)? */
    @Transient
    public boolean isDropiAMano() {
        return PROVEEDOR_DROPI_A_MANO.equals(proveedor);
    }

    /** Productos que vienen de Shopify: lo que se gana por unidad (precio de venta − costo). Null si falta algún dato. */
    @Transient
    public BigDecimal getGananciaUnidad() {
        if (!isDeShopify() || precioUnidad == null || costoProveedor == null || costoProveedor.signum() <= 0) return null;
        return precioUnidad.subtract(costoProveedor);
    }

    /**
     * ¿Se envía a Shopify? Solo los de precio fijo que son NUESTROS
     * (los que vienen de Shopify ya están allá; los a la medida solo se venden aquí).
     */
    @Transient
    public boolean isParaShopify() {
        boolean nuestro = proveedor == null || proveedor.isBlank() || PROVEEDOR_PROPIO.equals(proveedor);
        return nuestro && PRECIO_UNIDAD.equals(tipoPrecio) && precioUnidad != null && precioUnidad.signum() > 0;
    }

    /** Nuestros productos: ¿ya están en Shopify? */
    @Transient
    public boolean isEnShopify() {
        return !isDeShopify() && shopifyProductoId != null && !shopifyProductoId.isBlank();
    }

    @Transient
    public List<TelaTienda> getTelasActivas() {
        return telas.stream().filter(TelaTienda::isActiva).collect(Collectors.toList());
    }

    /** Precio más bajo para mostrar "Desde $X" en el catálogo. */
    @Transient
    public BigDecimal getPrecioDesde() {
        if (isRiel()) {
            BigDecimal b = precioRielPorMetro(SISTEMA_BASTON), c = precioRielPorMetro(SISTEMA_CONTROL);
            if (b == null) return c;
            if (c == null) return b;
            return b.min(c);
        }
        if (!isPorMetro()) return precioUnidad;
        return getTelasActivas().stream()
                .map(TelaTienda::getPrecioMinimo)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /** Cómo se lee el precio "Desde": "el m²", "el metro" o "c/u". */
    @Transient
    public String getUnidadPrecio() {
        if (isPorMetro()) return "el m²";
        if (isRiel()) return "el metro";
        return "c/u";
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