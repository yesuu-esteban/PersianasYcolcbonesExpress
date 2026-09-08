package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.ReciboCaja;
import Colcones_Persinas.proyecto_express.modelo.ReciboCajaItem;
import Colcones_Persinas.proyecto_express.repository.ReciboCajaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/recibos")
@PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','FABRICA','ADMIN')")
public class ReciboCajaControlador {

    @Autowired
    private ReciboCajaRepository reciboCajaRepository;

    @GetMapping
    public String listar(
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            Model model) {

        List<ReciboCaja> todos = reciboCajaRepository.findAllByOrderByIdDesc();

        LocalDate fDesde = (desde != null && !desde.isBlank()) ? LocalDate.parse(desde) : null;
        LocalDate fHasta = (hasta != null && !hasta.isBlank()) ? LocalDate.parse(hasta) : null;

        List<ReciboCaja> filtrados = todos.stream()
                .filter(r -> origen == null || origen.isBlank() || origen.equalsIgnoreCase(r.getOrigen()))
                .filter(r -> cliente == null || cliente.isBlank()
                        || (r.getCliente() != null && r.getCliente().toLowerCase().contains(cliente.trim().toLowerCase())))
                .filter(r -> fDesde == null || (r.getFecha() != null && !r.getFecha().toLocalDate().isBefore(fDesde)))
                .filter(r -> fHasta == null || (r.getFecha() != null && !r.getFecha().toLocalDate().isAfter(fHasta)))
                .collect(Collectors.toList());

        model.addAttribute("recibos", filtrados);
        model.addAttribute("origen", origen != null ? origen : "");
        model.addAttribute("cliente", cliente != null ? cliente : "");
        model.addAttribute("desde", desde != null ? desde : "");
        model.addAttribute("hasta", hasta != null ? hasta : "");
        return "recibo/listado";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("origenSugerido", origenSegunRol());
        return "recibo/nuevo";
    }

    @PostMapping("/guardar")
    public String guardar(
            @RequestParam String cliente,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String cedula,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false, defaultValue = "0") BigDecimal abono,
            @RequestParam List<String> nombresProducto,
            @RequestParam List<BigDecimal> precios,
            @RequestParam List<Integer> cantidades,
            RedirectAttributes redirectAttributes) {

        if (cliente == null || cliente.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Debes indicar el nombre del cliente.");
            return "redirect:/recibos/nuevo";
        }

        ReciboCaja recibo = new ReciboCaja();
        recibo.setCliente(cliente.trim());
        recibo.setDireccion(direccion != null ? direccion.trim() : "");
        recibo.setCedula(cedula != null ? cedula.trim() : "");
        recibo.setTelefono(telefono != null ? telefono.trim() : "");
        recibo.setOrigen(origenSegunRol());
        recibo.setCreadoPor(nombreUsuarioActual());

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < nombresProducto.size(); i++) {
            String nombre = nombresProducto.get(i);
            if (nombre == null || nombre.isBlank()) continue;

            BigDecimal precio = (precios != null && i < precios.size() && precios.get(i) != null)
                    ? precios.get(i) : BigDecimal.ZERO;
            int cantidad = (cantidades != null && i < cantidades.size() && cantidades.get(i) != null)
                    ? cantidades.get(i) : 0;

            if (precio.compareTo(BigDecimal.ZERO) <= 0 || cantidad <= 0) continue;

            ReciboCajaItem item = new ReciboCajaItem();
            item.setNombre(nombre.trim());
            item.setPrecio(precio);
            item.setCantidad(cantidad);
            recibo.agregarItem(item);
            total = total.add(precio.multiply(BigDecimal.valueOf(cantidad)));
        }

        if (recibo.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Ningún producto válido fue agregado (verifica precio y cantidad).");
            return "redirect:/recibos/nuevo";
        }

        recibo.setTotal(total);
        BigDecimal abonoSeguro = (abono != null && abono.compareTo(BigDecimal.ZERO) >= 0) ? abono : BigDecimal.ZERO;
        if (abonoSeguro.compareTo(total) > 0) abonoSeguro = total;
        recibo.setAbono(abonoSeguro);
        recibo.setSaldo(total.subtract(abonoSeguro));

        reciboCajaRepository.save(recibo);

        redirectAttributes.addFlashAttribute("mensaje", "Recibo #" + recibo.getNumero() + " generado correctamente.");
        return "redirect:/recibos/imprimir/" + recibo.getId();
    }

    @GetMapping("/imprimir/{id}")
    public String imprimir(@PathVariable("id") int id, Model model) {
        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();
        model.addAttribute("recibo", recibo);
        return "recibo/imprimir";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            reciboCajaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Recibo eliminado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el recibo: " + e.getMessage());
        }
        return "redirect:/recibos";
    }

    // ─── Helpers ──────────────────────────────────────────────────
    private String nombreUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "desconocido";
    }

    private boolean tieneRol(String rol) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + rol));
    }

    private String origenSegunRol() {
        if (tieneRol("FABRICA")) return "FABRICA";
        if (tieneRol("TIENDA") || tieneRol("TIENDA_ADMIN")) return "TIENDA";
        return "ADMIN";
    }
}