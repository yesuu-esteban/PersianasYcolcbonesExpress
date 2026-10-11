package Colcones_Persinas.proyecto_express.controlador.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.ImagenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.TelaTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.ImagenTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.ProductoTiendaRepository;
import Colcones_Persinas.proyecto_express.servicio.tienda.WompiServicio;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/**
 * Administración de la tienda virtual (TIENDA_ADMIN y ADMIN): productos, telas con
 * sus precios por rollo y fotos. Un producto de precio fijo por unidad puede ser de Dropi:
 * se guarda su código y su costo allá para pedirlo cuando un cliente lo compre.
 *
 * Los pedidos están en OrdenesTiendaControlador y la contabilidad de la tienda en
 * ContabilidadTiendaControlador (las tres pantallas comparten el menú de la tienda).
 */
@Controller
@RequestMapping("/tienda-admin")
@PreAuthorize("hasAnyRole('TIENDA_ADMIN','ADMIN')")
public class TiendaAdminControlador {

    private static final long MAX_IMAGEN_BYTES = 5L * 1024 * 1024;

    private final ProductoTiendaRepository productoRepository;
    private final ImagenTiendaRepository imagenRepository;
    private final OrdenTiendaRepository ordenRepository;
    private final WompiServicio wompi;

    public TiendaAdminControlador(ProductoTiendaRepository productoRepository, ImagenTiendaRepository imagenRepository,
                                  OrdenTiendaRepository ordenRepository, WompiServicio wompi) {
        this.productoRepository = productoRepository;
        this.imagenRepository = imagenRepository;
        this.ordenRepository = ordenRepository;
        this.wompi = wompi;
    }

    // ─── Productos ────────────────────────────────────────────────────
    @GetMapping
    public String productos(Model model) {
        model.addAttribute("productos", productoRepository.findAllByOrderByOrdenAscNombreAsc());
        model.addAttribute("pagosActivos", wompi.isConfigurado());
        model.addAttribute("modoPruebas", wompi.isModoPruebas());
        model.addAttribute("eventosActivos", wompi.isEventosConfigurados());
        model.addAttribute("ordenesPagadas", ordenRepository.countByEstado(OrdenTienda.APROBADA));
        return "tienda-admin/productos";
    }

    @GetMapping("/producto/nuevo")
    public String nuevo(Model model) {
        ProductoTienda p = new ProductoTienda();
        p.agregarTela(new TelaTienda());
        model.addAttribute("producto", p);
        model.addAttribute("categorias", ProductoTienda.CATEGORIAS);
        return "tienda-admin/producto_form";
    }

    @GetMapping("/producto/{id}/editar")
    public String editar(@PathVariable int id, Model model, RedirectAttributes ra) {
        ProductoTienda p = productoRepository.findById(id).orElse(null);
        if (p == null) {
            ra.addFlashAttribute("error", "Ese producto ya no existe.");
            return "redirect:/tienda-admin";
        }
        model.addAttribute("producto", p);
        model.addAttribute("categorias", ProductoTienda.CATEGORIAS);
        return "tienda-admin/producto_form";
    }

