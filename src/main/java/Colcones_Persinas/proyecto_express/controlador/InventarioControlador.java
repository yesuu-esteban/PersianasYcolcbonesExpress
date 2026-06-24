package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.Insumo;
import Colcones_Persinas.proyecto_express.modelo.PiezaInsumo;
import Colcones_Persinas.proyecto_express.modelo.RetazoTela;
import Colcones_Persinas.proyecto_express.modelo.RolloTela;
import Colcones_Persinas.proyecto_express.repository.InsumoRepository;
import Colcones_Persinas.proyecto_express.repository.PiezaInsumoRepository;
import Colcones_Persinas.proyecto_express.repository.RetazoTelaRepository;
import Colcones_Persinas.proyecto_express.repository.RolloTelaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/inventario")
public class InventarioControlador {

    @Autowired
    private RolloTelaRepository rolloTelaRepository;

    @Autowired
    private InsumoRepository insumoRepository;

    @Autowired
    private PiezaInsumoRepository piezaInsumoRepository;

    @Autowired
    private RetazoTelaRepository retazoTelaRepository;

    private static final List<String> COLORES = Arrays.asList("Blanco", "Gris", "Fawn", "Vainilla");
    private static final List<Double> ANCHOS = Arrays.asList(1.83, 2.50, 3.00);

    // ─── PANTALLA PRINCIPAL ──────────────────────────────────────────────────

    @GetMapping
    public String verInventario(Model model) {
        List<RolloTela> rollos = rolloTelaRepository.findAllByOrderByColorAscAnchoAscLargoRestanteAsc();
        List<Insumo> insumos = insumoRepository.findAllByOrderByNombreAsc();
        List<RetazoTela> retazos = retazoTelaRepository.findAllByOrderByColorAscAnchoAscAltoAsc();

        List<PiezaInsumo> piezas = new java.util.ArrayList<>();
        for (Insumo insumo : insumos) {
            if (Boolean.TRUE.equals(insumo.getTieneMedida())) {
                piezas.addAll(piezaInsumoRepository.findByInsumoIdOrderByLargoRestanteAsc(insumo.getId()));
            }
        }

        Map<Integer, List<PiezaInsumo>> piezasPorInsumo = piezas.stream()
                .collect(Collectors.groupingBy(p -> p.getInsumo().getId()));

        long rollosAgotados = rollos.stream().filter(RolloTela::isAgotado).count();
        long rollosRetazo = rollos.stream().filter(RolloTela::isRetazo).count();

        model.addAttribute("rollos", rollos);
        model.addAttribute("insumos", insumos);
        model.addAttribute("piezas", piezas);
        model.addAttribute("piezasPorInsumo", piezasPorInsumo);
        model.addAttribute("rollosAgotados", rollosAgotados);
        model.addAttribute("rollosRetazo", rollosRetazo);
        model.addAttribute("retazos", retazos);
        model.addAttribute("colores", COLORES);
        model.addAttribute("anchos", ANCHOS);
        return "inventario";
    }

    // ─── ROLLOS DE TELA ──────────────────────────────────────────────────────

    @GetMapping("/rollo/nuevo")
    public String mostrarFormularioRollo(Model model) {
        model.addAttribute("colores", COLORES);
        model.addAttribute("anchos", ANCHOS);
        return "nuevo_rollo";
    }

    @PostMapping("/rollo/nuevo")
    public String guardarRollo(
            @RequestParam String color,
            @RequestParam double ancho,
            @RequestParam(required = false, defaultValue = "1") int cantidad,
            RedirectAttributes redirectAttributes) {

        for (int i = 0; i < cantidad; i++) {
            RolloTela rollo = new RolloTela();
            rollo.setColor(color);
            rollo.setAncho(ancho);
            rollo.setLargoInicial(30.0);
            rollo.setLargoRestante(30.0);
            rolloTelaRepository.save(rollo);
        }

        redirectAttributes.addFlashAttribute("mensaje",
                cantidad + " rollo(s) de " + color + " " + ancho + "m agregado(s) al inventario.");
        return "redirect:/inventario";
    }

