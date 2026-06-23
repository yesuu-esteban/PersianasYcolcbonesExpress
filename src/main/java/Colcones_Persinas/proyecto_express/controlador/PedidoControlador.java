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
import java.util.Map;

@Controller
@RequestMapping("/taller")
public class PedidoControlador {

    @Autowired 
    private PedidoRepository pedidoRepository;

    @GetMapping("/pedidos")
    public String verProduccion(Model model) {
        try {
            // Obtenemos los pedidos y, si está vacío, simplemente devolvemos una lista vacía
            List<Pedido> pedidos = pedidoRepository.findAll(Sort.by("nombreDecorador"));
            model.addAttribute("pedidos", pedidos != null ? pedidos : new java.util.ArrayList<Pedido>());
        } catch (Exception e) {
            System.err.println("Error al cargar pedidos: " + e.getMessage());
            model.addAttribute("pedidos", new java.util.ArrayList<Pedido>());
        }
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
            // Recibe los cabezales como una lista de String
            @RequestParam(required = false) List<String> cabezales) { 
        
        for (int i = 0; i < descripciones.size(); i++) {
            for (int j = 0; j < cantidades.get(i); j++) {
                Pedido p = new Pedido();
                p.setNombreDecorador(nombreDecorador);
                p.setNombreClienteFinal(nombreClienteFinal);
                p.setDescripcion(descripciones.get(i) + " (" + (j + 1) + "/" + cantidades.get(i) + ")");
                p.setAncho(anchos.get(i));
                p.setAltura(alturas.get(i));
                p.setColorTelaDeseado(colores.get(i));
                p.setLadoControl(mandos.get(i));
                p.setCantidad(1);

                // LOGICA MEJORADA:
                // Si la lista de cabezales no es nula, intentamos obtener el valor por índice
                if (cabezales != null && i < cabezales.size()) {
                    p.setUsaCabezal(Boolean.parseBoolean(cabezales.get(i)));
                } else {
                    p.setUsaCabezal(false);
                }
                
                p.setEstado("Pendiente");
                p.calcularFichaTecnica(); // Ahora calcularFichaTecnica usa el valor de usaCabezal ya seteado
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
        // Aseguramos que los cálculos estén frescos antes de enviar a la vista
        p.calcularFichaTecnica();
        model.addAttribute("pedido", p);
        return "imprimir_pedido";
    }
}