    /**
     * Guarda un producto con sus telas. Se lee todo con MultiValueMap para que los
     * colores con comas ("Blanco, Gris") no se partan en varias filas.
     */
    @PostMapping("/producto/guardar")
    @Transactional
    public String guardar(@RequestParam MultiValueMap<String, String> f,
                          @RequestParam(name = "imagen", required = false) MultipartFile imagen,
                          Model model, RedirectAttributes ra) {
        String idTexto = f.getFirst("id");
        boolean esNuevo = idTexto == null || idTexto.isBlank();
        ProductoTienda p = esNuevo ? new ProductoTienda()
                : productoRepository.findById(Integer.parseInt(idTexto)).orElse(null);
        if (p == null) {
            ra.addFlashAttribute("error", "Ese producto ya no existe.");
            return "redirect:/tienda-admin";
        }

        String error = null;
        String nombre = texto(f, "nombre");
        if (nombre.isEmpty()) error = "Escribe el nombre del producto.";

        p.setNombre(nombre);
        String categoria = texto(f, "categoria");
        p.setCategoria(ProductoTienda.CATEGORIAS.containsKey(categoria) ? categoria : "OTROS");
        p.setDescripcionCorta(texto(f, "descripcionCorta"));
        p.setDescripcion(texto(f, "descripcion"));
        String tipoPrecio = texto(f, "tipoPrecio");
        p.setTipoPrecio(ProductoTienda.PRECIO_UNIDAD.equals(tipoPrecio) || ProductoTienda.PRECIO_RIEL.equals(tipoPrecio)
                ? tipoPrecio : ProductoTienda.PRECIO_M2);
        p.setPrecioUnidad(precio(texto(f, "precioUnidad")));
        p.setM2Minimo(decimal(texto(f, "m2Minimo"), 1.0));
        // Solo hay medida mínima: la tienda ya no pone medida máxima (anchoMaxCm / altoMaxCm no se usan)
        p.setAnchoMinCm(entero(texto(f, "anchoMinCm"), 30));
        p.setAltoMinCm(entero(texto(f, "altoMinCm"), 30));
        p.setConMando("true".equals(f.getFirst("conMando")));
        p.setOfreceCabezal("true".equals(f.getFirst("ofreceCabezal")));
        p.setPrecioCabezalMetro(precio(texto(f, "precioCabezalMetro")));
        p.setOfreceEnrolladoContrario("true".equals(f.getFirst("ofreceEnrolladoContrario")));
        p.setPrecioRielBastonMetro(precio(texto(f, "precioRielBastonMetro")));
        p.setPrecioRielControlMetro(precio(texto(f, "precioRielControlMetro")));
        // ── Quién lo despacha: nosotros o Dropi (solo productos de precio fijo por unidad) ──
        boolean deDropi = ProductoTienda.PROVEEDOR_DROPI.equals(texto(f, "proveedor"))
                && ProductoTienda.PRECIO_UNIDAD.equals(p.getTipoPrecio());
        if (deDropi) {
            p.setProveedor(ProductoTienda.PROVEEDOR_DROPI);
            p.setCodigoProveedor(corto(texto(f, "codigoProveedor"), 80));
            p.setCostoProveedor(precio(texto(f, "costoProveedor")));
            p.setEnlaceProveedor(corto(texto(f, "enlaceProveedor"), 500));
        } else {
            p.setProveedor(ProductoTienda.PROVEEDOR_PROPIO);
            p.setCodigoProveedor(null);
            p.setCostoProveedor(null);
            p.setEnlaceProveedor(null);
        }
        p.setActivo("true".equals(f.getFirst("activo")));
        p.setDestacado("true".equals(f.getFirst("destacado")));
        p.setOrden(entero(texto(f, "orden"), 0));

        // ── Telas ──
        List<String> ids = lista(f, "telaId"), nombres = lista(f, "telaNombre"), colores = lista(f, "telaColores"),
                p183 = lista(f, "telaP183"), p250 = lista(f, "telaP250"), p300 = lista(f, "telaP300"),
                activas = lista(f, "telaActiva");
        Map<Integer, TelaTienda> existentes = new HashMap<>();
        for (TelaTienda t : p.getTelas()) existentes.put(t.getId(), t);
        List<TelaTienda> conservar = new ArrayList<>();
        for (int i = 0; i < nombres.size(); i++) {
            String nombreTela = nombres.get(i).trim();
            if (nombreTela.isEmpty()) continue;
            Integer idTela = i < ids.size() && !ids.get(i).isBlank() ? Integer.valueOf(ids.get(i)) : null;
            TelaTienda t = idTela != null && existentes.containsKey(idTela) ? existentes.get(idTela) : new TelaTienda();
            t.setNombre(nombreTela);
            t.setColores(i < colores.size() ? colores.get(i).trim() : "");
            t.setPrecioRollo183(i < p183.size() ? precio(p183.get(i)) : null);
            t.setPrecioRollo250(i < p250.size() ? precio(p250.get(i)) : null);
            t.setPrecioRollo300(i < p300.size() ? precio(p300.get(i)) : null);
            t.setActiva(i >= activas.size() || !"false".equals(activas.get(i)));
            conservar.add(t);
        }
        p.getTelas().removeIf(t -> !conservar.contains(t));
        for (TelaTienda t : conservar) if (!p.getTelas().contains(t)) p.agregarTela(t);

        if (error == null && p.isPorMetro() && p.getTelasActivas().stream().noneMatch(t -> t.getPrecioMinimo() != null)) {
            error = "Agrega al menos una tela activa con precio por m² en algún rollo.";
        }
        if (error == null && ProductoTienda.PRECIO_UNIDAD.equals(p.getTipoPrecio())
                && (p.getPrecioUnidad() == null || p.getPrecioUnidad().signum() <= 0)) {
            error = "Escribe el precio por unidad.";
        }
        if (error == null && deDropi) {
            error = errorDropi(p);
        }
        if (error == null && p.isRiel() && !p.isConBaston() && !p.isConControl()) {
            error = "Escribe el precio por metro con bastón, con control o los dos.";
        }
        if (error == null && p.isCabezalDisponible()
                && (p.getPrecioCabezalMetro() == null || p.getPrecioCabezalMetro().signum() <= 0)) {
            error = "Escribe cuánto vale el cabezal por metro de ancho (o quita la opción de cabezal).";
        }
        if (error == null && (p.getAnchoMinCm() <= 0 || p.getAltoMinCm() <= 0)) {
            error = "Revisa las medidas mínimas: escríbelas en metros, por ejemplo 0,30.";
        }

        // ── Foto ──
        if (error == null && imagen != null && !imagen.isEmpty()) {
            String tipo = imagen.getContentType();
            if (tipo == null || !tipo.startsWith("image/")) error = "La foto debe ser una imagen (JPG, PNG o WEBP).";
            else if (imagen.getSize() > MAX_IMAGEN_BYTES) error = "La foto pesa más de 5 MB. Redúcela e inténtalo de nuevo.";
        }

        if (error != null) {
            // No guardar nada: los cambios solo se muestran en el formulario para corregirlos.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            if (p.getTelas().isEmpty()) p.agregarTela(new TelaTienda());
            model.addAttribute("producto", p);
            model.addAttribute("categorias", ProductoTienda.CATEGORIAS);
            model.addAttribute("error", error);
            return "tienda-admin/producto_form";
        }

        p.setSlug(slugUnico(nombre, esNuevo ? null : p.getId()));

        Integer imagenVieja = p.getImagenId();
        boolean quitar = "true".equals(f.getFirst("quitarImagen"));
        try {
            if (imagen != null && !imagen.isEmpty()) {
                ImagenTienda img = new ImagenTienda();
                img.setContentType(imagen.getContentType());
                img.setDatos(imagen.getBytes());
                p.setImagenId(imagenRepository.save(img).getId());
            } else if (quitar) {
                p.setImagenId(null);
            }
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            model.addAttribute("producto", p);
            model.addAttribute("categorias", ProductoTienda.CATEGORIAS);
            model.addAttribute("error", "No se pudo leer la foto. Inténtalo con otra imagen.");
            return "tienda-admin/producto_form";
        }

        productoRepository.save(p);
        if (imagenVieja != null && !imagenVieja.equals(p.getImagenId())) imagenRepository.deleteById(imagenVieja);

        ra.addFlashAttribute("mensaje", "Producto \"" + p.getNombre() + "\" guardado.");
        return "redirect:/tienda-admin";
    }

