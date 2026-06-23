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
import java.util.stream.Collectors;

@Controller
@RequestMapping("/taller")
public class PedidoControlador {

    @Autowired
    private PedidoRepository pedidoRepository;

    @GetMapping("/pedidos")
    public String verProduccion(
            @RequestParam(name = "estado", required = false) String estado,
            Model model) {
        try {
            List<Pedido> todos = pedidoRepository.findAll(Sort.by("nombreDecorador"));
            if (todos == null) {
                todos = new java.util.ArrayList<>();
            }

            // Contadores para mostrar en cada pestaña, calculados siempre sobre el total
            long totalTodos = todos.size();
            long totalPendiente = todos.stream().filter(p -> "Pendiente".equals(p.getEstado())).count();
            long totalEnProceso = todos.stream().filter(p -> "En Proceso".equals(p.getEstado())).count();
            long totalListoEnsamblar = todos.stream().filter(p -> "Listo para Ensamblar".equals(p.getEstado())).count();
            long totalListoDespacho = todos.stream().filter(p -> "Listo para Despacho".equals(p.getEstado())).count();

            model.addAttribute("totalTodos", totalTodos);
            model.addAttribute("totalPendiente", totalPendiente);
            model.addAttribute("totalEnProceso", totalEnProceso);
            model.addAttribute("totalListoEnsamblar", totalListoEnsamblar);
            model.addAttribute("totalListoDespacho", totalListoDespacho);

            // Filtrado: si no se pasa estado (o es "Todos"), se muestran todos los pedidos
            final String estadoFiltro = (estado == null || estado.isBlank() || "Todos".equalsIgnoreCase(estado))
                    ? "Todos"
                    : estado;

            List<Pedido> pedidos;
            if ("Todos".equals(estadoFiltro)) {
                pedidos = todos;
            } else {
                pedidos = todos.stream()
                        .filter(p -> estadoFiltro.equals(p.getEstado()))
                        .collect(Collectors.toList());
            }

            model.addAttribute("pedidos", pedidos);
            model.addAttribute("estadoActivo", estadoFiltro);
        } catch (Exception e) {
            System.err.println("Error al cargar pedidos: " + e.getMessage());
            model.addAttribute("pedidos", new java.util.ArrayList<Pedido>());
            model.addAttribute("estadoActivo", "Todos");
        }
        return "pedidos";
    }

    @GetMapping("/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        List<String> coloresDisponibles = Arrays.asList("Blanco", "Gris", "Fawn", "Vainilla");
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
            @RequestParam Map<String, String> allParams) {

        // LOG: imprime todos los parámetros recibidos para depuración
        System.out.println("=== PARÁMETROS RECIBIDOS ===");
        allParams.forEach((k, v) -> System.out.println("  " + k + " = '" + v + "'"));
        System.out.println("============================");

        for (int i = 0; i < descripciones.size(); i++) {

            // El checkbox en el HTML tiene value="true".
            // Si está MARCADO  → el navegador envía cabezales[i]=true  → getOrDefault devuelve "true"
            // Si está DESMARCADO → el navegador NO envía la clave → getOrDefault devuelve "false"
            String claveCabezal = "cabezales[" + i + "]";
            String valorCabezal = allParams.getOrDefault(claveCabezal, "false");
            boolean tieneCabezal = "true".equals(valorCabezal);

            System.out.println("Fila " + i + " | " + claveCabezal + "='" + valorCabezal + "' | tieneCabezal=" + tieneCabezal);

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
                p.setUsaCabezal(tieneCabezal);
                p.calcularFichaTecnica();
                p.calcularEstadoGeneral();
                pedidoRepository.save(p);
            }
        }
        return "redirect:/taller/pedidos";
    }

    @PostMapping("/actualizar/{id}/{accion}")
    public String actualizarEstado(
            @PathVariable("id") int id,
            @PathVariable("accion") String accion,
            RedirectAttributes redirectAttributes) {

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();

        switch (accion.toLowerCase()) {
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
        p.calcularFichaTecnica();
        model.addAttribute("pedido", p);
        return "imprimir_pedido";
    }

    // ─── EDITAR ────────────────────────────────────────────────────────────

    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable("id") int id, Model model) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        List<String> coloresDisponibles = Arrays.asList("Blanco", "Gris", "Fawn", "Vainilla");
        model.addAttribute("pedido", pedido);
        model.addAttribute("listaColores", coloresDisponibles);
        return "editar_pedido";
    }

    @PostMapping("/editar/{id}")
    public String guardarEdicion(
            @PathVariable("id") int id,
            @RequestParam String nombreDecorador,
            @RequestParam String nombreClienteFinal,
            @RequestParam String descripcion,
            @RequestParam double ancho,
            @RequestParam double altura,
            @RequestParam String colorTelaDeseado,
            @RequestParam String ladoControl,
            @RequestParam(required = false, defaultValue = "false") boolean usaCabezal) {

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();

        pedido.setNombreDecorador(nombreDecorador);
        pedido.setNombreClienteFinal(nombreClienteFinal);
        pedido.setDescripcion(descripcion);
        pedido.setAncho(ancho);
        pedido.setAltura(altura);
        pedido.setColorTelaDeseado(colorTelaDeseado);
        pedido.setLadoControl(ladoControl);
        pedido.setUsaCabezal(usaCabezal);

        // Recalcular ficha técnica con los nuevos valores
        pedido.calcularFichaTecnica();
        pedido.calcularEstadoGeneral();

        pedidoRepository.save(pedido);
        return "redirect:/taller/pedidos";
    }

    // ─── ELIMINAR ──────────────────────────────────────────────────────────

    @PostMapping("/eliminar/{id}")
    public String eliminarPedido(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            pedidoRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Pedido eliminado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el pedido: " + e.getMessage());
        }
        return "redirect:/taller/pedidos";
    }
}