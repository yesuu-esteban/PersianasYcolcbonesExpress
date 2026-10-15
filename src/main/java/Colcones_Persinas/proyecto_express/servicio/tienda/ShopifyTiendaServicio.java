package Colcones_Persinas.proyecto_express.servicio.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.ImagenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.ItemOrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.ImagenTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.ProductoTiendaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * La tienda de Shopify conectada a la nuestra. Shopify es el puente con Dropi:
 *
 *   nuestra tienda  ⇄  Shopify  ⇄  Dropi (con la app de Dropi instalada en Shopify)
 *
 *  1. ENVIAR PRODUCTOS: los productos de precio fijo que son NUESTROS se crean o actualizan en Shopify
 *     (nombre, descripción, precio y foto). Si un producto se oculta aquí, queda en borrador allá.
 *  2. TRAER PRODUCTOS DE SHOPIFY: los demás productos de Shopify (los que mete la app de Dropi) llegan
 *     aquí con foto, precio, costo y stock. Si se agotan allá, se ocultan aquí; si vuelve el stock, se muestran.
 *  3. PEDIDOS DE AQUÍ → SHOPIFY: cuando un cliente paga aquí productos que vienen de Shopify, el pedido se
 *     crea solo en Shopify (ya pagado) con esos productos, y Dropi lo despacha. La guía vuelve sola.
 *  4. PEDIDOS DE SHOPIFY → AQUÍ: los pedidos pagados en Shopify que traen productos NUESTROS llegan a
 *     Pedidos con la etiqueta "Shopify". Un pedido nunca se trae dos veces.
 *
 * Todo se revisa solo cada 10 minutos (productos cada 30) y también con los botones de la pantalla.
 * La plata de las ventas hechas en Shopify no entra por nuestro Wompi: quedan en Contabilidad →
 * "Ventas sin anotar" para elegir a qué cuenta entró.
 */
@Service
public class ShopifyTiendaServicio {