    @PostMapping("/producto/{id}/activar")
    public String activar(@PathVariable int id, RedirectAttributes ra) {
        productoRepository.findById(id).ifPresent(p -> {
            p.setActivo(!p.isActivo());
            productoRepository.save(p);
            ra.addFlashAttribute("mensaje", "\"" + p.getNombre() + "\" " + (p.isActivo() ? "ahora se ve en la tienda." : "ya no se ve en la tienda."));
        });
        return "redirect:/tienda-admin";
    }

    @PostMapping("/producto/{id}/eliminar")
    @Transactional
    public String eliminar(@PathVariable int id, RedirectAttributes ra) {
        productoRepository.findById(id).ifPresent(p -> {
            Integer img = p.getImagenId();
            productoRepository.delete(p);
            if (img != null) imagenRepository.deleteById(img);
            ra.addFlashAttribute("mensaje", "Producto \"" + p.getNombre() + "\" eliminado. Las compras ya hechas no se afectan.");
        });
        return "redirect:/tienda-admin";
    }

    // ─── Helpers ──────────────────────────────────────────────────────
    private static String texto(MultiValueMap<String, String> f, String k) {
        String v = f.getFirst(k);
        return v == null ? "" : v.trim();
    }

    private static List<String> lista(MultiValueMap<String, String> f, String k) {
        List<String> v = f.get(k);
        return v != null ? v : List.of();
    }

    /** "51.200", "$51,200" o "51200" → 51200. Vacío → null. */
    private static BigDecimal precio(String s) {
        if (s == null) return null;
        String digitos = s.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : new BigDecimal(digitos);
    }

    /** Revisa los datos de Dropi de un producto. Devuelve el error o null si todo está bien. */
    static String errorDropi(ProductoTienda p) {
        if (p.getCodigoProveedor() == null || p.getCodigoProveedor().isEmpty()) {
            return "Escribe el código del producto en Dropi (con ese código lo pides allá cuando te compren).";
        }
        if (p.getCostoProveedor() == null || p.getCostoProveedor().signum() <= 0) {
            return "Escribe cuánto te cobra Dropi por cada unidad.";
        }
        if (p.getPrecioUnidad() != null && p.getCostoProveedor().compareTo(p.getPrecioUnidad()) >= 0) {
            return "El precio de venta debe ser mayor que lo que te cobra Dropi; si no, pierdes plata en cada venta.";
        }
        String enlace = p.getEnlaceProveedor();
        if (enlace != null && !enlace.isEmpty()
                && !(enlace.toLowerCase(Locale.ROOT).startsWith("http://") || enlace.toLowerCase(Locale.ROOT).startsWith("https://"))) {
            return "El enlace de Dropi debe empezar por https:// (cópialo completo de la barra del navegador), o déjalo vacío.";
        }
        return null;
    }

    /** Texto recortado a un largo máximo; vacío → null. */
    private static String corto(String s, int max) {
        if (s == null || s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static int entero(String s, int porDefecto) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return porDefecto; }
    }

    private static double decimal(String s, double porDefecto) {
        try { return Double.parseDouble(s.replace(',', '.')); } catch (Exception e) { return porDefecto; }
    }

    private String slugUnico(String nombre, Integer id) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "producto";
        String slug = base;
        int n = 2;
        while (id == null ? productoRepository.existsBySlug(slug) : productoRepository.existsBySlugAndIdNot(slug, id)) {
            slug = base + "-" + n++;
        }
        return slug;
    }
}