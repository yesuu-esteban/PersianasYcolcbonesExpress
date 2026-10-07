package Colcones_Persinas.proyecto_express.controlador.instalaciones;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.instalaciones.FormularioTarea;
import Colcones_Persinas.proyecto_express.modelo.instalaciones.TareaCalendario;
import Colcones_Persinas.proyecto_express.modelo.usuarios.Usuario;
import Colcones_Persinas.proyecto_express.servicio.instalaciones.CalendarioServicio;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Módulo de INSTALACIONES (separado del listado de pedidos de almacén).
 * Lo usa el jefe (TIENDA_ADMIN / ADMIN) para:
 *  - Ver las "Instalaciones pendientes": pedidos que ya están "En Bodega".
 *  - Asignarlas a 1 o 2 instaladores con fecha y hora.
 *  - Poner, editar o eliminar SUS tareas (limpieza, arreglo, cotización, otro).
 *  - Ver la agenda de cada instalador o de todos juntos. Las tareas personales de
 *    un instalador las ve en solo lectura: no las puede modificar ni eliminar.
 */
@Controller
@RequestMapping("/instalaciones")
@PreAuthorize("hasAnyRole('TIENDA_ADMIN','ADMIN')")
public class InstalacionesControlador {

    private static final DateTimeFormatter FMT_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final CalendarioServicio servicio;

    public InstalacionesControlador(CalendarioServicio servicio) {
        this.servicio = servicio;
    }

    // ─── Panel principal: pendientes + agenda ─────────────────────────
    @GetMapping
    public String panel(@RequestParam(name = "instalador", required = false) Integer instaladorId, Model model) {
        List<Usuario> instaladores = servicio.listarInstaladoresActivos();
        Usuario seleccionado = (instaladorId == null) ? null
                : instaladores.stream().filter(u -> u.getId() == instaladorId).findFirst().orElse(null);

        model.addAttribute("instaladores", instaladores);
        model.addAttribute("instaladorSel", seleccionado);
        model.addAttribute("instaladorSelNombre", seleccionado != null ? TareaCalendario.nombreVisible(seleccionado) : null);
        model.addAttribute("pendientes", servicio.instalacionesPendientes());
        return "instalaciones/panel";
    }

    @GetMapping("/eventos")
    @ResponseBody
    public List<Map<String, Object>> eventos(@RequestParam String start,
                                             @RequestParam String end,
                                             @RequestParam(required = false) Integer instaladorId) {
        LocalDateTime desde = CalendarioServicio.parsearFechaCalendario(start);
        LocalDateTime hasta = CalendarioServicio.parsearFechaCalendario(end);
        if (desde == null || hasta == null) return List.of();

        boolean todos = (instaladorId == null);
        return servicio.listarParaAdmin(desde, hasta, instaladorId).stream()
                .map(t -> servicio.aEvento(t, todos, "/instalaciones/" + t.getId() + "/editar"))
                .toList();
    }

    // ─── Nueva tarea / asignar una instalación pendiente ──────────────
    @GetMapping("/nueva")
    public String nueva(@RequestParam(required = false) Integer pedidoId,
                        @RequestParam(required = false) Integer instaladorId,
                        @RequestParam(required = false) String fecha,
                        @RequestParam(required = false) String tipo,
                        Model model) {
        if (!model.containsAttribute("form")) {
            FormularioTarea f = new FormularioTarea();
            if (tipo != null && TareaCalendario.TIPOS.contains(tipo)) f.setTipo(tipo);
            if (pedidoId != null) f.setTipo(TareaCalendario.INSTALACION);
            f.setPedidoTiendaId(pedidoId);
            f.setInstaladorId(instaladorId);
            f.setDuracionMinutos(TareaCalendario.INSTALACION.equals(f.getTipo()) ? 120 : 60);

            LocalDateTime fl = CalendarioServicio.parsearFechaFormulario(fecha);
            if (fl == null) fl = LocalDate.now(TareaCalendario.ZONA_COLOMBIA).plusDays(1).atTime(8, 0);
            f.setFechaProgramada(fl.format(FMT_INPUT));
            model.addAttribute("form", f);
        }
        cargarListas(model, null);
        return "instalaciones/formulario";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("form") FormularioTarea form, RedirectAttributes redirectAttributes) {
        try {
            TareaCalendario t = servicio.guardarComoAdmin(null, form, usuarioActual());
            redirectAttributes.addFlashAttribute("mensaje", t.getTipoEtiqueta() + " asignada a "
                    + t.getNombresInstaladores() + ": " + t.getFechaLarga().toLowerCase() + ", " + t.getHoraRango() + ".");
            return "redirect:/instalaciones";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/instalaciones/nueva";
        }
    }

    // ─── Editar / eliminar ────────────────────────────────────────────
    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") int id, Model model, RedirectAttributes redirectAttributes) {
        TareaCalendario t = servicio.buscar(id).orElse(null);
        if (t == null) {
            redirectAttributes.addFlashAttribute("error", "Esa tarea ya no existe.");
            return "redirect:/instalaciones";
        }
        // Las tareas personales del instalador el jefe solo las puede VER, no modificar.
        if (!t.isAsignadaPorAdmin()) {
            model.addAttribute("tarea", t);
            return "instalaciones/detalle";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", FormularioTarea.desde(t));
        }
        cargarListas(model, t);
        return "instalaciones/formulario";
    }

    @PostMapping("/{id}/editar")
    public String guardarEdicion(@PathVariable("id") int id,
                                 @ModelAttribute("form") FormularioTarea form,
                                 RedirectAttributes redirectAttributes) {
        try {
            servicio.guardarComoAdmin(id, form, usuarioActual());
            redirectAttributes.addFlashAttribute("mensaje", "Tarea actualizada.");
            return "redirect:/instalaciones";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/instalaciones/" + id + "/editar";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            servicio.eliminarComoAdmin(id);
            redirectAttributes.addFlashAttribute("mensaje", "Tarea eliminada.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/instalaciones";
    }

    // ─── Helpers ──────────────────────────────────────────────────────
    private void cargarListas(Model model, TareaCalendario tarea) {
        List<PedidoTienda> pedidos = new ArrayList<>(servicio.instalacionesPendientes());
        // Al editar, el pedido ya ligado no sale como pendiente: se agrega para que siga seleccionado.
        if (tarea != null && tarea.getPedidoTienda() != null
                && pedidos.stream().noneMatch(p -> p.getId() == tarea.getPedidoTienda().getId())) {
            pedidos.add(0, tarea.getPedidoTienda());
        }

        List<Usuario> instaladores = new ArrayList<>(servicio.listarInstaladoresActivos());
        // Si algún instalador de esta tarea fue desactivado, igual debe aparecer en la lista.
        if (tarea != null) {
            for (Usuario u : tarea.getInstaladores()) {
                if (instaladores.stream().noneMatch(x -> x.getId() == u.getId())) instaladores.add(u);
            }
        }

        model.addAttribute("tarea", tarea);
        model.addAttribute("accionFormulario",
                tarea == null ? "/instalaciones/guardar" : "/instalaciones/" + tarea.getId() + "/editar");
        model.addAttribute("pedidosDisponibles", pedidos);
        model.addAttribute("instaladores", instaladores);
        model.addAttribute("tipos", TareaCalendario.TIPOS);
        model.addAttribute("estados", TareaCalendario.ESTADOS);
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "desconocido";
    }
}