    private static final Logger log = LoggerFactory.getLogger(ShopifyTiendaServicio.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", new Locale("es", "CO"));

    /** Resultado de enviar un producto. */
    public record ResultadoProducto(String nombre, boolean ok, String accion, String mensaje) {}

    /** Resultado de traer pedidos (y de mandar a Shopify los pendientes y traer guías). */
    public record ResultadoPedidos(LocalDateTime cuando, int revisados, int nuevos, int sinProductosNuestros,
                                   List<String> traidos, String error, String aviso,
                                   int enviadosAShopify, int conErrorAShopify, int guiasNuevas) {
        public ResultadoPedidos(LocalDateTime cuando, int revisados, int nuevos, int sinProductosNuestros,
                                List<String> traidos, String error, String aviso) {
            this(cuando, revisados, nuevos, sinProductosNuestros, traidos, error, aviso, 0, 0, 0);
        }
        public String getCuandoFormateado() { return cuando == null ? "" : cuando.format(FMT); }
        ResultadoPedidos con(int enviados, int conError, int guias) {
            return new ResultadoPedidos(cuando, revisados, nuevos, sinProductosNuestros, traidos, error, aviso, enviados, conError, guias);
        }
    }

    /** Resultado de traer los productos de Shopify (los de Dropi). */
    public record ResultadoImportacion(LocalDateTime cuando, int nuevos, int actualizados, int agotados,
                                       int retirados, List<String> detalle, String error) {
        public String getCuandoFormateado() { return cuando == null ? "" : cuando.format(FMT); }
    }

    private final ShopifyServicio shopify;
    private final ProductoTiendaRepository productoRepository;
    private final OrdenTiendaRepository ordenRepository;
    private final ImagenTiendaRepository imagenRepository;
    /** Para trabajar con un pedido y sus productos en su propia transacción. Puede ser null en pruebas. */
    private final TransactionTemplate transaccionAparte;

    private volatile ResultadoPedidos ultimaRevision;
    private volatile ResultadoImportacion ultimaImportacion;
    private String publicacionTiendaOnline;

    public ShopifyTiendaServicio(ShopifyServicio shopify, ProductoTiendaRepository productoRepository,
                                 OrdenTiendaRepository ordenRepository, ImagenTiendaRepository imagenRepository,
                                 PlatformTransactionManager transacciones) {
        this.shopify = shopify;
        this.productoRepository = productoRepository;
        this.ordenRepository = ordenRepository;
        this.imagenRepository = imagenRepository;
        if (transacciones != null) {
            this.transaccionAparte = new TransactionTemplate(transacciones);
            this.transaccionAparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        } else {
            this.transaccionAparte = null;
        }
    }

    public ResultadoImportacion getUltimaImportacion() { return ultimaImportacion; }

    public ResultadoPedidos getUltimaRevision() { return ultimaRevision; }

    // ═══════════════════════════════════════════════════════════════
    // PROBAR LA CONEXIÓN
    // ═══════════════════════════════════════════════════════════════

    /** Devuelve "Nombre de la tienda (moneda)" si la conexión funciona. Lanza ShopifyError si no. */
    public String probarConexion() {
        JsonNode d = shopify.graphql("query { shop { name currencyCode myshopifyDomain } }", null);
        JsonNode s = d.path("shop");
        return s.path("name").asText("?") + " · " + s.path("myshopifyDomain").asText("") + " · moneda " + s.path("currencyCode").asText("?");
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. ENVIAR PRODUCTOS A SHOPIFY
    // ═══════════════════════════════════════════════════════════════

    private static final String MUTACION_PRODUCTO =
            "mutation guardar($input: ProductSetInput!, $identifier: ProductSetIdentifiers) {"
            + " productSet(input: $input, identifier: $identifier, synchronous: true) {"
            + "  product { id }"
            + "  userErrors { field message }"
            + " } }";

    /**
     * Crea o actualiza en Shopify los productos de precio fijo que son nuestros.
     * @param urlPublica dirección pública de nuestra tienda (para que Shopify descargue las fotos). Si es
     *                   la de la computadora (localhost), los productos se envían sin foto.
     */
    public synchronized List<ResultadoProducto> enviarProductos(String urlPublica) {
        boolean fotosPosibles = urlPublica != null && urlPublica.startsWith("https://")
                && !urlPublica.contains("localhost") && !urlPublica.contains("127.0.0.1");
        List<ResultadoProducto> resultados = new ArrayList<>();
        for (ProductoTienda p : productoRepository.findAllByOrderByOrdenAscNombreAsc()) {
            boolean apto = p.isParaShopify();
            if (!apto && !p.isEnShopify()) continue;   // a la medida o de Dropi y nunca se envió: no se toca
            try {
                resultados.add(enviarUno(p, apto, fotosPosibles ? urlPublica : null));
            } catch (ShopifyServicio.ShopifyError e) {
                resultados.add(new ResultadoProducto(p.getNombre(), false, "No se pudo", e.getMessage()));
            }
        }
        return resultados;
    }

    private ResultadoProducto enviarUno(ProductoTienda p, boolean apto, String urlFotos) {
        boolean crear = !p.isEnShopify();
        boolean conFoto = apto && urlFotos != null && p.getImagenId() != null
                && (crear || !p.getImagenId().equals(p.getShopifyImagenId()));
        String id;
        try {
            id = guardarEnShopify(entrada(p, apto, crear, conFoto ? urlFotos : null), crear ? null : p.getShopifyProductoId());
        } catch (ShopifyServicio.ShopifyError e) {
            String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            boolean noExiste = m.contains("not exist") || m.contains("not found") || m.contains("no existe");
            if (crear || !noExiste || !apto) throw e;
            // Lo borraron en Shopify: se vuelve a crear
            crear = true;
            conFoto = urlFotos != null && p.getImagenId() != null;
            id = guardarEnShopify(entrada(p, true, true, conFoto ? urlFotos : null), null);
        }
        p.setShopifyProductoId(id);
        if (conFoto) p.setShopifyImagenId(p.getImagenId());
        if (apto && p.getImagenId() == null) p.setShopifyImagenId(null);
        productoRepository.save(p);

        String aviso = "";
        if (crear && apto) aviso = publicarEnTiendaOnline(id);
        if (apto && p.getImagenId() != null && urlFotos == null && (crear || !p.getImagenId().equals(p.getShopifyImagenId()))) {
            aviso += (aviso.isEmpty() ? "" : " ") + "Sin foto: envíalo desde la página de Railway (no desde tu computadora) para que Shopify pueda descargarla.";
        }
        String accion = !apto ? "Pasado a borrador" : crear ? "Creado" : "Actualizado";
        if (apto && !p.isActivo()) accion += " (en borrador, porque está oculto aquí)";
        return new ResultadoProducto(p.getNombre(), true, accion, aviso);
    }

    private String guardarEnShopify(Map<String, Object> input, String idExistente) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("input", input);
        if (idExistente != null) vars.put("identifier", Map.of("id", idExistente));
        JsonNode r = shopify.graphql(MUTACION_PRODUCTO, vars).path("productSet");
        String errores = erroresDeUsuario(r.path("userErrors"));
        if (!errores.isEmpty()) throw new ShopifyServicio.ShopifyError("Shopify no lo aceptó: " + errores);
        String id = r.path("product").path("id").asText("");
        if (id.isEmpty()) throw new ShopifyServicio.ShopifyError("Shopify no devolvió el producto.");
        return id;
    }

    /** Lo que se le manda a Shopify de un producto. */
    static Map<String, Object> entrada(ProductoTienda p, boolean apto, boolean crear, String urlFotos) {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("title", p.getNombre());
        if (!apto) {
            // Ya no se vende en Shopify (se pasó a Dropi o a la medida): solo se pasa a borrador
            in.put("status", "DRAFT");
            return in;
        }
        in.put("descriptionHtml", descripcionHtml(p));
        in.put("vendor", "Persianas Express");
        in.put("productType", p.getCategoriaEtiqueta());
        in.put("status", p.isActivo() ? "ACTIVE" : "DRAFT");
        if (crear && p.getSlug() != null && !p.getSlug().isBlank()) in.put("handle", p.getSlug());
        in.put("productOptions", List.of(Map.of("name", "Title", "values", List.of(Map.of("name", "Default Title")))));
        Map<String, Object> variante = new LinkedHashMap<>();
        variante.put("optionValues", List.of(Map.of("optionName", "Title", "name", "Default Title")));
        variante.put("price", p.getPrecioUnidad().setScale(2, RoundingMode.HALF_UP).toPlainString());
        // Lo fabricamos o lo tenemos: no se lleva inventario en Shopify y se puede vender siempre
        variante.put("inventoryPolicy", "CONTINUE");
        variante.put("inventoryItem", Map.of("tracked", false));
        in.put("variants", List.of(variante));
        if (urlFotos != null) {
            in.put("files", List.of(Map.of(
                    "originalSource", urlFotos + "/tienda/img/" + p.getImagenId(),
                    "contentType", "IMAGE",
                    "alt", p.getNombre())));
        } else if (p.getImagenId() == null && p.getShopifyImagenId() != null) {
            in.put("files", List.of());   // se le quitó la foto aquí: se quita allá
        }
        return in;
    }

    static String descripcionHtml(ProductoTienda p) {
        StringBuilder sb = new StringBuilder();
        for (String parte : new String[]{p.getDescripcionCorta(), p.getDescripcion()}) {
            if (parte == null || parte.isBlank()) continue;
            for (String parrafo : parte.trim().split("\\R\\s*\\R")) {
                sb.append("<p>").append(escapar(parrafo.trim()).replaceAll("\\R", "<br>")).append("</p>");
            }
        }
        return sb.toString();
    }

    /** Publica el producto en el canal "Tienda online" de Shopify. Devuelve un aviso si no se pudo. */
    private String publicarEnTiendaOnline(String productoId) {
        try {
            if (publicacionTiendaOnline == null) {
                JsonNode d = shopify.graphql("query { publications(first: 25, catalogType: APP) { nodes { id"
                        + " catalog { ... on AppCatalog { apps(first: 1) { nodes { handle } } } } } } }", null);
                for (JsonNode n : d.path("publications").path("nodes")) {
                    for (JsonNode a : n.path("catalog").path("apps").path("nodes")) {
                        if ("online_store".equals(a.path("handle").asText())) publicacionTiendaOnline = n.path("id").asText();
                    }
                }
            }
            if (publicacionTiendaOnline == null) {
                return "Revisa en Shopify que esté disponible en la Tienda online (no se encontró ese canal).";
            }
            JsonNode r = shopify.graphql("mutation publicar($id: ID!, $pub: ID!) { publishablePublish(id: $id,"
                    + " input: [{ publicationId: $pub }]) { userErrors { field message } } }",
                    Map.of("id", productoId, "pub", publicacionTiendaOnline)).path("publishablePublish");
            String errores = erroresDeUsuario(r.path("userErrors"));
            return errores.isEmpty() ? "" : "Creado, pero no se pudo publicar en la Tienda online: " + errores;
        } catch (ShopifyServicio.ShopifyError e) {
            return "Creado, pero no se pudo publicar en la Tienda online (" + e.getMessage() + "). Publícalo a mano en Shopify.";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. TRAER PEDIDOS PAGADOS DE SHOPIFY
    // ═══════════════════════════════════════════════════════════════

    private static final String CAMPOS_PEDIDO =
            " id name createdAt cancelledAt note"
            + " lineItems(first: 50) { nodes { title variantTitle quantity product { id }"
            + "   discountedTotalSet { shopMoney { amount } } } }";

    private static final String CAMPOS_CLIENTE =
            " email phone"
            + " customer { firstName lastName email phone }"
            + " shippingAddress { firstName lastName address1 address2 city province phone }";

    private static String consultaPedidos(boolean conCliente) {
        return "query pedidos($q: String!) { orders(first: 50, sortKey: CREATED_AT, reverse: true, query: $q) { nodes {"
                + CAMPOS_PEDIDO + (conCliente ? CAMPOS_CLIENTE : "") + " } } }";
    }

    /** Cada 10 minutos (si Shopify está configurado): pedidos de allá, pedidos pendientes de mandar y guías. */
    @Scheduled(initialDelayString = "${SHOPIFY_PRIMERA_REVISION_MS:120000}", fixedDelayString = "${SHOPIFY_CADA_MS:600000}")
    public void revisarSolo() {
        if (!shopify.isConfigurado()) return;
        try {
            ResultadoPedidos r = sincronizarPedidos(3);
            if (r.error() != null) log.warn("[Shopify] No se pudieron traer los pedidos: {}", r.error());
            if (r.nuevos() > 0 || r.enviadosAShopify() > 0 || r.guiasNuevas() > 0) {
                log.info("[Shopify] {} pedido(s) traído(s), {} enviado(s) a Shopify, {} guía(s) nueva(s).",
                        r.nuevos(), r.enviadosAShopify(), r.guiasNuevas());
            }
        } catch (Exception e) {
            log.error("[Shopify] Falló la revisión automática de pedidos.", e);
        }
    }

    /** Cada 30 minutos: trae los productos de Shopify (precio, stock y fotos de los de Dropi). */
    @Scheduled(initialDelayString = "${SHOPIFY_PRIMERA_REVISION_MS:120000}", fixedDelayString = "${SHOPIFY_PRODUCTOS_CADA_MS:1800000}")
    public void revisarProductosSolo() {
        if (!shopify.isConfigurado()) return;
        try {
            ResultadoImportacion r = traerProductos();
            if (r.error() != null) log.warn("[Shopify] No se pudieron traer los productos: {}", r.error());
        } catch (Exception e) {
            log.error("[Shopify] Falló la revisión automática de productos.", e);
        }
    }

    /** Todo lo de pedidos: traer los de Shopify, mandar los pendientes y traer las guías. */
    public ResultadoPedidos sincronizarPedidos(int dias) {
        ResultadoPedidos r = traerPedidos(dias);
        int[] envio = enviarPendientes();
        int guias = actualizarGuias();
        ResultadoPedidos completo = r.con(envio[0], envio[1], guias);
        ultimaRevision = completo;
        return completo;
    }

    /** Trae los pedidos pagados de los últimos días que todavía no estén en nuestro sistema. */
    public synchronized ResultadoPedidos traerPedidos(int dias) {
        LocalDateTime ahora = LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA);
        String q = "financial_status:paid created_at:>=" + LocalDate.now(ZoneOffset.UTC).minusDays(Math.max(dias, 1));
        JsonNode nodos;
        String aviso = null;
        try {
            try {
                nodos = shopify.graphql(consultaPedidos(true), Map.of("q", q)).path("orders").path("nodes");
            } catch (ShopifyServicio.ShopifyError e) {
                String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
                if (!(m.contains("access denied") || m.contains("customer") || m.contains("protected"))) throw e;
                // La app no tiene permiso para ver datos del cliente: se traen sin nombre/dirección
                nodos = shopify.graphql(consultaPedidos(false), Map.of("q", q)).path("orders").path("nodes");
                aviso = "Shopify no deja ver el nombre, el celular ni la dirección del cliente. En la app de Shopify activa el acceso "
                        + "a los datos del cliente (protected customer data) y vuelve a traer los pedidos; mientras tanto, mira esos datos en Shopify.";
            }
        } catch (ShopifyServicio.ShopifyError e) {
            ResultadoPedidos r = new ResultadoPedidos(ahora, 0, 0, 0, List.of(), e.getMessage(), null);
            ultimaRevision = r;
            return r;
        }

        Map<String, Integer> nuestros = new HashMap<>();
        for (ProductoTienda p : productoRepository.findAllByOrderByOrdenAscNombreAsc()) {
            if (p.isEnShopify()) nuestros.put(p.getShopifyProductoId(), p.getId());
        }

        int revisados = 0, nuevos = 0, ajenos = 0;
        List<String> traidos = new ArrayList<>();
        for (JsonNode n : nodos) {
            revisados++;
            String idShopify = n.path("id").asText("");
            if (idShopify.isEmpty() || !n.path("cancelledAt").isNull() && !n.path("cancelledAt").isMissingNode()) continue;
            if (ordenRepository.existsByShopifyPedidoId(idShopify)) continue;
            OrdenTienda o = convertirPedido(n, nuestros, ahora);
            if (o == null) { ajenos++; continue; }
            if (ordenRepository.existsByReferencia(o.getReferencia())) {
                o.setReferencia(o.getReferencia() + "-" + idShopify.replaceAll("\\D", "").replaceFirst("^.*(.{6})$", "$1"));
            }
            ordenRepository.save(o);
            nuevos++;
            traidos.add(o.getReferencia() + " · " + o.getNombreCliente());
        }
        ResultadoPedidos r = new ResultadoPedidos(ahora, revisados, nuevos, ajenos, traidos, null, aviso);
        ultimaRevision = r;
        return r;
    }

    /** Pasa un pedido de Shopify a una compra nuestra. Null si no trae ningún producto nuestro. */
    static OrdenTienda convertirPedido(JsonNode n, Map<String, Integer> nuestros, LocalDateTime ahora) {
        List<ItemOrdenTienda> items = new ArrayList<>();
        boolean traeAjenos = false;
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode li : n.path("lineItems").path("nodes")) {
            Integer productoId = nuestros.get(li.path("product").path("id").asText(""));
            if (productoId == null) { traeAjenos = true; continue; }
            int cantidad = Math.max(1, li.path("quantity").asInt(1));
            BigDecimal subtotal = plata(li.path("discountedTotalSet").path("shopMoney").path("amount"));
            ItemOrdenTienda it = new ItemOrdenTienda();
            it.setProductoId(productoId);
            String variante = li.path("variantTitle").asText("");
            String titulo = li.path("title").asText("Producto");
            it.setProductoNombre(variante.isBlank() || "Default Title".equals(variante) ? titulo : titulo + " – " + variante);
            it.setTelaNombre("");
            it.setColor("");
            it.setCantidad(cantidad);
            it.setSubtotal(subtotal);
            it.setPrecioUnitario(subtotal.divide(BigDecimal.valueOf(cantidad), 0, RoundingMode.HALF_UP));
            items.add(it);
            total = total.add(subtotal);
        }
        if (items.isEmpty()) return null;

        JsonNode env = n.path("shippingAddress"), cli = n.path("customer");
        String nombre = (texto(env, "firstName") + " " + texto(env, "lastName")).trim();
        if (nombre.isEmpty()) nombre = (texto(cli, "firstName") + " " + texto(cli, "lastName")).trim();
        if (nombre.isEmpty()) nombre = "Cliente de Shopify";
        String celular = primero(texto(env, "phone"), texto(cli, "phone"), texto(n, "phone"));
        String correo = primero(texto(n, "email"), texto(cli, "email"));
        String direccion = (texto(env, "address1") + " " + texto(env, "address2")).trim();
        String ciudad = texto(env, "city");
        if (!texto(env, "province").isEmpty()) ciudad = (ciudad.isEmpty() ? "" : ciudad + ", ") + texto(env, "province");

        String numero = texto(n, "name");   // "#1001"
        StringBuilder notas = new StringBuilder("Pedido ").append(numero.isEmpty() ? "" : numero + " ").append("de Shopify.");
        String nota = texto(n, "note");
        if (!nota.isEmpty()) notas.append(" Nota del cliente: ").append(nota).append(nota.matches(".*[.!?]$") ? "" : ".");
        if (traeAjenos) notas.append(" Este pedido también trae productos que no son nuestros (por ejemplo de Dropi): esos se manejan desde Shopify.");

        OrdenTienda o = new OrdenTienda();
        String digitos = numero.replaceAll("\\D", "");
        o.setReferencia("SHP-" + (digitos.isEmpty() ? n.path("id").asText("").replaceAll("\\D", "") : digitos));
        o.setOrigen(OrdenTienda.ORIGEN_SHOPIFY);
        o.setShopifyPedidoId(n.path("id").asText());
        o.setEstado(OrdenTienda.APROBADA);
        o.setMetodoPago("SHOPIFY");
        o.setNombreCliente(recortar(nombre, 255));
        o.setTelefono(recortar(celular, 255));
        o.setEmail(recortar(correo, 255));
        o.setCedula("");
        o.setDireccion(recortar(direccion, 255));
        o.setCiudad(recortar(ciudad, 255));
        o.setNotas(recortar(notas.toString(), 1000));
        LocalDateTime creado = fecha(texto(n, "createdAt"), ahora);
        o.setFechaCreacion(creado);
        o.setFechaPago(creado);
        o.setEstadoPedido(OrdenTienda.NUEVO);
        o.setFechaEstadoPedido(ahora);
        for (ItemOrdenTienda it : items) o.agregarItem(it);
        o.setTotal(total);
        return o;
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. TRAER LOS PRODUCTOS DE SHOPIFY (LOS DE DROPI)
    // ═══════════════════════════════════════════════════════════════

    private static final String CONSULTA_PRODUCTOS =
            "query productos($despues: String) { products(first: 50, after: $despues, query: \"status:active\") {"
            + " nodes { id title descriptionHtml featuredMedia { preview { image { url } } }"
            + "  variants(first: 20) { nodes { id title price inventoryQuantity inventoryPolicy"
            + "   inventoryItem { tracked unitCost { amount } } } } }"
            + " pageInfo { hasNextPage endCursor } } }";

    /** Una variante de Shopify ya leída. */
    record VarianteShopify(String productoId, String varianteId, String nombre, String descripcionHtml, String fotoUrl,
                           BigDecimal precio, BigDecimal costo, Integer stock, boolean disponible) {}

    /**
     * Trae los productos de Shopify que NO son nuestros (los que puso la app de Dropi).
     * Cada variante (talla, color...) queda como un producto de precio fijo en nuestra tienda.
     */
    public synchronized ResultadoImportacion traerProductos() {
        LocalDateTime ahora = LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA);
        List<ProductoTienda> todos = productoRepository.findAllByOrderByOrdenAscNombreAsc();
        Set<String> nuestrosEnShopify = new HashSet<>();
        Map<String, ProductoTienda> yaTraidos = new HashMap<>();
        for (ProductoTienda p : todos) {
            if (p.isEnShopify()) nuestrosEnShopify.add(p.getShopifyProductoId());
            if (p.isDeShopify() && p.getShopifyVarianteId() != null) yaTraidos.put(p.getShopifyVarianteId(), p);
        }

        List<VarianteShopify> variantes = new ArrayList<>();
        try {
            String despues = null;
            for (int pagina = 0; pagina < 20; pagina++) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("despues", despues);
                JsonNode prods = shopify.graphql(CONSULTA_PRODUCTOS, vars).path("products");
                for (JsonNode p : prods.path("nodes")) {
                    if (nuestrosEnShopify.contains(p.path("id").asText())) continue;   // es nuestro: no se trae
                    variantes.addAll(leerVariantes(p));
                }
                if (!prods.path("pageInfo").path("hasNextPage").asBoolean(false)) break;
                despues = prods.path("pageInfo").path("endCursor").asText(null);
            }
        } catch (ShopifyServicio.ShopifyError e) {
            ResultadoImportacion r = new ResultadoImportacion(ahora, 0, 0, 0, 0, List.of(), e.getMessage());
            ultimaImportacion = r;
            return r;
        }

        int nuevos = 0, actualizados = 0, agotados = 0, retirados = 0;
        List<String> detalle = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (VarianteShopify v : variantes) {
            if (v.precio() == null || v.precio().signum() <= 0) continue;
            vistas.add(v.varianteId());
            ProductoTienda p = yaTraidos.get(v.varianteId());
            boolean esNuevo = p == null;
            if (esNuevo) {
                p = new ProductoTienda();
                p.setProveedor(ProductoTienda.PROVEEDOR_SHOPIFY);
                p.setShopifyVarianteId(v.varianteId());
                p.setTipoPrecio(ProductoTienda.PRECIO_UNIDAD);
                p.setCategoria("OTROS");
                p.setConMando(false);
                p.setOrden(100);
                p.setSlug(slugUnico(v.nombre(), todos));
                p.setActivo(v.disponible());
                p.setOcultoPorStock(!v.disponible());
            }
            p.setShopifyProductoId(v.productoId());
            p.setNombre(recortar(v.nombre(), 120));
            String texto = textoDeHtml(v.descripcionHtml());
            p.setDescripcion(texto);
            p.setDescripcionCorta(recortar(texto.replaceAll("\\s+", " ").trim(), 160));
            p.setPrecioUnidad(v.precio());
            p.setCostoProveedor(v.costo());
            p.setStockShopify(v.stock());
            if (!esNuevo) {
                if (!v.disponible() && p.isActivo()) { p.setActivo(false); p.setOcultoPorStock(true); agotados++; detalle.add("Agotado en Shopify, se ocultó: " + p.getNombre()); }
                else if (v.disponible() && Boolean.TRUE.equals(p.getOcultoPorStock())) { p.setActivo(true); p.setOcultoPorStock(false); detalle.add("Volvió el stock, se muestra otra vez: " + p.getNombre()); }
            } else if (!v.disponible()) {
                agotados++;
            }
            actualizarFoto(p, v.fotoUrl());
            productoRepository.save(p);
            if (esNuevo) { nuevos++; todos.add(p); detalle.add("Nuevo: " + p.getNombre() + (p.isActivo() ? "" : " (agotado, queda oculto)")); }
            else actualizados++;
        }
        // Los que ya no están en Shopify (o ya no están activos allá) se ocultan
        for (ProductoTienda p : yaTraidos.values()) {
            if (vistas.contains(p.getShopifyVarianteId()) || !p.isActivo()) continue;
            p.setActivo(false);
            p.setOcultoPorStock(true);
            productoRepository.save(p);
            retirados++;
            detalle.add("Ya no está en Shopify, se ocultó: " + p.getNombre());
        }
        ResultadoImportacion r = new ResultadoImportacion(ahora, nuevos, actualizados, agotados, retirados, detalle, null);
        ultimaImportacion = r;
        return r;
    }

