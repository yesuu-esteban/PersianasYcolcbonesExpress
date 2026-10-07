package Colcones_Persinas.proyecto_express.controlador.tienda;

import Colcones_Persinas.proyecto_express.config.LogoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.ImagenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.TelaTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.ImagenTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.ProductoTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.TelaTiendaRepository;
import Colcones_Persinas.proyecto_express.servicio.tienda.AddiServicio;
import Colcones_Persinas.proyecto_express.servicio.tienda.PrecioTiendaServicio;
import Colcones_Persinas.proyecto_express.servicio.tienda.TiendaServicio;
import Colcones_Persinas.proyecto_express.servicio.tienda.WompiServicio;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Tienda virtual PÚBLICA (no requiere iniciar sesión): catálogo, cotizador por medidas,
 * carrito, pago con Wompi o a cuotas con Addi, y página de resultado. Los avisos
 * automáticos también llegan aquí: POST /tienda/wompi/eventos y POST /tienda/addi/aviso.
 */
@Controller
@RequestMapping("/tienda")
public class TiendaControlador {

    private final ProductoTiendaRepository productoRepository;
    private final TelaTiendaRepository telaRepository;
    private final ImagenTiendaRepository imagenRepository;
    private final OrdenTiendaRepository ordenRepository;
    private final TiendaServicio tiendaServicio;
    private final PrecioTiendaServicio precioServicio;
    private final WompiServicio wompi;
    private final AddiServicio addi;
    private final ObjectMapper objectMapper;

    /** Número de WhatsApp para asesoría (con 57 adelante, sin espacios). */
    @Value("${tienda.whatsapp:573041354963}")
    private String whatsapp;

    @Value("${tienda.telefonos:304 135 4963, 312 206 5950, 314 866 0215}")
    private String telefonos;

    public TiendaControlador(ProductoTiendaRepository productoRepository, TelaTiendaRepository telaRepository,
                             ImagenTiendaRepository imagenRepository, OrdenTiendaRepository ordenRepository,
                             TiendaServicio tiendaServicio, PrecioTiendaServicio precioServicio,
                             WompiServicio wompi, AddiServicio addi, ObjectMapper objectMapper) {
        this.productoRepository = productoRepository;
        this.telaRepository = telaRepository;
        this.imagenRepository = imagenRepository;
        this.ordenRepository = ordenRepository;
        this.tiendaServicio = tiendaServicio;
        this.precioServicio = precioServicio;
        this.wompi = wompi;
        this.addi = addi;
        this.objectMapper = objectMapper;
    }

