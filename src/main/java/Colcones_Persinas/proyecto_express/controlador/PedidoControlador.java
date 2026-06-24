package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.Pedido;
import Colcones_Persinas.proyecto_express.repository.PedidoRepository;
import Colcones_Persinas.proyecto_express.servicio.InventarioServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/taller")
public class PedidoControlador {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private InventarioServicio inventarioServicio;

    // ─── Ver lista de pedidos ─────────────────────────────────────────────
    @GetMapping("/pedidos")
    public String verProduccion(
            @RequestParam(name = "estado", required = false) String estado,
            Model model) {
        try {
            List<Pedido> todos = pedidoRepository.findAll(Sort.by("nombreDecorador"));
            if (todos == null) todos = new java.util.ArrayList<>();

            long totalTodos          = todos.size();
            long totalPendiente      = todos.stream().filter(p -> "Pendiente".equals(p.getEstado())).count();
            long totalEnProceso      = todos.stream().filter(p -> "En Proceso".equals(p.getEstado())).count();
            long totalListoEnsamblar = todos.stream().filter(p -> "Listo para Ensamblar".equals(p.getEstado())).count();
            long totalListoDespacho  = todos.stream().filter(p -> "Listo para Despacho".equals(p.getEstado())).count();

            model.addAttribute("totalTodos",          totalTodos);
            model.addAttribute("totalPendiente",      totalPendiente);
            model.addAttribute("totalEnProceso",      totalEnProceso);
            model.addAttribute("totalListoEnsamblar", totalListoEnsamblar);
            model.addAttribute("totalListoDespacho",  totalListoDespacho);

            final String estadoFiltro = (estado == null || estado.isBlank() || "Todos".equalsIgnoreCase(estado))
                    ? "Todos" : estado;

            List<Pedido> pedidos = "Todos".equals(estadoFiltro) ? todos : todos.stream()
                    .filter(p -> estadoFiltro.equals(p.getEstado())).collect(Collectors.toList());

            model.addAttribute("pedidos",      pedidos);
            model.addAttribute("estadoActivo", estadoFiltro);

        } catch (Exception e) {
            System.err.println("Error al cargar pedidos: " + e.getMessage());
            model.addAttribute("pedidos",      new java.util.ArrayList<Pedido>());
            model.addAttribute("estadoActivo", "Todos");
        }
        return "pedidos";
    }

    // ─── Formulario nuevo pedido ──────────────────────────────────────────
    @GetMapping("/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        List<String> coloresDisponibles = Arrays.asList("Blanco", "Gris", "Fawn", "Vainilla");
        model.addAttribute("pedido",             new Pedido());
        model.addAttribute("listaColores",        coloresDisponibles);
        model.addAttribute("rollosDisponibles",   inventarioServicio.getTodosLosRollos());
        model.addAttribute("piezasDisponibles",   inventarioServicio.getTodasLasPiezas());
        model.addAttribute("retazosDisponibles",  inventarioServicio.getTodosLosRetazos());
        return "nuevo_pedido";
    }

    // ─── Previsualización AJAX ────────────────────────────────────────────
    @GetMapping("/previsualizar-material")
    @ResponseBody
    public InventarioServicio.PrevisualizacionMaterial previsualizarMaterial(
            @RequestParam double ancho,
            @RequestParam double altura,
            @RequestParam String color,
            @RequestParam(required = false, defaultValue = "false") boolean usaCabezal,
            @RequestParam(required = false, defaultValue = "true")  boolean usaPitilloPesa,
            @RequestParam(required = false, defaultValue = "true")  boolean usaConectorTope) {

        Pedido p = new Pedido();
        p.setAncho(ancho);
        p.setAltura(altura);
        p.setColorTelaDeseado(color);
        p.setUsaCabezal(usaCabezal);
        p.setUsaPitilloPesa(usaPitilloPesa);
        p.setUsaConectorTope(usaConectorTope);
        p.calcularFichaTecnica();

        return inventarioServicio.previsualizar(p);
    }