    static List<VarianteShopify> leerVariantes(JsonNode p) {
        List<VarianteShopify> lista = new ArrayList<>();
        JsonNode nodos = p.path("variants").path("nodes");
        boolean varias = nodos.size() > 1;
        String foto = p.path("featuredMedia").path("preview").path("image").path("url").asText("");
        for (JsonNode v : nodos) {
            String tituloVariante = v.path("title").asText("");
            String nombre = p.path("title").asText("Producto");
            if (varias && !tituloVariante.isBlank() && !"Default Title".equals(tituloVariante)) nombre += " – " + tituloVariante;
            boolean llevaInventario = v.path("inventoryItem").path("tracked").asBoolean(false);
            Integer stock = llevaInventario && v.has("inventoryQuantity") && !v.path("inventoryQuantity").isNull()
                    ? v.path("inventoryQuantity").asInt(0) : null;
            boolean disponible = stock == null || stock > 0 || "CONTINUE".equals(v.path("inventoryPolicy").asText(""));
            BigDecimal costo = plataONull(v.path("inventoryItem").path("unitCost").path("amount"));
            lista.add(new VarianteShopify(p.path("id").asText(), v.path("id").asText(), nombre,
                    p.path("descriptionHtml").asText(""), foto.isBlank() ? null : foto,
                    plataONull(v.path("price")), costo, stock, disponible));
        }
        return lista;
    }