    /** Datos que usan todas las páginas de la tienda (encabezado y pie). */
    @ModelAttribute
    public void datosComunes(Model model) {
        model.addAttribute("whatsapp", whatsapp);
        model.addAttribute("telefonos", Arrays.stream(telefonos.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        model.addAttribute("categorias", ProductoTienda.CATEGORIAS);
    }

    // ═══════════════════════════════════════════════════════════════
    // PÁGINAS
    // ═══════════════════════════════════════════════════════════════

    @GetMapping({"", "/"})
    public String catalogo(@RequestParam(required = false) String categoria, Model model) {
        boolean filtrar = categoria != null && ProductoTienda.CATEGORIAS.containsKey(categoria);
        model.addAttribute("productos", filtrar
                ? productoRepository.findByActivoTrueAndCategoriaOrderByDestacadoDescOrdenAscNombreAsc(categoria)
                : productoRepository.findByActivoTrueOrderByDestacadoDescOrdenAscNombreAsc());
        model.addAttribute("categoriaSel", filtrar ? categoria : null);
        return "tienda/catalogo";
    }

    @GetMapping("/producto/{slug}")
    public String producto(@PathVariable String slug, Model model) {
        ProductoTienda p = productoRepository.findBySlugAndActivoTrue(slug).orElse(null);
        if (p == null) return "redirect:/tienda";
        model.addAttribute("producto", p);
        return "tienda/producto";
    }

    @GetMapping("/carrito")
    public String carrito() {
        return "tienda/carrito";
    }

    @GetMapping("/checkout")
    public String checkout(Model model) {
        ponerMediosDePago(model);
        return "tienda/checkout";
    }

    /** Le dice a la página de pago qué botones mostrar: Wompi, Addi o los dos. */
    private void ponerMediosDePago(Model model) {
        model.addAttribute("wompiActivo", wompi.isConfigurado());
        model.addAttribute("addiActivo", addi.isConfigurado());
        model.addAttribute("pagosActivos", wompi.isConfigurado() || addi.isConfigurado());
    }

    /** El cliente elige el medio con el botón que oprime: "wompi" (por defecto) o "addi". */
    @PostMapping("/checkout")
    public String pagar(@RequestParam Map<String, String> datos, Model model) {
        ponerMediosDePago(model);
        model.addAttribute("datos", datos);
        boolean conAddi = "addi".equals(datos.get("medio"));

        if (conAddi && !addi.isConfigurado()) {
            model.addAttribute("error", "El pago con Addi no está disponible en este momento. Elige otro medio de pago.");
            return "tienda/checkout";
        }
        if (!conAddi && !wompi.isConfigurado()) {
            model.addAttribute("error", "Los pagos en línea todavía no están activos. Envíanos tu pedido por WhatsApp y te ayudamos a completarlo.");
            return "tienda/checkout";
        }
        if (!"on".equals(datos.get("autorizo"))) {
            model.addAttribute("error", "Debes autorizar el tratamiento de tus datos para poder enviarte el pedido.");
            return "tienda/checkout";
        }

        List<TiendaServicio.ItemCarrito> items;
        try {
            items = objectMapper.readValue(datos.getOrDefault("carrito", "[]"),
                    new TypeReference<List<TiendaServicio.ItemCarrito>>() {});
        } catch (Exception e) {
            model.addAttribute("error", "No pudimos leer tu carrito. Vuelve al carrito e inténtalo de nuevo.");
            return "tienda/checkout";
        }

        OrdenTienda orden;
        try {
            orden = tiendaServicio.crearOrden(new TiendaServicio.DatosCliente(
                    datos.get("nombre"), datos.get("cedula"), datos.get("email"), datos.get("telefono"),
                    datos.get("direccion"), datos.get("ciudad"), datos.get("notas")), items);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "tienda/checkout";
        }

        String urlRegreso = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/tienda/pedido/{ref}").buildAndExpand(orden.getReferencia()).toUriString();
        if (!conAddi) return "redirect:" + wompi.urlCheckout(orden, urlRegreso);

        // ── Addi ──
        orden.setMetodoPago(TiendaServicio.MEDIO_ADDI);
        ordenRepository.save(orden);
        try {
            String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            return "redirect:" + addi.crearSolicitud(orden, base + "/tienda/addi/aviso", urlRegreso, base + "/tienda/logo.png");
        } catch (AddiServicio.AddiException e) {
            // No se alcanzó a enviar a Addi: no se deja una compra "esperando pago" que nadie va a pagar
            ordenRepository.delete(orden);
            model.addAttribute("error", e.getMessage());
            return "tienda/checkout";
        }
    }

    /**
     * Página a la que Wompi o Addi devuelven al cliente al terminar. Wompi agrega ?id=<transacción>:
     * si la orden sigue pendiente, se consulta el resultado directamente a Wompi.
     * Con Addi no hay nada que consultar: el resultado llega por su aviso (/tienda/addi/aviso).
     */
    @GetMapping("/pedido/{referencia}")
    public String resultado(@PathVariable String referencia,
                            @RequestParam(name = "id", required = false) String transaccionId,
                            Model model) {
        OrdenTienda orden = ordenRepository.findByReferencia(referencia).orElse(null);
        if (orden == null) {
            model.addAttribute("referencia", referencia);
            return "tienda/resultado";
        }
        if (transaccionId != null && !OrdenTienda.APROBADA.equals(orden.getEstado())) {
            wompi.consultarTransaccion(transaccionId).ifPresent(tx -> {
                if (referencia.equals(tx.path("reference").asText())) {
                    tiendaServicio.aplicarPago(referencia, tx.path("id").asText(), tx.path("status").asText(),
                            tx.path("amount_in_cents").asLong(), tx.path("payment_method_type").asText(null));
                }
            });
            orden = ordenRepository.findByReferencia(referencia).orElse(orden);
        }
        model.addAttribute("orden", orden);
        model.addAttribute("transaccionId", transaccionId);
        return "tienda/resultado";
    }

    @GetMapping("/logo.png")
    public ResponseEntity<byte[]> logo() {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(LogoTienda.PNG);
    }

    @GetMapping("/img/{id}")
    public ResponseEntity<byte[]> imagen(@PathVariable int id) {
        ImagenTienda img = imagenRepository.findById(id).orElse(null);
        if (img == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(img.getDatos());
    }

    // ═══════════════════════════════════════════════════════════════
    // API (la usa el JavaScript de las páginas)
    // ═══════════════════════════════════════════════════════════════

    /** Precio de una configuración (producto + tela + medidas). */
    @GetMapping("/api/cotizar")
    @ResponseBody
    public Map<String, Object> cotizar(@RequestParam int productoId,
                                       @RequestParam(required = false) Integer telaId,
                                       @RequestParam(required = false) Integer ancho,
                                       @RequestParam(required = false) Integer alto,
                                       @RequestParam(required = false, defaultValue = "1") Integer cantidad) {
        ProductoTienda p = productoRepository.findById(productoId).orElse(null);
        TelaTienda tela = telaId != null ? telaRepository.findById(telaId).orElse(null) : null;
        PrecioTiendaServicio.Cotizacion c = precioServicio.cotizar(p, tela, ancho, alto, cantidad);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", c.ok());
        r.put("mensaje", c.mensaje());
        r.put("precioUnitario", c.precioUnitario());
        r.put("subtotal", c.subtotal());
        r.put("m2", c.m2());
        r.put("m2Reales", c.m2Reales());
        r.put("rollo", c.rollo());
        return r;
    }

    /** Cotiza todas las líneas del carrito guardado en el navegador. */
    @PostMapping("/api/carrito")
    @ResponseBody
    public Map<String, Object> cotizarCarrito(@RequestBody List<TiendaServicio.ItemCarrito> items) {
        List<TiendaServicio.LineaCotizada> lineas = tiendaServicio.cotizarCarrito(items);
        java.math.BigDecimal total = lineas.stream().filter(TiendaServicio.LineaCotizada::ok)
                .map(TiendaServicio.LineaCotizada::subtotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("lineas", lineas);
        r.put("total", total);
        r.put("todoOk", !lineas.isEmpty() && lineas.stream().allMatch(TiendaServicio.LineaCotizada::ok));
        return r;
    }

    // ═══════════════════════════════════════════════════════════════
    // AVISO AUTOMÁTICO DE WOMPI (webhook)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Wompi llama aquí cada vez que cambia el estado de una transacción.
     * En el panel de Wompi → Desarrolladores → "URL de eventos" se pone:
     *   https://TU-DOMINIO/tienda/wompi/eventos
     */
    @PostMapping("/wompi/eventos")
    @ResponseBody
    public ResponseEntity<String> eventoWompi(@RequestBody String cuerpo) {
        try {
            JsonNode evento = objectMapper.readTree(cuerpo);
            if (!wompi.eventoValido(evento)) {
                System.err.println("[Wompi] Evento con firma inválida, ignorado.");
                return ResponseEntity.badRequest().body("firma invalida");
            }
            if ("transaction.updated".equals(evento.path("event").asText())) {
                JsonNode tx = evento.path("data").path("transaction");
                tiendaServicio.aplicarPago(tx.path("reference").asText(), tx.path("id").asText(),
                        tx.path("status").asText(), tx.path("amount_in_cents").asLong(),
                        tx.path("payment_method_type").asText(null));
            }
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            System.err.println("[Wompi] Error procesando evento: " + e.getMessage());
            return ResponseEntity.internalServerError().body("error");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AVISO AUTOMÁTICO DE ADDI
    // ═══════════════════════════════════════════════════════════════

    /**
     * Addi llama aquí cuando decide una solicitud (aprobada, rechazada, abandonada).
     * Esta dirección no se configura en ningún panel: la tienda se la envía a Addi con cada compra.
     * El aviso trae un usuario y una clave que solo Addi conoce; si no coinciden, se rechaza.
     * Addi espera que se le conteste con el mismo contenido que envió.
     */
    @PostMapping("/addi/aviso")
    @ResponseBody
    public ResponseEntity<String> avisoAddi(@RequestBody(required = false) String cuerpo,
                                            @RequestHeader(value = "Authorization", required = false) String autorizacion) {
        if (!addi.avisoAutorizado(autorizacion)) {
            System.err.println("[Addi] Aviso rechazado: no trae el usuario y la clave de Addi.");
            return ResponseEntity.status(401).header("WWW-Authenticate", "Basic realm=\"addi\"").build();
        }
        try {
            String contenido = cuerpo == null ? "" : cuerpo;
            JsonNode aviso = objectMapper.readTree(contenido.isBlank() ? "{}" : contenido);
            String referencia = aviso.path("orderId").asText("");
            OrdenTienda orden = ordenRepository.findByReferencia(referencia).orElse(null);

            if (orden == null || !TiendaServicio.MEDIO_ADDI.equals(orden.getMetodoPago())) {
                System.err.println("[Addi] Aviso de una compra que no es de Addi o no existe: " + referencia);
            } else {
                tiendaServicio.aplicarPago(referencia, aviso.path("applicationId").asText(null),
                        AddiServicio.estadoComoWompi(aviso.path("status").asText("")),
                        AddiServicio.centavos(aviso.path("approvedAmount"), orden.getTotalEnCentavos()),
                        TiendaServicio.MEDIO_ADDI);
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(contenido);
        } catch (Exception e) {
            System.err.println("[Addi] Error procesando el aviso: " + e.getMessage());
            return ResponseEntity.internalServerError().body("error");
        }
    }
}