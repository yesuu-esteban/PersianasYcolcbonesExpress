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
        // Ordenamos por nombreDecorador para mantener la estructura visual
        model.addAttribute("pedidos", pedidoRepository.findAll(Sort.by("nombreDecorador")));
        return "pedidos";
    }

    @GetMapping("/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        List<String> coloresDisponibles = Arrays.asList("Blanco", "Gris", "fawn", "Vainilla");
        model.addAttribute("pedido", new Pedido());
        model.addAttribute("listaColores", coloresDisponibles);
        return "nuevo_pedido"; 
    }

    @PostMapping("/guardar-lista")
    public String guardarListaPedidos(
            @RequestParam String nombreDecorador, 
            @RequestParam String nombreClienteFinal,
            @RequestParam List<String> descripciones,
            @RequestParam List<Integer> cantidades,
            @RequestParam List<Double> anchos,
            @RequestParam List<Double> alturas,
            @RequestParam List<String> colores,
            @RequestParam List<String> mandos,
            // Usamos HttpServletRequest para ver si llegan los datos o usamos un mapeo más simple
            @RequestParam(required = false) List<String> cabezales) { // Cambiado a List<String> para capturar mejor los valores

        for (int i = 0; i < descripciones.size(); i++) {
            for (int j = 0; j < cantidades.get(i); j++) {
                Pedido p = new Pedido();
                // ... (resto de tus sets) ...
                
                // LÓGICA REFORZADA: Si el valor llega como "true" o "on", es verdadero
                boolean esCabezal = (cabezales != null && i < cabezales.size() && "true".equals(cabezales.get(i)));
                p.setUsaCabezal(esCabezal);
                
                p.calcularFichaTecnica(); // Aquí se procesa el Boolean
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
            case "tela": 
                pedido.setTelaCortada(!pedido.getTelaCortada()); 
                break;
            case "perfileria": 
                pedido.setPerfileriaCortada(!pedido.getPerfileriaCortada()); 
                break;
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