    /** Baja la foto de Shopify si cambió (se guarda como las demás fotos de la tienda). */
    private void actualizarFoto(ProductoTienda p, String url) {
        if (url == null || url.equals(p.getShopifyImagenUrl())) return;
        ShopifyServicio.Foto foto = shopify.descargarFoto(url);
        if (foto == null) return;   // se intenta otra vez en la próxima revisión
        ImagenTienda img = new ImagenTienda();
        img.setContentType(foto.tipo());
        img.setDatos(foto.datos());
        Integer vieja = p.getImagenId();
        p.setImagenId(imagenRepository.save(img).getId());
        p.setShopifyImagenUrl(url);
        if (vieja != null && !vieja.equals(p.getImagenId())) {
            try { imagenRepository.deleteById(vieja); } catch (Exception ignorada) { /* no importa si ya no estaba */ }
        }
    }

    private String slugUnico(String nombre, List<ProductoTienda> todos) {
        String base = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "producto";
        if (base.length() > 80) base = base.substring(0, 80);
        Set<String> usados = new HashSet<>();
        for (ProductoTienda p : todos) if (p.getSlug() != null) usados.add(p.getSlug());
        String slug = base;
        int n = 2;
        while (usados.contains(slug) || productoRepository.existsBySlug(slug)) slug = base + "-" + n++;
        return slug;
    }