    // ─── Guardar lista de pedidos ─────────────────────────────────────────
    @PostMapping("/guardar-lista")
    @org.springframework.transaction.annotation.Transactional
    public String guardarListaPedidos(
            @RequestParam String nombreDecorador,
            @RequestParam String nombreClienteFinal,
            @RequestParam List<String> descripciones,
            @RequestParam List<Integer> cantidades,
            @RequestParam List<Double> anchos,
            @RequestParam List<Double> alturas,
            @RequestParam List<String> colores,
            @RequestParam List<String> mandos,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        System.out.println("=== PARÁMETROS RECIBIDOS ===");
        allParams.forEach((k, v) -> System.out.println("  " + k + " = '" + v + "'"));
        System.out.println("============================");

        // ── Validación defensiva de tamaños ──────────────────────────────
        int n = anchos.size();
        if (cantidades.size() != n || alturas.size() != n ||
            colores.size()   != n || mandos.size()   != n ||
            descripciones.size() != n) {

            redirectAttributes.addFlashAttribute("error",
                "Error al leer el formulario: verifica que todos los campos estén completos. " +
                "(descripciones=" + descripciones.size() + ", filas esperadas=" + n + "). " +
                "Asegúrate de que ningún campo de descripción esté en blanco.");
            return "redirect:/taller/nuevo";
        }

        List<Pedido> pedidosDelLote                          = new java.util.ArrayList<>();
        List<InventarioServicio.SeleccionManual> seleccionesDelLote = new java.util.ArrayList<>();

        for (int i = 0; i < n; i++) {
            boolean tieneCabezal    = leerBooleanoFila(allParams, "cabezales",       i, false);
            boolean usaPitilloPesa  = leerBooleanoFila(allParams, "usaPitilloPesa",  i, true);
            boolean usaConectorTope = leerBooleanoFila(allParams, "usaConectorTope", i, true);

            InventarioServicio.SeleccionManual seleccion = new InventarioServicio.SeleccionManual();
            seleccion.rolloTelaId   = leerIdOpcionalFila(allParams, "rolloManual",   i);
            seleccion.retazoTelaId  = leerIdOpcionalFila(allParams, "retazoManual",  i);
            seleccion.piezaTuboId   = leerIdOpcionalFila(allParams, "tuboManual",    i);
            seleccion.piezaPesaId   = leerIdOpcionalFila(allParams, "pesaManual",    i);
            seleccion.piezaCuerdaId = leerIdOpcionalFila(allParams, "cuerdaManual",  i);
            seleccion.piezaPitilloId= leerIdOpcionalFila(allParams, "pitilloManual", i);

            int cantidad = cantidades.get(i);
            for (int j = 0; j < cantidad; j++) {
                Pedido p = new Pedido();
                p.setNombreDecorador(nombreDecorador);
                p.setNombreClienteFinal(nombreClienteFinal);
                p.setDescripcion(cantidad > 1
                    ? descripciones.get(i) + " (" + (j + 1) + "/" + cantidad + ")"
                    : descripciones.get(i));
                p.setAncho(anchos.get(i));
                p.setAltura(alturas.get(i));
                p.setColorTelaDeseado(colores.get(i));
                p.setLadoControl(mandos.get(i));
                p.setCantidad(1);
                p.setUsaCabezal(tieneCabezal);
                p.setUsaPitilloPesa(usaPitilloPesa);
                p.setUsaConectorTope(usaConectorTope);
                p.calcularFichaTecnica();
                p.calcularEstadoGeneral();
                pedidosDelLote.add(p);
                seleccionesDelLote.add(seleccion);
            }
        }

        // ── Verificación previa (sin descontar nada todavía) ─────────────
        try {
            for (Pedido p : pedidosDelLote) {
                inventarioServicio.verificarDisponibilidad(p);
            }
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/taller/nuevo";
        }

        // ── Guardado y descuento real ─────────────────────────────────────
        try {
            for (int i = 0; i < pedidosDelLote.size(); i++) {
                Pedido p = pedidosDelLote.get(i);
                pedidoRepository.save(p);
                inventarioServicio.descontarMaterialDe(p, seleccionesDelLote.get(i));
            }
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/taller/nuevo";
        }

        redirectAttributes.addFlashAttribute("mensaje",
            pedidosDelLote.size() + " pedido(s) creados correctamente.");
        return "redirect:/taller/pedidos";
    }

    // ─── Helpers para leer parámetros indexados del formulario ───────────
    private boolean leerBooleanoFila(Map<String, String> allParams, String nombreCampo,
                                     int indice, boolean porDefecto) {
        String clave = nombreCampo + "[" + indice + "]";
        if (!allParams.containsKey(clave)) return porDefecto;
        return "true".equals(allParams.get(clave));
    }

