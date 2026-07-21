package Colcones_Persinas.proyecto_express.controlador;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

import Colcones_Persinas.proyecto_express.modelo.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.DetallePedidoTienda;
import Colcones_Persinas.proyecto_express.repository.PedidoTiendaRepository;

@Controller
@RequestMapping("/tienda")
public class PedidoTiendaControlador {

    @Autowired
    private PedidoTiendaRepository pedidoTiendaRepository;

    @GetMapping("/nuevo")
    public String mostrarFormulario(Model model) {
        PedidoTienda pedido = new PedidoTienda();
        pedido.agregarDetalle(new DetallePedidoTienda());
        model.addAttribute("pedidoTienda", pedido);
        return "tienda/formulario";
    }

    @PostMapping("/guardar")
    public String guardarPedido(@ModelAttribute PedidoTienda pedidoTienda, RedirectAttributes redirectAttributes) {
        recalcularTotales(pedidoTienda);
        pedidoTiendaRepository.save(pedidoTienda);
        redirectAttributes.addFlashAttribute("mensaje", "Pedido registrado correctamente.");
        return "redirect:/tienda/listado";
    }

    @GetMapping("/listado")
    public String listarPedidos(Model model) {
        model.addAttribute("pedidos", pedidoTiendaRepository.findAll());
        model.addAttribute("puedeCrearPedidos", puedeGestionarPedidos());
        return "tienda/listado";
    }

    // ─── Editar pedido ──────────────────────────────────────────────────
    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable("id") int id, Model model) {
        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();
        if (pedido.getDetalles().isEmpty()) {
            pedido.agregarDetalle(new DetallePedidoTienda());
        }
        model.addAttribute("pedidoTienda", pedido);
        return "tienda/editar_pedido";
    }

    @PostMapping("/editar/{id}")
    public String guardarEdicion(
            @PathVariable("id") int id,
            @ModelAttribute PedidoTienda formPedido,
            RedirectAttributes redirectAttributes) {

        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();

        pedido.setNombreCliente(formPedido.getNombreCliente());
        pedido.setDireccion(formPedido.getDireccion());
        pedido.setTelefono(formPedido.getTelefono());
        pedido.setDescripcion(formPedido.getDescripcion());
        pedido.setVendedor(formPedido.getVendedor());
        pedido.setFechaEntrega(formPedido.getFechaEntrega());
        pedido.setAbono(formPedido.getAbono());
        pedido.setMetodoPago(formPedido.getMetodoPago());

        // Reemplaza los productos: limpia la lista (orphanRemoval borra los viejos)
        // y agrega los que vinieron del formulario, ignorando filas vacías.
        pedido.getDetalles().clear();
        for (DetallePedidoTienda d : formPedido.getDetalles()) {
            if (d.getProducto() == null || d.getProducto().isBlank()) continue;
            d.setId(0);
            pedido.agregarDetalle(d);
        }

        recalcularTotales(pedido);
        pedidoTiendaRepository.save(pedido);

        redirectAttributes.addFlashAttribute("mensaje", "Pedido #" + id + " actualizado correctamente.");
        return "redirect:/tienda/listado";
    }

    // ─── Eliminar pedido ────────────────────────────────────────────────
    @PostMapping("/eliminar/{id}")
    public String eliminarPedido(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            pedidoTiendaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Pedido #" + id + " eliminado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el pedido: " + e.getMessage());
        }
        return "redirect:/tienda/listado";
    }

    // ─── Cambiar estado del pedido ─────────────────────────────────────
    @PostMapping("/actualizar-estado/{id}")
    public String actualizarEstado(
            @PathVariable("id") int id,
            @RequestParam String estado,
            RedirectAttributes redirectAttributes) {
        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();
        pedido.setEstado(estado);
        pedidoTiendaRepository.save(pedido);
        redirectAttributes.addFlashAttribute("mensaje", "Estado actualizado a \"" + estado + "\".");
        return "redirect:/tienda/listado";
    }

    // ─── Helpers ────────────────────────────────────────────────────────
    private void recalcularTotales(PedidoTienda pedido) {
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedidoTienda d : pedido.getDetalles()) {
            BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(d.getCantidad()));
            d.setSubtotal(subtotal);
            d.setPedidoTienda(pedido);
            total = total.add(subtotal);
        }
        pedido.setTotal(total);
        BigDecimal abono = pedido.getAbono() != null ? pedido.getAbono() : BigDecimal.ZERO;
        pedido.setSaldo(total.subtract(abono));
    }

    private boolean puedeGestionarPedidos() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TIENDA") || a.getAuthority().equals("ROLE_ADMIN"));
    }
}