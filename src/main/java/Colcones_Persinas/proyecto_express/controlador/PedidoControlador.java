package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.Pedido;
import Colcones_Persinas.proyecto_express.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/taller")
public class PedidoControlador {

    @Autowired private PedidoRepository pedidoRepository;
    @GetMapping("/pedidos")
    public String verProduccion(Model model) {
        // Es vital el Sort.by para que el agrupamiento visual no se mezcle
        model.addAttribute("pedidos", pedidoRepository.findAll(Sort.by("nombreCliente")));
        return "pedidos";
    }
    @GetMapping("/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        List<String> coloresDisponibles = Arrays.asList(
            "Blanco","Gris","fawn", "Vainilla"
        );
        
        model.addAttribute("pedido", new Pedido());
        model.addAttribute("listaColores", coloresDisponibles);
        
        // Cambia esto:
        return "nuevo_pedido"; 
    }

    @PostMapping("/guardar-lista")
    public String guardarListaPedidos(
            @RequestParam String nombreCliente, 
            @RequestParam("descripciones") List<String> descripciones,
            @RequestParam("cantidades") List<Integer> cantidades,
            @RequestParam("anchos") List<Double> anchos,
            @RequestParam("alturas") List<Double> alturas,
            @RequestParam("colores") List<String> colores) {
        
        for (int i = 0; i < descripciones.size(); i++) {
            // Obtenemos la cantidad definida para esta fila
            int cantidad = cantidades.get(i);
            
            for (int j = 0; j < cantidad; j++) {
                Pedido p = new Pedido();
                p.setNombreCliente(nombreCliente);
                // Agregamos el identificador de unidad para facilitar la gestión
                p.setDescripcion(descripciones.get(i) + " (" + (j + 1) + "/" + cantidad + ")");
                p.setAncho(anchos.get(i));
                p.setAltura(alturas.get(i));
                p.setColorTelaDeseado(colores.get(i));
                p.setEstado("Pendiente");
                
                p.calcularFichaTecnica();
                p.calcularEstadoGeneral();
                pedidoRepository.save(p);
            }
        }
        return "redirect:/taller/pedidos";
    }

    @PostMapping("/actualizar/{id}/{accion}")
    public String actualizarEstado(@PathVariable("id") int id, @PathVariable("accion") String accion, RedirectAttributes redirectAttributes) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        switch(accion.toLowerCase()) {
            case "tela": pedido.setTelaCortada(!pedido.getTelaCortada()); break;
            case "perfileria": pedido.setPerfileriaCortada(!pedido.getPerfileriaCortada()); break;
            case "ensamblado":
                if (Boolean.TRUE.equals(pedido.getTelaCortada()) && Boolean.TRUE.equals(pedido.getPerfileriaCortada())) {
                    pedido.setEnsamblado(!pedido.getEnsamblado());
                } else {
                    redirectAttributes.addFlashAttribute("error", "¡Error! Primero debes cortar la tela y los perfiles.");
                }
                break;
        }
        pedido.calcularEstadoGeneral();
        pedidoRepository.save(pedido);
        return "redirect:/taller/pedidos";
    }

    @GetMapping("/imprimir/{id}")
    public String imprimirPedido(@PathVariable("id") int id, Model model) {
        Pedido p = pedidoRepository.findById(id).orElseThrow();
        model.addAttribute("pedido", p);
        return "imprimir_pedido";
    }
}