    private Integer leerIdOpcionalFila(Map<String, String> allParams, String nombreCampo, int indice) {
        String clave = nombreCampo + "[" + indice + "]";
        String texto = allParams.get(clave);
        if (texto == null || texto.isBlank() || "auto".equalsIgnoreCase(texto)) return null;
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ─── Actualizar estado (tela / perfilería / ensamblado) ───────────────
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
                if (Boolean.TRUE.equals(pedido.getTelaCortada()) &&
                    Boolean.TRUE.equals(pedido.getPerfileriaCortada())) {
                    pedido.setEnsamblado(!pedido.getEnsamblado());
                } else {
                    redirectAttributes.addFlashAttribute("error",
                        "¡Error! Primero debes cortar la tela y los perfiles.");
                }
                break;
        }

        pedido.calcularEstadoGeneral();
        pedidoRepository.save(pedido);
        return "redirect:/taller/pedidos";
    }

    // ─── Imprimir pedido ──────────────────────────────────────────────────
    @GetMapping("/imprimir/{id}")
    public String imprimirPedido(@PathVariable("id") int id, Model model) {
        Pedido p = pedidoRepository.findById(id).orElseThrow();
        p.calcularFichaTecnica();
        model.addAttribute("pedido",           p);
        model.addAttribute("historialMaterial", inventarioServicio.getHistorialDePedido(id));
        return "imprimir_pedido";
    }

    // ─── Formulario editar pedido ─────────────────────────────────────────
    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable("id") int id, Model model) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        List<String> coloresDisponibles = Arrays.asList("Blanco", "Gris", "Fawn", "Vainilla");
        model.addAttribute("pedido",            pedido);
        model.addAttribute("listaColores",       coloresDisponibles);
        model.addAttribute("rollosDisponibles",  inventarioServicio.getTodosLosRollos());
        model.addAttribute("piezasDisponibles",  inventarioServicio.getTodasLasPiezas());
        model.addAttribute("retazosDisponibles", inventarioServicio.getTodosLosRetazos());
        return "editar_pedido";
    }

    // ─── Guardar edición ──────────────────────────────────────────────────
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
            @RequestParam String estado,
            @RequestParam(required = false, defaultValue = "false") boolean usaCabezal,
            @RequestParam(required = false, defaultValue = "true")  boolean usaPitilloPesa,
            @RequestParam(required = false, defaultValue = "true")  boolean usaConectorTope) {

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();

        pedido.setNombreDecorador(nombreDecorador);
        pedido.setNombreClienteFinal(nombreClienteFinal);
        pedido.setDescripcion(descripcion);
        pedido.setAncho(ancho);
        pedido.setAltura(altura);
        pedido.setColorTelaDeseado(colorTelaDeseado);
        pedido.setLadoControl(ladoControl);
        pedido.setUsaCabezal(usaCabezal);
        pedido.setUsaPitilloPesa(usaPitilloPesa);
        pedido.setUsaConectorTope(usaConectorTope);

        pedido.calcularFichaTecnica();

        // Respetar el estado elegido manualmente en el formulario;
        // solo recalcular automáticamente si por algún motivo no llegó.
        if (estado != null && !estado.isBlank()) {
            pedido.setEstado(estado);
        } else {
            pedido.calcularEstadoGeneral();
        }

        pedidoRepository.save(pedido);
        return "redirect:/taller/pedidos";
    }

    // ─── Eliminar pedido ──────────────────────────────────────────────────
    @PostMapping("/eliminar/{id}")
    public String eliminarPedido(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            pedidoRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Pedido eliminado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "No se pudo eliminar el pedido: " + e.getMessage());
        }
        return "redirect:/taller/pedidos";
    }

    // ─── Reporte global de consumo ────────────────────────────────────────
    @GetMapping("/reporte-materiales")
    public String reporteMateriales(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            Model model) {

        LocalDateTime fechaDesde = (desde != null && !desde.isBlank())
                ? LocalDateTime.parse(desde + "T00:00:00") : null;
        LocalDateTime fechaHasta = (hasta != null && !hasta.isBlank())
                ? LocalDateTime.parse(hasta + "T23:59:59") : null;

        List<InventarioServicio.ResumenMaterial> resumen =
                inventarioServicio.obtenerResumenConsumo(fechaDesde, fechaHasta);

        model.addAttribute("resumen", resumen);
        model.addAttribute("desde",   desde != null ? desde : "");
        model.addAttribute("hasta",   hasta != null ? hasta : "");
        return "reporte_materiales";
    }
}