    @PostMapping("/rollo/eliminar/{id}")
    public String eliminarRollo(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            rolloTelaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Rollo eliminado del inventario.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el rollo: " + e.getMessage());
        }
        return "redirect:/inventario";
    }

    // ─── RETAZOS DE TELA ─────────────────────────────────────────────────────

    @PostMapping("/retazo/nuevo")
    public String guardarRetazo(
            @RequestParam String color,
            @RequestParam double ancho,
            @RequestParam double alto,
            RedirectAttributes redirectAttributes) {

        if (alto <= 0 || ancho <= 0) {
            redirectAttributes.addFlashAttribute("error", "El ancho y el alto del retazo deben ser mayores a 0.");
            return "redirect:/inventario";
        }

        RetazoTela retazo = new RetazoTela();
        retazo.setColor(color);
        retazo.setAncho(ancho);
        retazo.setAlto(alto);
        retazoTelaRepository.save(retazo);

        redirectAttributes.addFlashAttribute("mensaje",
                "Retazo de tela " + color + " (" + ancho + "m × " + alto + "m) registrado.");
        return "redirect:/inventario";
    }

    @PostMapping("/retazo/eliminar/{id}")
    public String eliminarRetazo(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            retazoTelaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Retazo eliminado del inventario.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el retazo: " + e.getMessage());
        }
        return "redirect:/inventario";
    }

    // ─── INSUMOS ─────────────────────────────────────────────────────────────

    @GetMapping("/insumo/nuevo")
    public String mostrarFormularioInsumo() {
        return "nuevo_insumo";
    }

    @PostMapping("/insumo/nuevo")
    public String guardarInsumoNuevo(
            @RequestParam List<String> nombres,
            @RequestParam(required = false) List<String> descripciones,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        List<String> creados = new java.util.ArrayList<>();
        List<String> errores = new java.util.ArrayList<>();

        for (int i = 0; i < nombres.size(); i++) {
            String nombre = nombres.get(i).trim();
            if (nombre.isEmpty()) continue;

            if (insumoRepository.findByNombreIgnoreCase(nombre).isPresent()) {
                errores.add("Ya existe un insumo llamado \"" + nombre + "\" (omitido).");
                continue;
            }

            boolean tieneMedida = "true".equals(allParams.getOrDefault("tieneMedida[" + i + "]", "false"));
            String descripcion = (descripciones != null && i < descripciones.size()) ? descripciones.get(i) : "";

            Insumo insumo = new Insumo();
            insumo.setNombre(nombre);
            insumo.setTieneMedida(tieneMedida);
            insumo.setDescripcion(descripcion != null ? descripcion : "");
            insumo.setStockUnidades(0);

            if (tieneMedida) {
                String largoTexto = allParams.get("largoInicial[" + i + "]");
                String cantidadTexto = allParams.get("cantidadPiezas[" + i + "]");
                double largo = parsearDouble(largoTexto, 0.0);
                int cantidadPiezas = (int) parsearDouble(cantidadTexto, 1.0);

                if (largo <= 0) {
                    errores.add("\"" + nombre + "\": debes indicar el largo de cada pieza (omitido).");
                    continue;
                }

                insumo = insumoRepository.save(insumo);
                for (int p = 0; p < cantidadPiezas; p++) {
                    PiezaInsumo pieza = new PiezaInsumo();
                    pieza.setInsumo(insumo);
                    pieza.setLargoInicial(largo);
                    pieza.setLargoRestante(largo);
                    piezaInsumoRepository.save(pieza);
                }
                creados.add(nombre + " (" + cantidadPiezas + " pieza(s) de " + largo + " m)");
            } else {
                String unidadesTexto = allParams.get("unidadesIniciales[" + i + "]");
                int unidades = (int) parsearDouble(unidadesTexto, 0.0);
                insumo.setStockUnidades(unidades);
                insumoRepository.save(insumo);
                creados.add(nombre + " (" + unidades + " unidad(es))");
            }
        }

        StringBuilder mensaje = new StringBuilder();
        if (!creados.isEmpty()) {
            mensaje.append("Insumos creados: ").append(String.join(", ", creados)).append(". ");
        }
        if (!errores.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", String.join(" ", errores));
        }
        if (!mensaje.isEmpty()) {
            redirectAttributes.addFlashAttribute("mensaje", mensaje.toString());
        }

        return "redirect:/inventario";
    }

    private double parsearDouble(String texto, double porDefecto) {
        if (texto == null || texto.isBlank()) return porDefecto;
        try {
            return Double.parseDouble(texto);
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    @GetMapping("/insumo/{id}/cargar")
    public String mostrarFormularioCargarInsumo(@PathVariable("id") int id, Model model) {
        Insumo insumo = insumoRepository.findById(id).orElseThrow();
        model.addAttribute("insumo", insumo);
        return "cargar_insumo";
    }

    @PostMapping("/insumo/{id}/cargar")
    public String cargarStockInsumo(
            @PathVariable("id") int id,
            @RequestParam(required = false) Double largo,
            @RequestParam(required = false) Integer unidades,
            @RequestParam(required = false, defaultValue = "1") int cantidadPiezas,
            RedirectAttributes redirectAttributes) {

        Insumo insumo = insumoRepository.findById(id).orElseThrow();

        if (Boolean.TRUE.equals(insumo.getTieneMedida())) {
            if (largo == null || largo <= 0) {
                redirectAttributes.addFlashAttribute("error", "Debes indicar el largo de la pieza a agregar.");
                return "redirect:/inventario/insumo/" + id + "/cargar";
            }
            for (int i = 0; i < cantidadPiezas; i++) {
                PiezaInsumo pieza = new PiezaInsumo();
                pieza.setInsumo(insumo);
                pieza.setLargoInicial(largo);
                pieza.setLargoRestante(largo);
                piezaInsumoRepository.save(pieza);
            }
            redirectAttributes.addFlashAttribute("mensaje",
                    cantidadPiezas + " pieza(s) de " + insumo.getNombre() + " (" + largo + " m c/u) agregada(s).");
        } else {
            if (unidades == null || unidades <= 0) {
                redirectAttributes.addFlashAttribute("error", "Debes indicar cuántas unidades vas a agregar.");
                return "redirect:/inventario/insumo/" + id + "/cargar";
            }
            int actual = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;
            insumo.setStockUnidades(actual + unidades);
            insumoRepository.save(insumo);
            redirectAttributes.addFlashAttribute("mensaje",
                    unidades + " unidad(es) de " + insumo.getNombre() + " agregada(s). Total ahora: "
                    + insumo.getStockUnidades());
        }

        return "redirect:/inventario";
    }

    @PostMapping("/insumo/pieza/eliminar/{id}")
    public String eliminarPieza(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            piezaInsumoRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Pieza eliminada del inventario.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar la pieza: " + e.getMessage());
        }
        return "redirect:/inventario";
    }
}