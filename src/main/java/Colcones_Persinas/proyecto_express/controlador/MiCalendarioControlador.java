package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.FormularioTarea;
import Colcones_Persinas.proyecto_express.modelo.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.TareaCalendario;
import Colcones_Persinas.proyecto_express.servicio.CalendarioServicio;
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
import java.util.Locale;
import java.util.Map;

/**
 * Agenda personal del INSTALADOR.
 *  - Ve las tareas donde participa (solo, o con un compañero).
 *  - Puede agregar tareas propias: instalación, limpieza, arreglo, cotización u otro.
 *  - Solo puede editar/eliminar lo que él mismo agregó. Lo que puso el jefe
 *    solo lo puede empezar y marcar como terminado.
 */
@Controller
@RequestMapping("/mi-calendario")
@PreAuthorize("hasRole('INSTALADOR')")
public class MiCalendarioControlador {

    private static final DateTimeFormatter FMT_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter FMT_DIA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "CO"));

    private final CalendarioServicio servicio;

    public MiCalendarioControlador(CalendarioServicio servicio) {
        this.servicio = servicio;
    }

    // ─── Agenda ───────────────────────────────────────────────────────
    @GetMapping
    public String verCalendario(Model model) {
        String yo = usuarioActual();
        LocalDate hoy = LocalDate.now(TareaCalendario.ZONA_COLOMBIA);
        String hoyTexto = hoy.format(FMT_DIA);

        model.addAttribute("tareasHoy", servicio.delDiaParaInstalador(yo, hoy));
        model.addAttribute("tareasManana", servicio.delDiaParaInstalador(yo, hoy.plusDays(1)).size());
        model.addAttribute("hoyTexto", Character.toUpperCase(hoyTexto.charAt(0)) + hoyTexto.substring(1));
        model.addAttribute("nombreUsuario", servicio.nombreVisible(yo));
        model.addAttribute("yo", yo);
        return "calendario/mi_calendario";
    }

    @GetMapping("/eventos")
    @ResponseBody
    public List<Map<String, Object>> eventos(@RequestParam String start, @RequestParam String end) {
        LocalDateTime desde = CalendarioServicio.parsearFechaCalendario(start);
        LocalDateTime hasta = CalendarioServicio.parsearFechaCalendario(end);
        if (desde == null || hasta == null) return List.of();

        return servicio.listarParaInstalador(usuarioActual(), desde, hasta).stream()
                .map(t -> servicio.aEvento(t, false, "/mi-calendario/" + t.getId()))
                .toList();
    }

    // ─── Ver una tarea ────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detalle(@PathVariable("id") int id, Model model, RedirectAttributes redirectAttributes) {
        String yo = usuarioActual();
        TareaCalendario t;
        try {
            t = servicio.obtenerParaInstalador(id, yo);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/mi-calendario";
        }
        String companero = t.getInstaladores().stream()
                .filter(u -> !u.getUsername().equalsIgnoreCase(yo))
                .map(TareaCalendario::nombreVisible)
                .findFirst().orElse(null);

        model.addAttribute("tarea", t);
        model.addAttribute("companero", companero);
        model.addAttribute("puedeModificar",
                servicio.puedeModificar(t, yo) && !TareaCalendario.COMPLETADA.equals(t.getEstado()));
        model.addAttribute("whatsapp", numeroWhatsapp(t.getTelefono()));
        model.addAttribute("mensajeWhatsapp", "Hola " + t.getCliente() + ", le escribe "
                + servicio.nombreVisible(yo) + " de Persianas Express. Vamos en camino.");
        return "calendario/mi_detalle";
    }

    // ─── Agregar / editar / eliminar tareas propias ───────────────────
    @GetMapping("/nueva")
    public String nueva(@RequestParam(required = false) String fecha,
                        @RequestParam(required = false) String tipo,
                        @RequestParam(required = false) Integer pedidoId,
                        Model model) {
        if (!model.containsAttribute("form")) {
            FormularioTarea f = new FormularioTarea();
            f.setTipo(tipo != null && TareaCalendario.TIPOS.contains(tipo) ? tipo : TareaCalendario.COTIZACION);
            if (pedidoId != null) {
                f.setTipo(TareaCalendario.INSTALACION);
                f.setPedidoTiendaId(pedidoId);
            }
            f.setDuracionMinutos(TareaCalendario.INSTALACION.equals(f.getTipo()) ? 120 : 60);
            LocalDateTime fl = CalendarioServicio.parsearFechaFormulario(fecha);
            if (fl == null) fl = LocalDate.now(TareaCalendario.ZONA_COLOMBIA).plusDays(1).atTime(8, 0);
            f.setFechaProgramada(fl.format(FMT_INPUT));
            model.addAttribute("form", f);
        }
        model.addAttribute("accionFormulario", "/mi-calendario/guardar");
        model.addAttribute("tipos", TareaCalendario.TIPOS);
        model.addAttribute("pedidosDisponibles", pedidosParaFormulario(null));
        return "calendario/mi_formulario";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("form") FormularioTarea form, RedirectAttributes redirectAttributes) {
        try {
            TareaCalendario t = servicio.guardarComoInstalador(null, form, usuarioActual());
            redirectAttributes.addFlashAttribute("mensaje", t.getTipoEtiqueta() + " agregada a tu agenda: "
                    + t.getFechaLarga().toLowerCase() + ", " + t.getHoraRango() + ".");
            return "redirect:/mi-calendario";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/mi-calendario/nueva";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") int id, Model model, RedirectAttributes redirectAttributes) {
        TareaCalendario t;
        try {
            t = servicio.obtenerPropiaModificable(id, usuarioActual());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/mi-calendario/" + id;
        }
        if (!model.containsAttribute("form")) model.addAttribute("form", FormularioTarea.desde(t));
        model.addAttribute("tarea", t);
        model.addAttribute("accionFormulario", "/mi-calendario/" + id + "/editar");
        model.addAttribute("tipos", TareaCalendario.TIPOS);
        model.addAttribute("pedidosDisponibles", pedidosParaFormulario(t));
        return "calendario/mi_formulario";
    }

    @PostMapping("/{id}/editar")
    public String guardarEdicion(@PathVariable("id") int id,
                                 @ModelAttribute("form") FormularioTarea form,
                                 RedirectAttributes redirectAttributes) {
        try {
            servicio.guardarComoInstalador(id, form, usuarioActual());
            redirectAttributes.addFlashAttribute("mensaje", "Cambios guardados.");
            return "redirect:/mi-calendario/" + id;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/mi-calendario/" + id + "/editar";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            servicio.eliminarComoInstalador(id, usuarioActual());
            redirectAttributes.addFlashAttribute("mensaje", "Tarea eliminada de tu agenda.");
            return "redirect:/mi-calendario";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/mi-calendario/" + id;
        }
    }

    // ─── Avance del trabajo (sirve para tareas propias y del jefe) ────
    @PostMapping("/{id}/iniciar")
    public String iniciar(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            servicio.iniciar(id, usuarioActual());
            redirectAttributes.addFlashAttribute("mensaje", "Marcada como en curso.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/mi-calendario/" + id;
    }

    @PostMapping("/{id}/completar")
    public String completar(@PathVariable("id") int id,
                            @RequestParam(required = false) String observaciones,
                            RedirectAttributes redirectAttributes) {
        try {
            servicio.completar(id, usuarioActual(), observaciones);
            redirectAttributes.addFlashAttribute("mensaje", "Tarea registrada como terminada.");
            return "redirect:/mi-calendario";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/mi-calendario/" + id;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /** Pedidos "En Bodega" sin instalación; al editar, incluye también el pedido ya ligado. */
    private List<PedidoTienda> pedidosParaFormulario(TareaCalendario tarea) {
        List<PedidoTienda> pedidos = new ArrayList<>(servicio.instalacionesPendientes());
        if (tarea != null && tarea.getPedidoTienda() != null
                && pedidos.stream().noneMatch(p -> p.getId() == tarea.getPedidoTienda().getId())) {
            pedidos.add(0, tarea.getPedidoTienda());
        }
        return pedidos;
    }

    /** Deja solo dígitos y agrega el 57 si es un celular colombiano de 10 dígitos. */
    private String numeroWhatsapp(String telefono) {
        if (telefono == null) return null;
        String d = telefono.replaceAll("\\D", "");
        if (d.length() == 10) return "57" + d;
        if (d.length() == 12 && d.startsWith("57")) return d;
        return null;
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}