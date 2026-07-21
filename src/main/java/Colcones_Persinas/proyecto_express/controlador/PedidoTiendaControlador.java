package Colcones_Persinas.proyecto_express.controlador;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
        List<String> errores = validarProductos(pedidoTienda.getDetalles());
        if (!errores.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", String.join(" ", errores));
            redirectAttributes.addFlashAttribute("pedidoTienda", pedidoTienda);
            return "redirect:/tienda/nuevo";
        }

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

        List<String> errores = validarProductos(formPedido.getDetalles());
        if (!errores.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", String.join(" ", errores));
            return "redirect:/tienda/editar/" + id;
        }

        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();

        pedido.setNombreCliente(formPedido.getNombreCliente());
        pedido.setDireccion(formPedido.getDireccion());
        pedido.setTelefono(formPedido.getTelefono());
        pedido.setDescripcion(formPedido.getDescripcion());
        pedido.setVendedor(formPedido.getVendedor());
        pedido.setFechaEntrega(formPedido.getFechaEntrega());
        pedido.setAbono(formPedido.getAbono());
        pedido.setMetodoPago(formPedido.getMetodoPago());

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

    // ─── Actualizar abono (pago) ────────────────────────────────────────
    // Disponible para TIENDA, TIENDA_ADMIN y ADMIN (cae bajo el matcher
    // general "/tienda/**" del SecurityConfig, no bajo el restringido).
    @PostMapping("/actualizar-abono/{id}")
    public String actualizarAbono(
            @PathVariable("id") int id,
            @RequestParam BigDecimal abono,
            RedirectAttributes redirectAttributes) {

        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();

        if (abono == null || abono.compareTo(BigDecimal.ZERO) < 0) {
            redirectAttributes.addFlashAttribute("error", "El abono no puede ser negativo.");
            return "redirect:/tienda/listado";
        }

        BigDecimal total = pedido.getTotal() != null ? pedido.getTotal() : BigDecimal.ZERO;
        if (abono.compareTo(total) > 0) {
            redirectAttributes.addFlashAttribute("error",
                "El abono (" + abono + ") no puede superar el total del pedido (" + total + ").");
            return "redirect:/tienda/listado";
        }

        pedido.setAbono(abono);
        pedido.setSaldo(total.subtract(abono));
        pedidoTiendaRepository.save(pedido);

        String mensaje = pedido.getSaldo().compareTo(BigDecimal.ZERO) <= 0
            ? "Pedido #" + id + " marcado como Pagado Completo."
            : "Abono actualizado para el pedido #" + id + ".";
        redirectAttributes.addFlashAttribute("mensaje", mensaje);

        return "redirect:/tienda/listado";
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    /**
     * Valida que cada producto con nombre tenga cantidad y precio unitario
     * mayores a cero. Filas totalmente vacías (sin nombre de producto) se
     * ignoran aquí — se descartan más adelante al guardar.
     */
    private List<String> validarProductos(List<DetallePedidoTienda> detalles) {
        List<String> errores = new ArrayList<>();
        if (detalles == null) return errores;

        for (DetallePedidoTienda d : detalles) {
            if (d.getProducto() == null || d.getProducto().isBlank()) continue;

            if (d.getCantidad() <= 0) {
                errores.add("\"" + d.getProducto() + "\": la cantidad debe ser mayor a 0.");
            }

            BigDecimal precio = d.getPrecioUnitario();
            if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
                errores.add("\"" + d.getProducto() + "\": el precio unitario debe ser mayor a 0.");
            }
        }

        return errores;
    }

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
        if (abono.compareTo(BigDecimal.ZERO) < 0) abono = BigDecimal.ZERO;
        pedido.setAbono(abono);
        pedido.setSaldo(total.subtract(abono));
    }

    private boolean puedeGestionarPedidos() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TIENDA") || a.getAuthority().equals("ROLE_ADMIN"));
    }

}