    /** "<p>Hola<br>mundo</p>" → "Hola\nmundo". */
    static String textoDeHtml(String html) {
        if (html == null || html.isBlank()) return "";
        String t = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</(p|div|h[1-6])>", "\n\n").replaceAll("(?i)</li>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        return t.replaceAll("[ \t]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").trim();
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. PEDIDOS DE AQUÍ CON PRODUCTOS DE DROPI → SHOPIFY
    // ═══════════════════════════════════════════════════════════════

    private static final String MUTACION_PEDIDO =
            "mutation crear($order: OrderCreateOrderInput!, $options: OrderCreateOptionsInput) {"
            + " orderCreate(order: $order, options: $options) { order { id name } userErrors { field message } } }";

    /**
     * Llamar cuando se aprueba el pago de una compra que trae productos de Shopify. El pedido se crea
     * en Shopify cuando el pago termine de guardarse; si falla, queda pendiente y se reintenta solo.
     */
    public void enviarPedidoCuandoSeGuarde(int ordenId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarPedidoSinFallar(ordenId);
                }
            });
        } else {
            enviarPedidoSinFallar(ordenId);
        }
    }

    private void enviarPedidoSinFallar(int ordenId) {
        try {
            enviarPedido(ordenId);
        } catch (Exception e) {
            log.error("[Shopify] No se pudo crear en Shopify el pedido #{}. Se reintenta solo.", ordenId, e);
        }
    }

    /**
     * Crea en Shopify (ya pagado) el pedido con los productos que vienen de Shopify. Devuelve null si
     * quedó bien o no había nada que hacer, o el mensaje de error (que también queda guardado en el pedido).
     */
    public synchronized String enviarPedido(int ordenId) {
        String[] error = new String[1];
        enTransaccion(() -> {
            OrdenTienda o = ordenRepository.findById(ordenId).orElse(null);
            if (o == null || !o.isPorEnviarAShopify()) return;
            if (!shopify.isConfigurado()) {
                error[0] = "Shopify no está configurado.";
                o.setShopifyError(error[0]);
                ordenRepository.save(o);
                return;
            }
            try {
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("order", pedidoParaShopify(o));
                vars.put("options", Map.of("sendReceipt", false, "sendFulfillmentReceipt", false));
                JsonNode r = shopify.graphql(MUTACION_PEDIDO, vars).path("orderCreate");
                String errores = erroresDeUsuario(r.path("userErrors"));
                String id = r.path("order").path("id").asText("");
                if (!errores.isEmpty() || id.isEmpty()) {
                    throw new ShopifyServicio.ShopifyError("Shopify no aceptó el pedido: " + (errores.isEmpty() ? "no devolvió el pedido." : errores));
                }
                o.setShopifyPedidoId(id);
                o.setShopifyPedidoNombre(recortar(r.path("order").path("name").asText(""), 40));
                o.setShopifyError(null);
            } catch (ShopifyServicio.ShopifyError e) {
                error[0] = e.getMessage();
                o.setShopifyError(recortar(e.getMessage(), 500));
            }
            ordenRepository.save(o);
        });
        return error[0];
    }

    /** Lo que se le manda a Shopify para crear el pedido (solo los productos que vienen de Shopify). */
    static Map<String, Object> pedidoParaShopify(OrdenTienda o) {
        List<Map<String, Object>> lineas = new ArrayList<>();
        for (ItemOrdenTienda it : o.getItems()) {
            if (!it.isDeShopify() || it.getShopifyVarianteId() == null) continue;
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("variantId", it.getShopifyVarianteId());
            l.put("quantity", it.getCantidad());
            BigDecimal unitario = it.getPrecioUnitario() != null ? it.getPrecioUnitario() : BigDecimal.ZERO;
            l.put("priceSet", Map.of("shopMoney", Map.of("amount", unitario.setScale(2, RoundingMode.HALF_UP).toPlainString(), "currencyCode", "COP")));
            lineas.add(l);
        }
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("lineItems", lineas);
        in.put("currency", "COP");
        in.put("financialStatus", "PAID");
        String correo = o.getEmail() == null ? "" : o.getEmail().trim();
        if (correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) in.put("email", correo);
        String celular = celularShopify(o.getTelefono());
        if (celular != null) in.put("phone", celular);
        String nombre = o.getNombreCliente() == null ? "" : o.getNombreCliente().trim();
        int espacio = nombre.indexOf(' ');
        Map<String, Object> dir = new LinkedHashMap<>();
        dir.put("firstName", espacio > 0 ? nombre.substring(0, espacio) : nombre);
        dir.put("lastName", espacio > 0 ? nombre.substring(espacio + 1) : nombre);
        dir.put("address1", recortar(o.getDireccion(), 255));
        dir.put("city", recortar(o.getCiudad(), 255));
        dir.put("countryCode", "CO");
        if (celular != null) dir.put("phone", celular);
        in.put("shippingAddress", dir);
        in.put("billingAddress", dir);
        in.put("note", recortar("Pedido " + o.getReferencia() + " de nuestra tienda virtual, ya pagado"
                + (TiendaServicio.MEDIO_ADDI.equals(o.getMetodoPago()) ? " con Addi" : " con Wompi")
                + ". Enviar SIN RECAUDO (no cobrar al entregar). Cédula: " + (o.getCedula() == null ? "" : o.getCedula())
                + (o.getNotas() == null || o.getNotas().isBlank() ? "" : ". Notas del cliente: " + o.getNotas()), 1000));
        in.put("tags", List.of("tienda-propia", "pagado", o.getReferencia()));
        return in;
    }

    /** "312 304 3450" → "+573123043450". Null si no parece un celular. */
    static String celularShopify(String telefono) {
        if (telefono == null) return null;
        String n = telefono.replaceAll("\\D", "");
        if (n.length() == 10 && n.startsWith("3")) return "+57" + n;
        if (n.length() == 12 && n.startsWith("573")) return "+" + n;
        return null;
    }

    /** Reintenta los pedidos de las últimas 2 semanas que todavía no se han podido crear en Shopify. [enviados, con error] */
    public int[] enviarPendientes() {
        int enviados = 0, conError = 0;
        if (!shopify.isConfigurado()) return new int[]{0, 0};
        LocalDateTime desde = LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA).minusDays(14);
        for (Integer id : ordenRepository.idsPorEnviarAShopify(desde)) {
            String error = enviarPedido(id);
            if (error == null) enviados++; else conError++;
        }
        return new int[]{enviados, conError};
    }

    // ── Guías: cuando Dropi despacha, Shopify tiene la transportadora y el número ──

    private static final String CONSULTA_GUIAS =
            "query guias($ids: [ID!]!) { nodes(ids: $ids) { ... on Order { id"
            + " fulfillments(first: 5) { trackingInfo(first: 3) { company number } } } } }";

    /** Trae las guías de los pedidos que ya se crearon en Shopify. Devuelve cuántas guías nuevas llegaron. */
    public int actualizarGuias() {
        if (!shopify.isConfigurado()) return 0;
        LocalDateTime desde = LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA).minusDays(45);
        List<Integer> ids = ordenRepository.idsEnviadosAShopifyAbiertos(desde);
        int nuevas = 0;
        for (int i = 0; i < ids.size(); i += 50) {
            List<Integer> grupo = ids.subList(i, Math.min(ids.size(), i + 50));
            Map<Integer, String> idsShopify = new LinkedHashMap<>();
            for (Integer id : grupo) {
                ordenRepository.findById(id).ifPresent(o -> idsShopify.put(id, o.getShopifyPedidoId()));
            }
            JsonNode nodos;
            try {
                nodos = shopify.graphql(CONSULTA_GUIAS, Map.of("ids", new ArrayList<>(idsShopify.values()))).path("nodes");
            } catch (ShopifyServicio.ShopifyError e) {
                log.warn("[Shopify] No se pudieron traer las guías: {}", e.getMessage());
                return nuevas;
            }
            Map<String, String> guiaPorPedido = new HashMap<>();
            for (JsonNode n : nodos) {
                String guia = guiaDe(n);
                if (guia != null) guiaPorPedido.put(n.path("id").asText(), guia);
            }
            for (Map.Entry<Integer, String> e : idsShopify.entrySet()) {
                String guia = guiaPorPedido.get(e.getValue());
                if (guia != null && ponerGuia(e.getKey(), guia)) nuevas++;
            }
        }
        return nuevas;
    }

    /** "Servientrega · guía 123456" a partir del despacho en Shopify. Null si todavía no hay guía. */
    static String guiaDe(JsonNode pedido) {
        for (JsonNode f : pedido.path("fulfillments")) {
            for (JsonNode t : f.path("trackingInfo")) {
                String numero = t.path("number").asText("").trim();
                if (numero.isEmpty()) continue;
                String empresa = t.path("company").asText("").trim();
                return (empresa.isEmpty() ? "" : empresa + " · ") + "guía " + numero;
            }
        }
        return null;
    }

    /** Pone la guía en los productos de Shopify del pedido; si todo el pedido es de Shopify, lo pasa a Despachado. */
    private boolean ponerGuia(int ordenId, String guia) {
        boolean[] cambio = new boolean[1];
        enTransaccion(() -> {
            OrdenTienda o = ordenRepository.findById(ordenId).orElse(null);
            if (o == null) return;
            LocalDateTime ahora = LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA);
            boolean todoDeShopify = true;
            for (ItemOrdenTienda it : o.getItems()) {
                if (!it.isDeShopify()) { todoDeShopify = false; continue; }
                if (!guia.equals(it.getPedidoProveedor())) {
                    it.setPedidoProveedor(recortar(guia, 200));
                    it.setFechaPedidoProveedor(ahora);
                    cambio[0] = true;
                }
            }
            String estado = o.getEstadoPedidoActual();
            if (todoDeShopify && (OrdenTienda.NUEVO.equals(estado) || OrdenTienda.EN_FABRICACION.equals(estado) || OrdenTienda.LISTO.equals(estado))) {
                o.setEstadoPedido(OrdenTienda.DESPACHADO);
                o.setFechaEstadoPedido(ahora);
                o.setGuiaEnvio(recortar(guia, 200));
                cambio[0] = true;
            }
            if (cambio[0]) ordenRepository.save(o);
        });
        return cambio[0];
    }

    private void enTransaccion(Runnable trabajo) {
        if (transaccionAparte == null) trabajo.run();
        else transaccionAparte.executeWithoutResult(estado -> trabajo.run());
    }

    // ── Ayudas ──

    private static String erroresDeUsuario(JsonNode errores) {
        if (!errores.isArray() || errores.size() == 0) return "";
        StringJoiner sj = new StringJoiner("; ");
        for (JsonNode e : errores) sj.add(e.path("message").asText(e.toString()));
        return ShopifyServicio.recortar(sj.toString(), 300);
    }

    private static BigDecimal plata(JsonNode n) {
        try { return new BigDecimal(n.asText("0").trim()).setScale(0, RoundingMode.HALF_UP); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static BigDecimal plataONull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        try { BigDecimal v = new BigDecimal(n.asText().trim()).setScale(0, RoundingMode.HALF_UP); return v.signum() > 0 ? v : null; }
        catch (Exception e) { return null; }
    }

    private static LocalDateTime fecha(String iso, LocalDateTime siNo) {
        try { return OffsetDateTime.parse(iso).atZoneSameInstant(OrdenTienda.ZONA_COLOMBIA).toLocalDateTime(); }
        catch (Exception e) { return siNo; }
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.path(campo);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
    }

    private static String primero(String... valores) {
        for (String v : valores) if (v != null && !v.isBlank()) return v;
        return "";
    }

    private static String recortar(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}