package Colcones_Persinas.proyecto_express.controlador.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.ProductoTiendaRepository;
import Colcones_Persinas.proyecto_express.servicio.tienda.DropiServicio;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla "Conexión con Dropi" (PRUEBA, SOLO LECTURA).
 *  - Probar el token consultando un producto de Dropi por su ID.
 *  - Revisar los productos de la tienda marcados como Dropi: stock y precio que tiene Dropi hoy.
 * No cambia nada ni en Dropi ni en la tienda.
 */
@Controller
@RequestMapping("/tienda-admin/dropi")
@PreAuthorize("hasAnyRole('TIENDA_ADMIN','ADMIN')")
public class DropiControlador {

    /** Para no hacerle demasiadas consultas seguidas a Dropi. */
    private static final int MAX_REVISAR = 30;

    /** Un producto de la tienda junto con lo que Dropi dice de él. */
    public record Revision(ProductoTienda producto, DropiServicio.ProductoDropi dropi) {}

    private final DropiServicio dropi;
    private final ProductoTiendaRepository productoRepository;

    public DropiControlador(DropiServicio dropi, ProductoTiendaRepository productoRepository) {
        this.dropi = dropi;
        this.productoRepository = productoRepository;
    }

    @GetMapping
    public String pantalla(Model model) {
        preparar(model);
        return "tienda-admin/dropi";
    }

    /** Consulta un producto de Dropi por su ID para ver si el token funciona. */
    @PostMapping("/probar")
    public String probar(@RequestParam(name = "id", required = false, defaultValue = "") String id, Model model) {
        preparar(model);
        model.addAttribute("idProbado", id.trim());
        model.addAttribute("prueba", dropi.consultarProducto(id));
        return "tienda-admin/dropi";
    }

    /** Consulta en Dropi cada producto de la tienda marcado como Dropi (solo lectura). */
    @PostMapping("/revisar")
    public String revisar(Model model) {
        List<ProductoTienda> deDropi = preparar(model);
        List<Revision> revisiones = new ArrayList<>();
        for (ProductoTienda p : deDropi) {
            if (revisiones.size() >= MAX_REVISAR) break;
            revisiones.add(new Revision(p, dropi.consultarProducto(p.getCodigoProveedor())));
        }
        model.addAttribute("revisiones", revisiones);
        model.addAttribute("revisionRecortada", deDropi.size() > MAX_REVISAR);
        return "tienda-admin/dropi";
    }

    private List<ProductoTienda> preparar(Model model) {
        List<ProductoTienda> deDropi = new ArrayList<>();
        for (ProductoTienda p : productoRepository.findAllByOrderByOrdenAscNombreAsc()) {
            if (p.isDeDropi()) deDropi.add(p);
        }
        model.addAttribute("configurado", dropi.isConfigurado());
        model.addAttribute("productosDropi", deDropi);
        return deDropi;
    }
}