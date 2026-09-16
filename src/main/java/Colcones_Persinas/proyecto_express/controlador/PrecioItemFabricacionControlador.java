package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.PrecioItemFabricacion;
import Colcones_Persinas.proyecto_express.modelo.TipoCalculoPrecio;
import Colcones_Persinas.proyecto_express.repository.PrecioItemFabricacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/inventario/precios")
public class PrecioItemFabricacionControlador {

    @Autowired
    private PrecioItemFabricacionRepository repositorio;

    @PreAuthorize("hasAnyRole('FABRICA','ADMIN')")
    @GetMapping
    public String verPrecios(Model model) {
        model.addAttribute("items", repositorio.findAllByOrderByNombreAsc());
        model.addAttribute("tiposCalculo", TipoCalculoPrecio.values());
        return "inventario/precios";
    }

    @PreAuthorize("hasAnyRole('FABRICA','ADMIN')")
    @PostMapping("/nuevo")
    public String crearItem(
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam TipoCalculoPrecio tipoCalculo,
            @RequestParam BigDecimal precioUnitario,
            RedirectAttributes redirectAttributes) {

        if (nombre == null || nombre.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "El nombre del ítem es obligatorio.");
            return "redirect:/inventario/precios";
        }
        if (repositorio.findByNombreIgnoreCase(nombre.trim()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Ya existe un ítem con ese nombre.");
            return "redirect:/inventario/precios";
        }

        PrecioItemFabricacion item = new PrecioItemFabricacion();
        item.setNombre(nombre.trim());
        item.setDescripcion(descripcion != null ? descripcion.trim() : "");
        item.setTipoCalculo(tipoCalculo);
        item.setPrecioUnitario(precioUnitario != null ? precioUnitario : BigDecimal.ZERO);
        item.setActualizadoPor(nombreUsuarioActual());
        repositorio.save(item);

        redirectAttributes.addFlashAttribute("mensaje", "Ítem \"" + nombre + "\" creado correctamente.");
        return "redirect:/inventario/precios";
    }

    @PreAuthorize("hasAnyRole('FABRICA','ADMIN')")
    @PostMapping("/{id}/actualizar")
    public String actualizarItem(
            @PathVariable("id") int id,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam TipoCalculoPrecio tipoCalculo,
            @RequestParam BigDecimal precioUnitario,
            RedirectAttributes redirectAttributes) {

        PrecioItemFabricacion item = repositorio.findById(id).orElseThrow();
        item.setNombre(nombre.trim());
        item.setDescripcion(descripcion != null ? descripcion.trim() : "");
        item.setTipoCalculo(tipoCalculo);
        item.setPrecioUnitario(precioUnitario != null ? precioUnitario : BigDecimal.ZERO);
        item.setActualizadoPor(nombreUsuarioActual());
        repositorio.save(item);

        redirectAttributes.addFlashAttribute("mensaje", "Precio de \"" + item.getNombre() + "\" actualizado.");
        return "redirect:/inventario/precios";
    }

    @PreAuthorize("hasAnyRole('FABRICA','ADMIN')")
    @PostMapping("/{id}/eliminar")
    public String eliminarItem(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            repositorio.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Ítem eliminado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar: " + e.getMessage());
        }
        return "redirect:/inventario/precios";
    }

    private String nombreUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "desconocido";
    }
}