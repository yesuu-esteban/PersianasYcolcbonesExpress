package Colcones_Persinas.proyecto_express.controlador.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.ProductoTiendaRepository;
import Colcones_Persinas.proyecto_express.servicio.tienda.ShopifyServicio;
import Colcones_Persinas.proyecto_express.servicio.tienda.ShopifyTiendaServicio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla "Conexión con Shopify":
 *  - Probar la conexión.
 *  - Enviar a Shopify los productos de precio fijo que son nuestros.
 *  - Traer de Shopify los productos de Dropi (también se traen solos cada 30 minutos).
 *  - Pedidos: traer los pagados en Shopify, mandar a Shopify los de Dropi pendientes y traer guías
 *    (también se hace solo cada 10 minutos).
 */
@Controller
@RequestMapping("/tienda-admin/shopify")
@PreAuthorize("hasAnyRole('TIENDA_ADMIN','ADMIN')")
public class ShopifyControlador {

    private final ShopifyServicio shopify;
    private final ShopifyTiendaServicio sincronizacion;
    private final ProductoTiendaRepository productoRepository;
    private final OrdenTiendaRepository ordenRepository;

    /** Dominio propio de la tienda (APP_DOMINIO_TIENDA), si existe. Se usa para las fotos. */
    @Value("${app.dominio-tienda:}")
    private String dominioTienda;

    public ShopifyControlador(ShopifyServicio shopify, ShopifyTiendaServicio sincronizacion,
                              ProductoTiendaRepository productoRepository, OrdenTiendaRepository ordenRepository) {
        this.shopify = shopify;
        this.sincronizacion = sincronizacion;
        this.productoRepository = productoRepository;
        this.ordenRepository = ordenRepository;
    }

    @GetMapping
    public String pantalla(Model model) {
        preparar(model);
        return "tienda-admin/shopify";
    }

    @PostMapping("/probar")
    public String probar(Model model) {
        try {
            model.addAttribute("conexionOk", sincronizacion.probarConexion());
        } catch (ShopifyServicio.ShopifyError e) {
            model.addAttribute("conexionError", e.getMessage());
        }
        preparar(model);
        return "tienda-admin/shopify";
    }

    @PostMapping("/enviar")
    public String enviar(Model model) {
        model.addAttribute("resultadosProductos", sincronizacion.enviarProductos(urlPublica()));
        preparar(model);
        return "tienda-admin/shopify";
    }

    /** Trae ahora los pedidos de Shopify, manda a Shopify los pendientes y trae las guías. */
    @PostMapping("/traer")
    public String traer(Model model) {
        model.addAttribute("resultadoPedidos", sincronizacion.sincronizarPedidos(7));
        preparar(model);
        return "tienda-admin/shopify";
    }

    /** Trae ahora los productos de Shopify (los de Dropi): precio, costo, stock y fotos. */
    @PostMapping("/productos")
    public String traerProductos(Model model) {
        model.addAttribute("resultadoImportacion", sincronizacion.traerProductos());
        preparar(model);
        return "tienda-admin/shopify";
    }

    private void preparar(Model model) {
        List<ProductoTienda> paraShopify = new ArrayList<>();
        List<ProductoTienda> deShopify = new ArrayList<>();
        int aLaMedida = 0;
        for (ProductoTienda p : productoRepository.findAllByOrderByOrdenAscNombreAsc()) {
            if (p.isParaShopify()) paraShopify.add(p);
            else if (p.isDeShopify()) deShopify.add(p);
            else aLaMedida++;
        }
        model.addAttribute("configurado", shopify.isConfigurado());
        model.addAttribute("faltante", shopify.getFaltante());
        model.addAttribute("tiendaShopify", shopify.getTienda());
        model.addAttribute("paraShopify", paraShopify);
        model.addAttribute("aLaMedida", aLaMedida);
        model.addAttribute("deShopify", deShopify);
        model.addAttribute("porEnviar", ordenRepository.contarPorEnviarAShopify());
        model.addAttribute("ultimaImportacion", sincronizacion.getUltimaImportacion());
        model.addAttribute("ultimaRevision", sincronizacion.getUltimaRevision());
        model.addAttribute("urlFotos", urlPublica());
    }

    /** Dirección pública de nuestra tienda, para que Shopify descargue las fotos. */
    private String urlPublica() {
        if (dominioTienda != null && !dominioTienda.isBlank()) {
            return "https://" + dominioTienda.split(",")[0].trim();
        }
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        // Railway atiende por https aunque internamente llegue como http
        if (base.startsWith("http://") && !base.contains("localhost") && !base.contains("127.0.0.1")) {
            base = "https://" + base.substring("http://".length());
        }
        return base.replaceAll("/+$", "");
    }
}