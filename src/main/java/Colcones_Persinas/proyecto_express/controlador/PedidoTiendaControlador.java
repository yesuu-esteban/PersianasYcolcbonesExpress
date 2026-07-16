package Colcones_Persinas.proyecto_express.controlador;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

import Colcones_Persinas.proyecto_express.modelo.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.DetallePedidoTienda;
import Colcones_Persinas.proyecto_express.repository.PedidoTiendaRepository;

@Controller
@RequestMapping("/tienda")
public class PedidoTiendaControlador {

    @Autowired
    private PedidoTiendaRepository pedidoTiendaRepository;

    // Mostrar el formulario vacío, con una línea de detalle ya lista
    @GetMapping("/nuevo")
    public String mostrarFormulario(Model model) {
        PedidoTienda pedido = new PedidoTienda();
        pedido.agregarDetalle(new DetallePedidoTienda()); // arranca con 1 línea vacía
        model.addAttribute("pedidoTienda", pedido);
        return "tienda/formulario";
    }

    // Guardar el pedido con todos sus detalles
    @PostMapping("/guardar")
    public String guardarPedido(@ModelAttribute PedidoTienda pedidoTienda) {
        // Recalcular subtotales y total, y enlazar cada detalle con el pedido padre
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
        return "redirect:/tienda/listado";
    }

    @GetMapping("/listado")
    public String listarPedidos(Model model) {
        model.addAttribute("pedidos", pedidoTiendaRepository.findAll());
        return "tienda/listado";
    }
}