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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

            Map<Integer, String> telaUsadaPorPedido = new HashMap<>();
            for (Pedido p : pedidos) {
                inventarioServicio.getHistorialDePedido(p.getId()).stream()
                        .filter(m -> "TELA".equals(m.getTipoMaterial()) || "RETAZO".equals(m.getTipoMaterial()))
                        .findFirst()
                        .ifPresent(m -> telaUsadaPorPedido.put(p.getId(), m.getFuenteDescripcion()));
            }
            model.addAttribute("telaUsadaPorPedido", telaUsadaPorPedido);

        } catch (Exception e) {
            System.err.println("Error al cargar pedidos: " + e.getMessage());
            model.addAttribute("pedidos",            new java.util.ArrayList<Pedido>());
            model.addAttribute("estadoActivo",       "Todos");
            model.addAttribute("telaUsadaPorPedido", new HashMap<>());
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
        p.setTuboManualElegido(tipoTuboManual);
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

        List<Pedido> pedidosDelLote = new java.util.ArrayList<>();
        List<InventarioServicio.SeleccionManual> seleccionesDelLote = new java.util.ArrayList<>();

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

    // ─── Formulario editar ────────────────────────────────────────────────
    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable("id") int id, Model model) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
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

    // ─── Guardar edición ──────────────────────────────────────────────────
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
        pedido.setTuboManualElegido(tipoTuboManual);
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
        procesarExtras(pedido, allParams);

        redirectAttributes.addFlashAttribute("mensaje", "Pedido actualizado. Inventario reajustado correctamente.");
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
     */
    private void procesarExtras(Pedido pedido, Map<String, String> allParams) {
        int i = 0;
        while (allParams.containsKey("extraCantidad[" + i + "]")) {
            String cantidadStr  = allParams.getOrDefault("extraCantidad["    + i + "]", "").trim();
            String insumoIdStr  = allParams.getOrDefault("extraInsumoId["    + i + "]", "").trim();
            String nombreLibre  = allParams.getOrDefault("extraNombreLibre[" + i + "]", "").trim();
            String unidad       = allParams.getOrDefault("extraUnidad["       + i + "]", "und").trim();
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
                // ── Insumo del catálogo ───────────────────────────────────
                try {
                    int insumoId = Integer.parseInt(insumoIdStr);
                    Insumo insumo = insumoRepository.findById(insumoId).orElse(null);

                    if (insumo != null) {
                        if (Boolean.TRUE.equals(insumo.getTieneMedida())) {
                            // Descontar de piezas disponibles (de menor a mayor sobrante)
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
                            registro.setFuenteDescripcion(insumo.getNombre()
                                    + " [extra] (" + cantidad + " " + unidad + ")");
                        } else {
                            // Descontar unidades
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
                // ── Nombre libre — sin descuento de inventario ────────────
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

    // ─── Actualizar estado ────────────────────────────────────────────────
    @PostMapping("/actualizar/{id}/{accion}")
    public String actualizarEstado(
            @PathVariable("id") int id,
            @PathVariable("accion") String accion,
            @RequestParam(required = false, defaultValue = "Todos") String estadoFiltro,
            RedirectAttributes redirectAttributes) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        switch (accion.toLowerCase()) {
            case "tela":      pedido.setTelaCortada(!pedido.getTelaCortada()); break;
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

        // ── Tela: primer registro TELA o RETAZO ──────────────────
        MaterialUsado materialTela = historial.stream()
                .filter(m -> "TELA".equals(m.getTipoMaterial()) || "RETAZO".equals(m.getTipoMaterial()))
                .findFirst().orElse(null);
        model.addAttribute("materialTela", materialTela);
        model.addAttribute("esRetazo", materialTela != null && "RETAZO".equals(materialTela.getTipoMaterial()));

        // ── Tubo: registro cuya fuente empieza por "Tubo" ────────
        String nombreTubo = "Tubo " + p.getTuboRecomendado();
        MaterialUsado materialTubo = historial.stream()
                .filter(m -> m.getFuenteDescripcion() != null
                        && m.getFuenteDescripcion().toLowerCase().startsWith("tubo"))
                .findFirst().orElse(null);
        model.addAttribute("materialTubo", materialTubo);

        // ── Pesa ─────────────────────────────────────────────────
        MaterialUsado materialPesa = historial.stream()
                .filter(m -> m.getFuenteDescripcion() != null
                        && m.getFuenteDescripcion().toLowerCase().startsWith("pesa"))
                .findFirst().orElse(null);
        model.addAttribute("materialPesa", materialPesa);

        // ── Cabezal ───────────────────────────────────────────────
        MaterialUsado materialCabezal = historial.stream()
                .filter(m -> m.getFuenteDescripcion() != null
                        && m.getFuenteDescripcion().toLowerCase().startsWith("cabezal"))
                .findFirst().orElse(null);
        model.addAttribute("materialCabezal", materialCabezal);

        // ── Extras ───────────────────────────────────────────────
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