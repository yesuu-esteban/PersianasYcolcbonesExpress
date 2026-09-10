package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.Pedido;
import Colcones_Persinas.proyecto_express.modelo.Insumo;
import Colcones_Persinas.proyecto_express.modelo.MaterialUsado;
import Colcones_Persinas.proyecto_express.modelo.PiezaInsumo;
import Colcones_Persinas.proyecto_express.repository.InsumoRepository;
import Colcones_Persinas.proyecto_express.repository.MaterialUsadoRepository;
import Colcones_Persinas.proyecto_express.repository.PedidoRepository;
import Colcones_Persinas.proyecto_express.repository.PiezaInsumoRepository;
import Colcones_Persinas.proyecto_express.servicio.InventarioServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/taller")
public class PedidoControlador {

    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private InventarioServicio inventarioServicio;
    @Autowired private InsumoRepository insumoRepository;
    @Autowired private MaterialUsadoRepository materialUsadoRepository;
    @Autowired private PiezaInsumoRepository piezaInsumoRepository;

    // ─── Ver lista de pedidos ─────────────────────────────────────────────
    @GetMapping("/pedidos")
    public String verProduccion(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "desde", required = false) String desde,
            @RequestParam(name = "hasta", required = false) String hasta,
            @RequestParam(name = "anio", required = false) Integer anio,
            @RequestParam(name = "pagina", required = false, defaultValue = "0") int pagina,
            Model model) {
        try {
            List<Pedido> todos = pedidoRepository.findAll(
                 Sort.by(Sort.Direction.DESC, "fechaCreacion")
                    .and(Sort.by(Sort.Direction.DESC, "id"))
            );
            if (todos == null) todos = new ArrayList<>();

            long totalTodos      = todos.size();
            long totalPendiente  = todos.stream().filter(p -> "Pendiente".equals(p.getEstado())).count();
            long totalFinalizado = todos.stream().filter(p -> "Finalizado".equals(p.getEstado())).count();
            long totalDespachado = todos.stream().filter(p -> "Despachado".equals(p.getEstado())).count();

            model.addAttribute("totalTodos",      totalTodos);
            model.addAttribute("totalPendiente",  totalPendiente);
            model.addAttribute("totalFinalizado", totalFinalizado);
            model.addAttribute("totalDespachado", totalDespachado);

            // ── Años disponibles para el filtro (sobre TODOS los pedidos, sin aplicar los demás filtros) ──
            TreeSet<Integer> aniosDisponibles = new TreeSet<>(Collections.reverseOrder());
            for (Pedido p : todos) {
                if (p.getFechaCreacion() != null) {
                    aniosDisponibles.add(p.getFechaCreacion().getYear());
                }
            }
            aniosDisponibles.add(LocalDate.now().getYear());
            model.addAttribute("aniosDisponibles", aniosDisponibles);

            final String estadoFiltro = (estado == null || estado.isBlank() || "Todos".equalsIgnoreCase(estado))
                    ? "Todos" : estado;

            List<Pedido> pedidosEstado = "Todos".equals(estadoFiltro) ? todos : todos.stream()
                    .filter(p -> estadoFiltro.equals(p.getEstado())).collect(Collectors.toList());

            // ── Filtro por fecha (rango desde/hasta) y por año ──
            LocalDate fechaDesde = (desde != null && !desde.isBlank()) ? LocalDate.parse(desde) : null;
            LocalDate fechaHasta = (hasta != null && !hasta.isBlank()) ? LocalDate.parse(hasta) : null;

            List<Pedido> pedidosFiltrados = pedidosEstado.stream()
                    .filter(p -> fechaDesde == null
                            || (p.getFechaCreacion() != null && !p.getFechaCreacion().toLocalDate().isBefore(fechaDesde)))
                    .filter(p -> fechaHasta == null
                            || (p.getFechaCreacion() != null && !p.getFechaCreacion().toLocalDate().isAfter(fechaHasta)))
                    .filter(p -> anio == null
                            || (p.getFechaCreacion() != null && p.getFechaCreacion().getYear() == anio))
                    .collect(Collectors.toList());

            // ── Paginación: máximo 10 pedidos por página ──
            int tamanoPagina = 10;
            int totalFiltrados = pedidosFiltrados.size();
            int totalPaginas = (int) Math.ceil((double) totalFiltrados / tamanoPagina);
            if (totalPaginas == 0) totalPaginas = 1;
            int paginaActual = Math.max(0, Math.min(pagina, totalPaginas - 1));
            int desdeIdx = paginaActual * tamanoPagina;
            int hastaIdx = Math.min(desdeIdx + tamanoPagina, totalFiltrados);
            List<Pedido> pedidosPagina = (desdeIdx < hastaIdx)
                    ? pedidosFiltrados.subList(desdeIdx, hastaIdx)
                    : new ArrayList<>();

            model.addAttribute("pedidos",         pedidosPagina);
            model.addAttribute("estadoActivo",    estadoFiltro);
            model.addAttribute("desde",           desde != null ? desde : "");
            model.addAttribute("hasta",           hasta != null ? hasta : "");
            model.addAttribute("anio",            anio);
            model.addAttribute("paginaActual",    paginaActual);
            model.addAttribute("totalPaginas",    totalPaginas);
            model.addAttribute("totalFiltrados",  totalFiltrados);

            // ── Conteo de pedidos por distribuidor, solo dentro de esta página (para el encabezado del grupo) ──
            Map<String, Long> conteoPorDecorador = pedidosPagina.stream()
                    .collect(Collectors.groupingBy(Pedido::getNombreDecorador, Collectors.counting()));
            model.addAttribute("conteoPorDecorador", conteoPorDecorador);

            Map<Integer, String> telaUsadaPorPedido = new HashMap<>();
            for (Pedido p : pedidosPagina) {
                inventarioServicio.getHistorialDePedido(p.getId()).stream()
                        .filter(m -> "TELA".equals(m.getTipoMaterial()) || "RETAZO".equals(m.getTipoMaterial()))
                        .findFirst()
                        .ifPresent(m -> telaUsadaPorPedido.put(p.getId(), m.getFuenteDescripcion()));
            }
            model.addAttribute("telaUsadaPorPedido", telaUsadaPorPedido);

        } catch (Exception e) {
            System.err.println("Error al cargar pedidos: " + e.getMessage());
            model.addAttribute("pedidos",            new ArrayList<Pedido>());
            model.addAttribute("estadoActivo",       "Todos");
            model.addAttribute("telaUsadaPorPedido", new HashMap<>());
            model.addAttribute("conteoPorDecorador", new HashMap<>());
            model.addAttribute("aniosDisponibles",   new TreeSet<Integer>());
            model.addAttribute("paginaActual", 0);
            model.addAttribute("totalPaginas", 1);
            model.addAttribute("totalFiltrados", 0);
            model.addAttribute("desde", "");
            model.addAttribute("hasta", "");
        }
        return "pedidos";
    }

    // ─── Formulario nuevo pedido ──────────────────────────────────────────
    @GetMapping("/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        model.addAttribute("pedido",            new Pedido());
        model.addAttribute("listaColores",       Arrays.asList("Blanco", "Gris", "Fawn", "Vainilla"));
        model.addAttribute("rollosDisponibles",  inventarioServicio.getTodosLosRollos());
        model.addAttribute("piezasDisponibles",  inventarioServicio.getTodasLasPiezas());
        model.addAttribute("retazosDisponibles", inventarioServicio.getTodosLosRetazos());
        model.addAttribute("catalogoInsumos",    insumoRepository.findAllByOrderByNombreAsc());
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
            @RequestParam(required = false, defaultValue = "true")  boolean usaConectorTope,
            @RequestParam(required = false) String tipoTuboManual) {

        Pedido p = new Pedido();
        p.setAncho(ancho);
        p.setAltura(altura);
        p.setColorTelaDeseado(color);
        p.setUsaCabezal(usaCabezal);
        p.setUsaPitilloPesa(usaPitilloPesa);
        p.setUsaConectorTope(usaConectorTope);
        p.setTuboManualElegido(normalizarTuboManual(tipoTuboManual));
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

        int n = anchos.size();
        if (cantidades.size() != n || alturas.size() != n ||
            colores.size()   != n || mandos.size()   != n ||
            descripciones.size() != n) {
            redirectAttributes.addFlashAttribute("error",
                "Error al leer el formulario: verifica que todos los campos estén completos.");
            return "redirect:/taller/nuevo";
        }

        List<Pedido> pedidosDelLote = new ArrayList<>();
        List<InventarioServicio.SeleccionManual> seleccionesDelLote = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            boolean tieneCabezal    = leerBooleanoFila(allParams, "cabezales",       i, false);
            boolean usaPitilloPesa  = leerBooleanoFila(allParams, "usaPitilloPesa",  i, true);
            boolean usaConectorTope = leerBooleanoFila(allParams, "usaConectorTope", i, true);
            String tipoTuboManual   = leerTextoOpcionalFila(allParams, "tipoTuboManual", i);

            InventarioServicio.SeleccionManual seleccion = new InventarioServicio.SeleccionManual();
            seleccion.rolloTelaId    = leerIdOpcionalFila(allParams, "rolloManual",   i);
            seleccion.retazoTelaId   = leerIdOpcionalFila(allParams, "retazoManual",  i);
            seleccion.piezaTuboId    = leerIdOpcionalFila(allParams, "tuboManual",    i);
            seleccion.piezaPesaId    = leerIdOpcionalFila(allParams, "pesaManual",    i);
            seleccion.piezaCuerdaId  = leerIdOpcionalFila(allParams, "cuerdaManual",  i);
            seleccion.piezaPitilloId = leerIdOpcionalFila(allParams, "pitilloManual", i);

            if (seleccion.rolloTelaId != null && seleccion.retazoTelaId != null) {
                seleccion.retazoTelaId = null;
            }

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
                p.setTuboManualElegido(tipoTuboManual);
                p.calcularFichaTecnica();
                p.calcularEstadoGeneral();
                pedidosDelLote.add(p);
                seleccionesDelLote.add(seleccion);
            }
        }

        // Verificar material estándar antes de guardar
        try {
            for (int i = 0; i < pedidosDelLote.size(); i++) {
                inventarioServicio.verificarDisponibilidad(pedidosDelLote.get(i), seleccionesDelLote.get(i));
            }
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/taller/nuevo";
        }

        // Guardar, descontar material estándar y procesar extras
        try {
            for (int i = 0; i < pedidosDelLote.size(); i++) {
                Pedido p = pedidosDelLote.get(i);
                pedidoRepository.save(p);
                inventarioServicio.descontarMaterialDe(p, seleccionesDelLote.get(i));
                // Los extras vienen en el allParams global (no por fila de producto),
                // solo se procesan en el primer pedido del lote para no duplicar
                if (i == 0) {
                    procesarExtras(p, allParams);
                }
            }
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/taller/nuevo";
        }

        redirectAttributes.addFlashAttribute("mensaje",
            pedidosDelLote.size() + " pedido(s) creados correctamente.");
        return "redirect:/taller/pedidos";
    }

    // ═══════════════════════════════════════════════════════════════
    // VENTA DIRECTA
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/guardar-venta-directa")
    @org.springframework.transaction.annotation.Transactional
    public String guardarVentaDirecta(
            @RequestParam String nombreClienteFinal,
            @RequestParam(required = false) String descripcion,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        if (nombreClienteFinal == null || nombreClienteFinal.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Debes indicar el cliente de la venta directa.");
            return "redirect:/taller/nuevo";
        }

        Pedido pedido = new Pedido();
        pedido.setTipo("VENTA_DIRECTA");
        pedido.setNombreDecorador("Venta Directa");
        pedido.setNombreClienteFinal(nombreClienteFinal);
        pedido.setDescripcion(descripcion != null ? descripcion : "");
        pedido.setUsaCabezal(false);
        pedido.setUsaPitilloPesa(false);
        pedido.setUsaConectorTope(false);
        pedido.setEnsamblado(true);
        pedido.setDespachado(true);
        pedido.calcularEstadoGeneral(); // deja estado = "Vendido"

        List<InventarioServicio.ItemTelaVenta> itemsTela = leerItemsTelaVenta(allParams);
        List<InventarioServicio.ExtraInsumo> extras = leerExtrasComoLista(allParams);

        if (itemsTela.isEmpty() && extras.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Agrega al menos un ítem de tela o un insumo para registrar la venta.");
            return "redirect:/taller/nuevo";
        }

        try {
            inventarioServicio.verificarItemsTelaVenta(itemsTela);
            inventarioServicio.verificarExtras(extras);
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/taller/nuevo";
        }

        try {
            pedidoRepository.save(pedido);
            inventarioServicio.descontarItemsTelaVenta(pedido, itemsTela);
            procesarExtras(pedido, allParams); // reutiliza el mismo método usado en fabricación
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/taller/nuevo";
        }

        redirectAttributes.addFlashAttribute("mensaje", "Venta directa registrada correctamente.");
        return "redirect:/taller/pedidos";
    }

    /** Lee los ítems de tela vendida por metros desde los parámetros indexados del formulario. */
    private List<InventarioServicio.ItemTelaVenta> leerItemsTelaVenta(Map<String, String> allParams) {
        List<InventarioServicio.ItemTelaVenta> items = new ArrayList<>();
        int i = 0;
        while (allParams.containsKey("ventaTelaMetros[" + i + "]")) {
            String metrosStr = allParams.getOrDefault("ventaTelaMetros[" + i + "]", "").trim();
            final int idx = i;
            i++;
            if (metrosStr.isEmpty()) continue;
            double metros;
            try { metros = Double.parseDouble(metrosStr); } catch (NumberFormatException e) { continue; }
            if (metros <= 0) continue;

            InventarioServicio.ItemTelaVenta item = new InventarioServicio.ItemTelaVenta();
            item.metros = metros;
            item.color  = allParams.getOrDefault("ventaTelaColor[" + idx + "]", "").trim();
            item.ancho  = parsearDoubleSeguro(allParams.get("ventaTelaAncho[" + idx + "]"), 1.83);
            item.rolloId = leerIdOpcionalFila(allParams, "ventaTelaRolloId", idx);
            items.add(item);
        }
        return items;
    }

    /** Reconstruye la lista de ExtraInsumo desde allParams, solo para VALIDAR antes de guardar el pedido. */
    private List<InventarioServicio.ExtraInsumo> leerExtrasComoLista(Map<String, String> allParams) {
        List<InventarioServicio.ExtraInsumo> lista = new ArrayList<>();
        int i = 0;
        while (allParams.containsKey("extraCantidad[" + i + "]")) {
            String cantidadStr = allParams.getOrDefault("extraCantidad[" + i + "]", "").trim();
            String insumoIdStr = allParams.getOrDefault("extraInsumoId[" + i + "]", "").trim();
            i++;
            if (cantidadStr.isEmpty()) continue;
            double cantidad;
            try { cantidad = Double.parseDouble(cantidadStr); } catch (NumberFormatException e) { continue; }
            if (cantidad <= 0) continue;

            InventarioServicio.ExtraInsumo ex = new InventarioServicio.ExtraInsumo();
            ex.cantidad = cantidad;
            if (!insumoIdStr.isEmpty() && !insumoIdStr.equals("libre")) {
                try { ex.insumoId = Integer.parseInt(insumoIdStr); } catch (NumberFormatException ignored) {}
            }
            lista.add(ex);
        }
        return lista;
    }

    private double parsearDoubleSeguro(String texto, double porDefecto) {
        if (texto == null || texto.isBlank()) return porDefecto;
        try { return Double.parseDouble(texto); } catch (NumberFormatException e) { return porDefecto; }
    }

    // ═══════════════════════════════════════════════════════════════
    // RIEL DE ONDA SERENA — creación
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/guardar-riel-onda")
    @org.springframework.transaction.annotation.Transactional
    public String guardarRielOnda(
            @RequestParam String nombreDecorador,
            @RequestParam String nombreClienteFinal,
            @RequestParam(required = false) String descripcion,
            @RequestParam(required = false, defaultValue = "1") int cantidad,
            @RequestParam double ancho,
            @RequestParam(required = false) Double altura,
            @RequestParam(required = false, defaultValue = "false") boolean usaPolea,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        if (nombreDecorador == null || nombreDecorador.isBlank()
                || nombreClienteFinal == null || nombreClienteFinal.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Debes indicar distribuidor y cliente.");
            return "redirect:/taller/nuevo";
        }
        if (ancho <= 0) {
            redirectAttributes.addFlashAttribute("error", "El ancho debe ser mayor a 0.");
            return "redirect:/taller/nuevo";
        }

        List<Pedido> pedidosDelLote = new ArrayList<>();
        for (int j = 0; j < Math.max(cantidad, 1); j++) {
            Pedido p = new Pedido();
            p.setTipo("RIEL_ONDA_SERENA");
            p.setNombreDecorador(nombreDecorador);
            p.setNombreClienteFinal(nombreClienteFinal);
            p.setDescripcion(descripcion != null ? descripcion : "");
            p.setAncho(ancho);
            p.setAltura(altura != null ? altura : 0.0);
            p.setUsaPolea(usaPolea);
            p.setUsaCabezal(false);
            p.setUsaPitilloPesa(false);
            p.setUsaConectorTope(false);
            p.calcularFichaTecnica();
            p.calcularEstadoGeneral();
            pedidosDelLote.add(p);
        }

        try {
            for (Pedido p : pedidosDelLote) {
                inventarioServicio.verificarRielOndaSerena(p);
            }
            List<InventarioServicio.ExtraInsumo> extras = leerExtrasComoLista(allParams);
            inventarioServicio.verificarExtras(extras);
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/taller/nuevo";
        }

        try {
            for (int i = 0; i < pedidosDelLote.size(); i++) {
                Pedido p = pedidosDelLote.get(i);
                pedidoRepository.save(p);
                inventarioServicio.descontarRielOndaSerena(p);
                if (i == 0) {
                    procesarExtras(p, allParams);
                }
            }
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/taller/nuevo";
        }

        redirectAttributes.addFlashAttribute("mensaje",
                pedidosDelLote.size() + " pedido(s) de Riel de Onda Serena creados correctamente.");
        return "redirect:/taller/pedidos";
    }

    // ─── Formulario editar ────────────────────────────────────────────────
    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable("id") int id, Model model) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();

        // Los pedidos de Riel de Onda Serena tienen su propio formulario de edición
        // (ancho/alto/polea + accesorios calculados), distinto del de fabricación
        // normal (tela/tubo). Se redirige a esa vista específica.
        if (pedido.isRielOndaSerena()) {
            model.addAttribute("pedido", pedido);
            model.addAttribute("catalogoInsumos", insumoRepository.findAllByOrderByNombreAsc());
            model.addAttribute("piezasDisponibles", inventarioServicio.getTodasLasPiezas());

            List<MaterialUsado> extrasExistentes = inventarioServicio.getHistorialDePedido(id).stream()
                    .filter(m -> "EXTRA".equals(m.getTipoMaterial()))
                    .collect(Collectors.toList());
            model.addAttribute("extrasExistentes", extrasExistentes);

            return "editar_pedido_riel";
        }

        model.addAttribute("pedido",            pedido);
        model.addAttribute("listaColores",       Arrays.asList("Blanco", "Gris", "Fawn", "Vainilla"));
        model.addAttribute("rollosDisponibles",  inventarioServicio.getTodosLosRollos());
        model.addAttribute("piezasDisponibles",  inventarioServicio.getTodasLasPiezas());
        model.addAttribute("retazosDisponibles", inventarioServicio.getTodosLosRetazos());
        model.addAttribute("catalogoInsumos",    insumoRepository.findAllByOrderByNombreAsc());

        // Extras ya registrados en este pedido (para mostrarlos pre-cargados)
        List<MaterialUsado> extrasExistentes = inventarioServicio.getHistorialDePedido(id).stream()
                .filter(m -> "EXTRA".equals(m.getTipoMaterial()))
                .collect(Collectors.toList());
        model.addAttribute("extrasExistentes", extrasExistentes);

        return "editar_pedido";
    }

    // ─── Guardar edición (fabricación) ──────────────────────────────────────
    @PostMapping("/editar/{id}")
    @org.springframework.transaction.annotation.Transactional
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
            @RequestParam(required = false, defaultValue = "true")  boolean usaConectorTope,
            @RequestParam(required = false) String tipoTuboManual,
            @RequestParam(required = false) String rolloManual,
            @RequestParam(required = false) String retazoManual,
            @RequestParam(required = false) String tuboManual,
            @RequestParam(required = false) String cabezalManual,
            @RequestParam(required = false) String pesaManual,
            @RequestParam(required = false) String cuerdaManual,
            @RequestParam(required = false) String pitilloManual,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();

        // Salvaguarda: este endpoint es solo para pedidos de fabricación normal.
        // Si por alguna razón llega aquí un riel (por ejemplo, un POST directo),
        // se rechaza en vez de aplicar lógica de tela/tubo que no le corresponde.
        if (pedido.isRielOndaSerena()) {
            redirectAttributes.addFlashAttribute("error",
                    "Este pedido es un Riel de Onda Serena; usa su propio formulario de edición.");
            return "redirect:/taller/editar/" + id;
        }

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

        // ── FIX: antes esto guardaba el texto literal "auto" cuando el select
        // quedaba en modo automático, en vez de null. calcularFichaTecnica()
        // espera null para recalcular el tubo automáticamente; con la cadena
        // "auto" ahí metida, la selección manual del tipo de tubo (R8/R16/R24)
        // quedaba inconsistente y el pedido se quedaba "pegado" al tubo previo.
        // Ahora se normaliza igual que en guardarListaPedidos (leerTextoOpcionalFila). ──
        pedido.setTuboManualElegido(normalizarTuboManual(tipoTuboManual));
        pedido.calcularFichaTecnica();
        if (estado != null && !estado.isBlank()) {
            pedido.setEstado(estado);
        } else {
            pedido.calcularEstadoGeneral();
        }

        InventarioServicio.SeleccionManual seleccion = new InventarioServicio.SeleccionManual();
        seleccion.rolloTelaId    = parsearIdManual(rolloManual);
        seleccion.retazoTelaId   = parsearIdManual(retazoManual);
        seleccion.piezaTuboId    = parsearIdManual(tuboManual);
        seleccion.piezaCabezalId = parsearIdManual(cabezalManual);
        seleccion.piezaPesaId    = parsearIdManual(pesaManual);
        seleccion.piezaCuerdaId  = parsearIdManual(cuerdaManual);
        seleccion.piezaPitilloId = parsearIdManual(pitilloManual);

        if (seleccion.rolloTelaId != null && seleccion.retazoTelaId != null) {
            seleccion.retazoTelaId = null;
        }

        // Revertir TODO (incluyendo extras anteriores que hayan descontado stock)
        try {
            inventarioServicio.revertirMaterialDe(id);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "No se pudo revertir el material del pedido: " + e.getMessage());
            return "redirect:/taller/editar/" + id;
        }

        try {
            inventarioServicio.verificarDisponibilidad(pedido, seleccion);
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error",
                    "No hay suficiente material para la nueva configuración: " + e.getMessage());
            return "redirect:/taller/editar/" + id;
        }

        pedidoRepository.save(pedido);

        try {
            inventarioServicio.descontarMaterialDe(pedido, seleccion);
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al descontar material: " + e.getMessage());
            return "redirect:/taller/editar/" + id;
        }

       // Procesar extras nuevos
        try {
            procesarExtras(pedido, allParams);
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Pedido guardado, pero hubo un problema con un insumo extra: " + e.getMessage());
            return "redirect:/taller/pedidos";
        }

        redirectAttributes.addFlashAttribute("mensaje", "Pedido actualizado. Inventario reajustado correctamente.");
        return "redirect:/taller/pedidos";
    }

    // ─── Guardar edición (Riel de Onda Serena) ─────────────────────────────
    @PostMapping("/editar-riel/{id}")
    @org.springframework.transaction.annotation.Transactional
    public String guardarEdicionRiel(
            @PathVariable("id") int id,
            @RequestParam String nombreDecorador,
            @RequestParam String nombreClienteFinal,
            @RequestParam(required = false) String descripcion,
            @RequestParam double ancho,
            @RequestParam(required = false) Double altura,
            @RequestParam(required = false, defaultValue = "false") boolean usaPolea,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();

        if (!pedido.isRielOndaSerena()) {
            redirectAttributes.addFlashAttribute("error",
                    "Este pedido no es un Riel de Onda Serena.");
            return "redirect:/taller/pedidos";
        }
        if (ancho <= 0) {
            redirectAttributes.addFlashAttribute("error", "El ancho debe ser mayor a 0.");
            return "redirect:/taller/editar/" + id;
        }

        // Revertir TODO el material anterior: los cortes (riel/riel de pines/cuerda),
        // los accesorios obligatorios (poleas+terminal o tapas+bastón) y cualquier
        // extra que se haya agregado antes. revertirMaterialDe() ya es genérico
        // (funciona por pieza/insumo sin importar el tipo de pedido).
        try {
            inventarioServicio.revertirMaterialDe(id);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "No se pudo revertir el material del pedido: " + e.getMessage());
            return "redirect:/taller/editar/" + id;
        }

        pedido.setNombreDecorador(nombreDecorador);
        pedido.setNombreClienteFinal(nombreClienteFinal);
        pedido.setDescripcion(descripcion != null ? descripcion : "");
        pedido.setAncho(ancho);
        pedido.setAltura(altura != null ? altura : 0.0);
        pedido.setUsaPolea(usaPolea);
        pedido.calcularFichaTecnica();

        try {
            inventarioServicio.verificarRielOndaSerena(pedido);
            List<InventarioServicio.ExtraInsumo> extras = leerExtrasComoLista(allParams);
            inventarioServicio.verificarExtras(extras);
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error",
                    "No hay suficiente material para la nueva configuración: " + e.getMessage());
            return "redirect:/taller/editar/" + id;
        }

        pedidoRepository.save(pedido);

        try {
            inventarioServicio.descontarRielOndaSerena(pedido);
            procesarExtras(pedido, allParams);
        } catch (InventarioServicio.MaterialInsuficienteException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al descontar material: " + e.getMessage());
            return "redirect:/taller/pedidos";
        }

        redirectAttributes.addFlashAttribute("mensaje",
                "Pedido de Riel de Onda Serena actualizado. Inventario reajustado correctamente.");
        return "redirect:/taller/pedidos";
    }

    // ═══════════════════════════════════════════════════════════════
    // INSUMOS EXTRAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Procesa los insumos extras opcionales que el jefe agregó al pedido.
     *
     * Cada extra llega como grupo de 4 parámetros con índice [i]:
     *   extraInsumoId[i]     → id del insumo del catálogo, o "libre" si es nombre libre
     *   extraNombreLibre[i]  → nombre escrito a mano (solo si extraInsumoId = "libre")
     *   extraCantidad[i]     → cantidad numérica
     *   extraUnidad[i]       → "und" o "m"
     *
     * Si viene del catálogo:
     *   - Insumo por UNIDAD → descuenta stockUnidades real
     *   - Insumo por MEDIDA → descuenta de la primera pieza disponible (ciclo hasta cubrir)
     *
     * Si es nombre libre → solo registra el gasto en MaterialUsado, sin tocar inventario.
     *
     * En todos los casos queda un registro MaterialUsado con tipoMaterial = "EXTRA".
     *
     * Reutilizado tanto por fabricación normal (guardar-lista, editar) como por
     * Venta Directa (guardar-venta-directa) y Riel de Onda Serena
     * (guardar-riel-onda, editar-riel), sin ninguna diferencia entre los flujos.
     */
   private void procesarExtras(Pedido pedido, Map<String, String> allParams) {
        int i = 0;
        while (allParams.containsKey("extraCantidad[" + i + "]")) {
            String cantidadStr  = allParams.getOrDefault("extraCantidad[" + i + "]", "").trim();
            String insumoIdStr  = allParams.getOrDefault("extraInsumoId[" + i + "]", "").trim();
            String nombreLibre  = allParams.getOrDefault("extraNombreLibre[" + i + "]", "").trim();
            String unidad       = allParams.getOrDefault("extraUnidad["       + i + "]", "und").trim();
            String piezaIdStr   = allParams.getOrDefault("extraPiezaId["      + i + "]", "auto").trim();
            i++;

            if (cantidadStr.isEmpty()) continue;
            double cantidad;
            try { cantidad = Double.parseDouble(cantidadStr); } catch (NumberFormatException e) { continue; }
            if (cantidad <= 0) continue;

            MaterialUsado registro = new MaterialUsado();
            registro.setPedidoId(pedido.getId());
            registro.setTipoMaterial("EXTRA");
            registro.setMetrosUsados(cantidad);
            registro.setMetrosSobrantes(0);
            registro.setSeleccionManual(true);

            if (!insumoIdStr.isEmpty() && !insumoIdStr.equals("libre")) {
                try {
                    int insumoId = Integer.parseInt(insumoIdStr);
                    Insumo insumo = insumoRepository.findById(insumoId).orElse(null);

                    if (insumo != null) {
                        if (Boolean.TRUE.equals(insumo.getTieneMedida())) {
                            boolean piezaManual = !piezaIdStr.isEmpty() && !piezaIdStr.equalsIgnoreCase("auto");

                            if (piezaManual) {
                                // ── Pieza específica elegida por el jefe ──
                                Integer piezaId = null;
                                try { piezaId = Integer.parseInt(piezaIdStr); } catch (NumberFormatException ignored) {}

                                PiezaInsumo pieza = (piezaId != null)
                                        ? piezaInsumoRepository.findById(piezaId).orElse(null)
                                        : null;

                                if (pieza == null || pieza.getInsumo() == null || pieza.getInsumo().getId() != insumoId) {
                                    piezaManual = false;
                                } else {
                                    if (pieza.getLargoRestante() < cantidad - 0.001) {
                                        throw new InventarioServicio.MaterialInsuficienteException(
                                            "La pieza #" + piezaId + " de \"" + insumo.getNombre() + "\" no tiene suficiente material ("
                                            + pieza.getLargoRestante() + " m disponibles, " + cantidad + " m necesarios).");
                                    }
                                    pieza.setLargoRestante(Math.round((pieza.getLargoRestante() - cantidad) * 1000.0) / 1000.0);
                                    piezaInsumoRepository.save(pieza);
                                    registro.setPiezaInsumoId(pieza.getId());
                                    registro.setFuenteDescripcion(insumo.getNombre()
                                            + " (#" + pieza.getId() + ") [extra] (" + cantidad + " " + unidad + ") — pieza elegida por ti");
                                }
                            }

                            if (!piezaManual) {
                                List<PiezaInsumo> piezas = piezaInsumoRepository
                                        .findByInsumoIdAndLargoRestanteGreaterThanOrderByLargoRestanteAsc(insumoId, 0.0);
                                double restante = cantidad;
                                for (PiezaInsumo pieza : piezas) {
                                    if (restante <= 0.001) break;
                                    double aUsar = Math.min(pieza.getLargoRestante(), restante);
                                    pieza.setLargoRestante(
                                            Math.round((pieza.getLargoRestante() - aUsar) * 1000.0) / 1000.0);
                                    piezaInsumoRepository.save(pieza);
                                    restante = Math.round((restante - aUsar) * 1000.0) / 1000.0;
                                }
                                if (restante > 0.001) {
                                    throw new InventarioServicio.MaterialInsuficienteException(
                                            "No hay suficiente \"" + insumo.getNombre() + "\" para completar el insumo extra.");
                                }
                                registro.setFuenteDescripcion(insumo.getNombre()
                                        + " [extra] (" + cantidad + " " + unidad + ")");
                            }
                        } else {
                            int cantInt = (int) Math.round(cantidad);
                            int disponible = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;
                            int nuevoStock = Math.max(0, disponible - cantInt);
                            insumo.setStockUnidades(nuevoStock);
                            insumoRepository.save(insumo);
                            registro.setMetrosSobrantes(nuevoStock);
                            registro.setFuenteDescripcion(insumo.getNombre()
                                    + " [extra] (" + cantInt + " und.)");
                        }
                    } else {
                        registro.setFuenteDescripcion("Insumo #" + insumoIdStr + " [extra, no encontrado]");
                    }
                } catch (NumberFormatException e) {
                    registro.setFuenteDescripcion(insumoIdStr + " [extra]");
                }
            } else {
                String desc = nombreLibre.isEmpty() ? "Insumo adicional" : nombreLibre;
                registro.setFuenteDescripcion(desc + " [extra libre, sin descuento] ("
                        + cantidad + " " + unidad + ")");
            }

            materialUsadoRepository.save(registro);
        }
    }

    // ─── Helpers de lectura de formulario ────────────────────────────────
    private boolean leerBooleanoFila(Map<String, String> allParams, String nombreCampo, int indice, boolean porDefecto) {
        String clave = nombreCampo + "[" + indice + "]";
        if (!allParams.containsKey(clave)) return porDefecto;
        return "true".equals(allParams.get(clave));
    }

    private String leerTextoOpcionalFila(Map<String, String> allParams, String nombreCampo, int indice) {
        String clave = nombreCampo + "[" + indice + "]";
        String texto = allParams.get(clave);
        if (texto == null || texto.isBlank() || "auto".equalsIgnoreCase(texto)) return null;
        return texto.trim();
    }

    private Integer leerIdOpcionalFila(Map<String, String> allParams, String nombreCampo, int indice) {
        String clave = nombreCampo + "[" + indice + "]";
        String texto = allParams.get(clave);
        if (texto == null || texto.isBlank() || "auto".equalsIgnoreCase(texto)) return null;
        try { return Integer.parseInt(texto.trim()); } catch (NumberFormatException e) { return null; }
    }

    private Integer parsearIdManual(String valor) {
        if (valor == null || valor.isBlank() || "auto".equalsIgnoreCase(valor.trim())) return null;
        try { return Integer.parseInt(valor.trim()); } catch (NumberFormatException e) { return null; }
    }

    /**
     * Normaliza el valor del select de tipo de tubo (R8/R16/R24/"auto") a lo que
     * espera Pedido.calcularFichaTecnica(): null cuando es automático, o el texto
     * elegido (recortado) cuando es manual. Se usa tanto en la previsualización
     * AJAX como al editar, igual que ya se hacía al crear (leerTextoOpcionalFila).
     */
    private String normalizarTuboManual(String valor) {
        if (valor == null || valor.isBlank() || "auto".equalsIgnoreCase(valor.trim())) return null;
        return valor.trim();
    }

    // ─── Actualizar estado ────────────────────────────────────────────────
    @PostMapping("/actualizar/{id}/{accion}")
    public String actualizarEstado(
            @PathVariable("id") int id,
            @PathVariable("accion") String accion,
            @RequestParam(required = false, defaultValue = "Todos") String estadoFiltro,
            RedirectAttributes redirectAttributes) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        switch (accion.toLowerCase()) {
            case "ensamblado":
                pedido.setEnsamblado(!pedido.getEnsamblado());
                // Si se desmarca "ensamblado", el pedido ya no puede seguir "despachado"
                if (!Boolean.TRUE.equals(pedido.getEnsamblado())) {
                    pedido.setDespachado(false);
                }
                break;
            case "despachado":
                if (Boolean.TRUE.equals(pedido.getEnsamblado())) {
                    pedido.setDespachado(!pedido.getDespachado());
                } else {
                    redirectAttributes.addFlashAttribute("error",
                            "¡Error! Primero debes finalizar (ensamblar) el pedido.");
                }
                break;
        }
        pedido.calcularEstadoGeneral();
        pedidoRepository.save(pedido);
        return "redirect:/taller/pedidos?estado=" + estadoFiltro;
    }

    // ─── Imprimir ─────────────────────────────────────────────────────────
    @GetMapping("/imprimir/{id}")
    public String imprimirPedido(@PathVariable("id") int id, Model model) {
        Pedido p = pedidoRepository.findById(id).orElseThrow();
        p.calcularFichaTecnica();
        model.addAttribute("pedido", p);

        List<MaterialUsado> historial = inventarioServicio.getHistorialDePedido(id);
        model.addAttribute("historialMaterial", historial);

        if (p.isRielOndaSerena()) {
            // ── Riel de Onda Serena: busca sus propios cortes en el historial ──
            MaterialUsado materialRiel = historial.stream()
                    .filter(m -> "RIEL_ONDA_SERENA".equals(m.getTipoMaterial()))
                    .findFirst().orElse(null);
            model.addAttribute("materialRiel", materialRiel);

            MaterialUsado materialRielPines = historial.stream()
                    .filter(m -> "RIEL_DE_PINES".equals(m.getTipoMaterial()))
                    .findFirst().orElse(null);
            model.addAttribute("materialRielPines", materialRielPines);

            MaterialUsado materialCuerdaOnda = historial.stream()
                    .filter(m -> "CUERDA_ONDA_SERENA".equals(m.getTipoMaterial()))
                    .findFirst().orElse(null);
            model.addAttribute("materialCuerdaOnda", materialCuerdaOnda);

        } else {
            // ── Fabricación normal (enrollable / blackout): tela, tubo, pesa, cabezal ──
            MaterialUsado materialTela = historial.stream()
                    .filter(m -> "TELA".equals(m.getTipoMaterial()) || "RETAZO".equals(m.getTipoMaterial()))
                    .findFirst().orElse(null);
            model.addAttribute("materialTela", materialTela);
            model.addAttribute("esRetazo", materialTela != null && "RETAZO".equals(materialTela.getTipoMaterial()));

            MaterialUsado materialTubo = historial.stream()
                    .filter(m -> m.getFuenteDescripcion() != null
                            && m.getFuenteDescripcion().toLowerCase().startsWith("tubo"))
                    .findFirst().orElse(null);
            model.addAttribute("materialTubo", materialTubo);

            MaterialUsado materialPesa = historial.stream()
                    .filter(m -> m.getFuenteDescripcion() != null
                            && m.getFuenteDescripcion().toLowerCase().startsWith("pesa"))
                    .findFirst().orElse(null);
            model.addAttribute("materialPesa", materialPesa);

            MaterialUsado materialCabezal = historial.stream()
                    .filter(m -> m.getFuenteDescripcion() != null
                            && m.getFuenteDescripcion().toLowerCase().startsWith("cabezal"))
                    .findFirst().orElse(null);
            model.addAttribute("materialCabezal", materialCabezal);
        }

        // ── Extras (comunes a todos los tipos de pedido) ──
        List<MaterialUsado> extrasHistorial = historial.stream()
                .filter(m -> "EXTRA".equals(m.getTipoMaterial()))
                .collect(Collectors.toList());
        model.addAttribute("extrasHistorial", extrasHistorial);

        return "imprimir_pedido";
    }

    // ─── Eliminar pedido ──────────────────────────────────────────────────
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

    // ─── Reporte de materiales ────────────────────────────────────────────
    @GetMapping("/reporte-materiales")
    public String reporteMateriales(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            Model model) {
        LocalDateTime fechaDesde = (desde != null && !desde.isBlank()) ? LocalDateTime.parse(desde + "T00:00:00") : null;
        LocalDateTime fechaHasta = (hasta != null && !hasta.isBlank()) ? LocalDateTime.parse(hasta + "T23:59:59") : null;
        model.addAttribute("resumen", inventarioServicio.obtenerResumenConsumo(fechaDesde, fechaHasta));
        model.addAttribute("desde", desde != null ? desde : "");
        model.addAttribute("hasta", hasta != null ? hasta : "");
        return "reporte_materiales";
    }
}