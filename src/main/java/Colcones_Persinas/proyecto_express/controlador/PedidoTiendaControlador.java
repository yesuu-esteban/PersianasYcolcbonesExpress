package Colcones_Persinas.proyecto_express.controlador;

import org.springframework.beans.factory.annotation.Autowired;
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
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedidoTienda d : pedidoTienda.getDetalles()) {
            BigDecimal subtotal = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
            d.setSubtotal(subtotal);
            d.setPedidoTienda(pedidoTienda);
            total = total.add(subtotal);
        }
        pedidoTienda.setTotal(total);
        pedidoTienda.setSaldo(total.subtract(pedidoTienda.getAbono()));

        pedidoTiendaRepository.save(pedidoTienda);
        redirectAttributes.addFlashAttribute("mensaje", "Pedido registrado correctamente.");
        return "redirect:/tienda/listado";
    }

    @GetMapping("/listado")
    public String listarPedidos(Model model) {
        model.addAttribute("pedidos", pedidoTiendaRepository.findAll());
        return "tienda/listado";
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
}