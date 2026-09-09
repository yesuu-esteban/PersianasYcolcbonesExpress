package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.ReciboCaja;
import Colcones_Persinas.proyecto_express.modelo.ReciboCajaItem;
import Colcones_Persinas.proyecto_express.repository.ReciboCajaRepository;
import Colcones_Persinas.proyecto_express.servicio.ReciboPdfServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/recibos")
public class ReciboCajaControlador {

    @Autowired
    private ReciboCajaRepository reciboCajaRepository;

    @Autowired
    private ReciboPdfServicio reciboPdfServicio;

    // ═══════════════════════════════════════════════════════════════
    // RECIBOS DE TIENDA
    // ═══════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/tienda")
    public String listarTienda(
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            Model model) {
        cargarListado("TIENDA", cliente, desde, hasta, model);
        return "recibo/tienda/listado";
    }

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/tienda/nuevo")
    public String nuevoTienda() {
        return "recibo/tienda/nuevo";
    }

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @PostMapping("/tienda/guardar")
    public String guardarTienda(
            @RequestParam String cliente,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String cedula,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false, defaultValue = "0") BigDecimal abono,
            @RequestParam List<String> nombresProducto,
            @RequestParam List<BigDecimal> precios,
            @RequestParam List<Integer> cantidades,
            @RequestParam(value = "token", required = false) String tokenParam,
            @CookieValue(value = "authToken", required = false) String tokenCookie,
            RedirectAttributes redirectAttributes) {

        String tokenEfectivo = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        Integer id = guardarRecibo("TIENDA", cliente, direccion, cedula, telefono, abono,
                nombresProducto, precios, cantidades, redirectAttributes);

        if (id == null) return conToken("redirect:/recibos/tienda/nuevo", tokenEfectivo);

        redirectAttributes.addFlashAttribute("mensaje", "Recibo de tienda generado correctamente.");
        return conToken("redirect:/recibos/imprimir/" + id, tokenEfectivo);
    }

    // ═══════════════════════════════════════════════════════════════
    // RECIBOS DE FÁBRICA
    // ═══════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('FABRICA','ADMIN')")
    @GetMapping("/fabrica")
    public String listarFabrica(
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            Model model) {
        cargarListado("FABRICA", cliente, desde, hasta, model);
        return "recibo/fabrica/listado";
    }

    @PreAuthorize("hasAnyRole('FABRICA','ADMIN')")
    @GetMapping("/fabrica/nuevo")
    public String nuevoFabrica() {
        return "recibo/fabrica/nuevo";
    }

    @PreAuthorize("hasAnyRole('FABRICA','ADMIN')")
    @PostMapping("/fabrica/guardar")
    public String guardarFabrica(
            @RequestParam String cliente,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String cedula,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false, defaultValue = "0") BigDecimal abono,
            @RequestParam List<String> nombresProducto,
            @RequestParam List<BigDecimal> precios,
            @RequestParam List<Integer> cantidades,
            @RequestParam(value = "token", required = false) String tokenParam,
            @CookieValue(value = "authToken", required = false) String tokenCookie,
            RedirectAttributes redirectAttributes) {

        String tokenEfectivo = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        Integer id = guardarRecibo("FABRICA", cliente, direccion, cedula, telefono, abono,
                nombresProducto, precios, cantidades, redirectAttributes);

        if (id == null) return conToken("redirect:/recibos/fabrica/nuevo", tokenEfectivo);

        redirectAttributes.addFlashAttribute("mensaje", "Recibo de fábrica generado correctamente.");
        return conToken("redirect:/recibos/imprimir/" + id, tokenEfectivo);
    }

    // ═══════════════════════════════════════════════════════════════
    // COMUNES (imprimir / PDF / firmar / eliminar)
    // ═══════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','FABRICA','ADMIN')")
    @GetMapping("/imprimir/{id}")
    public String imprimir(@PathVariable("id") int id, Model model) {
        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();

        if (!puedeVerRecibo(recibo)) {
            return "redirect:/recibos/" + (tieneRol("FABRICA") ? "fabrica" : "tienda")
                    + "?error=Sin+acceso+a+ese+recibo";
        }

        model.addAttribute("recibo", recibo);
        return "recibo/imprimir";
    }

    /** Genera el PDF del recibo. Se abre "inline" para que en el celular se pueda usar
     *  directamente el botón nativo de "Compartir" del visor de PDF (WhatsApp, correo, etc.). */
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','FABRICA','ADMIN')")
    @GetMapping("/pdf/{id}")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable("id") int id) {
        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();

        if (!puedeVerRecibo(recibo)) {
            return ResponseEntity.status(403).build();
        }

        try {
            byte[] pdf = reciboPdfServicio.generarPdf(recibo);
            String nombreArchivo = "recibo_" + recibo.getNumeroFormateado() + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreArchivo + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdf.length)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Guarda (o reemplaza) la firma de un recibo, ya sea dibujada a mano o generada
     *  a partir de texto escrito — ambas llegan como una imagen PNG en base64. */
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','FABRICA','ADMIN')")
    @PostMapping("/firmar/{id}")
    public String guardarFirma(
            @PathVariable("id") int id,
            @RequestParam String firmaBase64,
            @RequestParam(value = "token", required = false) String tokenParam,
            @CookieValue(value = "authToken", required = false) String tokenCookie,
            RedirectAttributes redirectAttributes) {

        String tokenEfectivo = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();
        if (!puedeVerRecibo(recibo)) {
            redirectAttributes.addFlashAttribute("error", "No tienes acceso para firmar ese recibo.");
            return conToken("redirect:/recibos/" + (tieneRol("FABRICA") ? "fabrica" : "tienda"), tokenEfectivo);
        }

        if (firmaBase64 == null || firmaBase64.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "No se recibió ninguna firma. Intenta de nuevo.");
            return conToken("redirect:/recibos/imprimir/" + id, tokenEfectivo);
        }

        recibo.setFirma(firmaBase64);
        recibo.setFirmaFecha(LocalDateTime.now());
        reciboCajaRepository.save(recibo);

        redirectAttributes.addFlashAttribute("mensaje", "Firma guardada correctamente.");
        return conToken("redirect:/recibos/imprimir/" + id, tokenEfectivo);
    }

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','FABRICA','ADMIN')")
    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();
        String origen = recibo.getOrigen();
        String volverA = "FABRICA".equals(origen) ? "/recibos/fabrica" : "/recibos/tienda";

        if (!puedeVerRecibo(recibo)) {
            redirectAttributes.addFlashAttribute("error", "No tienes acceso para eliminar ese recibo.");
            return "redirect:" + volverA;
        }

        try {
            reciboCajaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Recibo eliminado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el recibo: " + e.getMessage());
        }
        return "redirect:" + volverA;
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS INTERNOS
    // ═══════════════════════════════════════════════════════════════

    /** Anexa ?token=... a una redirección "redirect:..." para que la petición GET que
     *  el navegador dispara justo después del POST no dependa solo de la cookie. */
    private String conToken(String redirectUrl, String token) {
        if (token == null || token.isBlank()) return redirectUrl;
        String separador = redirectUrl.contains("?") ? "&" : "?";
        return redirectUrl + separador + "token=" + token;
    }

    private void cargarListado(String origenFijo, String cliente, String desde, String hasta, Model model) {
        List<ReciboCaja> todos = reciboCajaRepository.findAllByOrderByIdDesc().stream()
                .filter(r -> origenFijo.equalsIgnoreCase(r.getOrigen()))
                .collect(Collectors.toList());

        LocalDate fDesde = (desde != null && !desde.isBlank()) ? LocalDate.parse(desde) : null;
        LocalDate fHasta = (hasta != null && !hasta.isBlank()) ? LocalDate.parse(hasta) : null;

        List<ReciboCaja> filtrados = todos.stream()
                .filter(r -> cliente == null || cliente.isBlank()
                        || (r.getCliente() != null && r.getCliente().toLowerCase().contains(cliente.trim().toLowerCase())))
                .filter(r -> fDesde == null || (r.getFecha() != null && !r.getFecha().toLocalDate().isBefore(fDesde)))
                .filter(r -> fHasta == null || (r.getFecha() != null && !r.getFecha().toLocalDate().isAfter(fHasta)))
                .collect(Collectors.toList());

        model.addAttribute("recibos", filtrados);
        model.addAttribute("cliente", cliente != null ? cliente : "");
        model.addAttribute("desde", desde != null ? desde : "");
        model.addAttribute("hasta", hasta != null ? hasta : "");
    }

    private Integer guardarRecibo(String origen, String cliente, String direccion, String cedula, String telefono,
                                   BigDecimal abono, List<String> nombresProducto, List<BigDecimal> precios,
                                   List<Integer> cantidades, RedirectAttributes redirectAttributes) {

        if (cliente == null || cliente.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Debes indicar el nombre del cliente.");
            return null;
        }

        ReciboCaja recibo = new ReciboCaja();
        recibo.setCliente(cliente.trim());
        recibo.setDireccion(direccion != null ? direccion.trim() : "");
        recibo.setCedula(cedula != null ? cedula.trim() : "");
        recibo.setTelefono(telefono != null ? telefono.trim() : "");
        recibo.setOrigen(origen);
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
            return null;
        }

        recibo.setTotal(total);
        BigDecimal abonoSeguro = (abono != null && abono.compareTo(BigDecimal.ZERO) >= 0) ? abono : BigDecimal.ZERO;
        if (abonoSeguro.compareTo(total) > 0) abonoSeguro = total;
        recibo.setAbono(abonoSeguro);
        recibo.setSaldo(total.subtract(abonoSeguro));

        reciboCajaRepository.save(recibo);
        return recibo.getId();
    }

    private boolean puedeVerRecibo(ReciboCaja recibo) {
        if (tieneRol("ADMIN")) return true;
        if ("TIENDA".equals(recibo.getOrigen())) return tieneRol("TIENDA") || tieneRol("TIENDA_ADMIN");
        if ("FABRICA".equals(recibo.getOrigen())) return tieneRol("FABRICA");
        return false;
    }

    private String nombreUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "desconocido";
    }

    private boolean tieneRol(String rol) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + rol));
    }
}