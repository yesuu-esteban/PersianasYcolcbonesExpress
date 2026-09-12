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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador de Recibos de Caja. Rutas "/recibos/almacen/**" (antes
 * "/recibos/tienda/**") y "/recibos/fabrica/**". Los valores de "origen"
 * en la base de datos SIGUEN siendo "TIENDA"/"FABRICA" internamente —
 * solo cambiaron las rutas y textos visibles al usuario.
 */
@Controller
@RequestMapping("/recibos")
public class ReciboCajaControlador {

    private static final int TAMANO_PAGINA = 10;
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    @Autowired
    private ReciboCajaRepository reciboCajaRepository;

    @Autowired
    private ReciboPdfServicio reciboPdfServicio;

    // ═══════════════════════════════════════════════════════════════
    // RECIBOS DE ALMACÉN (antes "Tienda")
    // ═══════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/almacen")
    public String listarAlmacen(
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false, defaultValue = "0") int pagina,
            Model model) {
        cargarListado("TIENDA", cliente, desde, hasta, pagina, model);
        return "recibo/almacen/listado";
    }

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/almacen/nuevo")
    public String nuevoAlmacen() {
        return "recibo/almacen/nuevo";
    }

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @PostMapping("/almacen/guardar")
    public String guardarAlmacen(
            @RequestParam String cliente,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String cedula,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false, defaultValue = "0") BigDecimal abono,
            @RequestParam(required = false, defaultValue = "0") BigDecimal descuento,
            @RequestParam List<String> nombresProducto,
            @RequestParam List<BigDecimal> precios,
            @RequestParam List<Integer> cantidades,
            @RequestParam(value = "token", required = false) String tokenParam,
            @CookieValue(value = "authToken", required = false) String tokenCookie,
            RedirectAttributes redirectAttributes) {

        String tokenEfectivo = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        Integer id = guardarRecibo("TIENDA", cliente, direccion, cedula, telefono, abono, descuento,
                nombresProducto, precios, cantidades, redirectAttributes);

        if (id == null) return conToken("redirect:/recibos/almacen/nuevo", tokenEfectivo);

        redirectAttributes.addFlashAttribute("mensaje", "Recibo de almacén generado correctamente.");
        return conToken("redirect:/recibos/imprimir/" + id, tokenEfectivo);
    }

    /** Formulario de edición, precargado con los datos actuales del recibo. */
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/almacen/editar/{id}")
    public String editarAlmacen(@PathVariable("id") int id, Model model, RedirectAttributes redirectAttributes) {
        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();
        if (!puedeVerRecibo(recibo) || !"TIENDA".equals(recibo.getOrigen())) {
            redirectAttributes.addFlashAttribute("error", "No tienes acceso para editar ese recibo.");
            return "redirect:/recibos/almacen";
        }
        model.addAttribute("recibo", recibo);
        return "recibo/almacen/editar";
    }

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @PostMapping("/almacen/editar/{id}")
    public String guardarEdicionAlmacen(
            @PathVariable("id") int id,
            @RequestParam String cliente,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String cedula,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false, defaultValue = "0") BigDecimal abono,
            @RequestParam(required = false, defaultValue = "0") BigDecimal descuento,
            @RequestParam List<String> nombresProducto,
            @RequestParam List<BigDecimal> precios,
            @RequestParam List<Integer> cantidades,
            @RequestParam(value = "token", required = false) String tokenParam,
            @CookieValue(value = "authToken", required = false) String tokenCookie,
            RedirectAttributes redirectAttributes) {

        String tokenEfectivo = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        Integer resultado = actualizarRecibo(id, "TIENDA", cliente, direccion, cedula, telefono, abono, descuento,
                nombresProducto, precios, cantidades, redirectAttributes);

        if (resultado == null) return conToken("redirect:/recibos/almacen/editar/" + id, tokenEfectivo);

        redirectAttributes.addFlashAttribute("mensaje", "Recibo actualizado correctamente.");
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
            @RequestParam(required = false, defaultValue = "0") int pagina,
            Model model) {
        cargarListado("FABRICA", cliente, desde, hasta, pagina, model);
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
            @RequestParam(required = false, defaultValue = "0") BigDecimal descuento,
            @RequestParam List<String> nombresProducto,
            @RequestParam List<BigDecimal> precios,
            @RequestParam List<Integer> cantidades,
            @RequestParam(value = "token", required = false) String tokenParam,
            @CookieValue(value = "authToken", required = false) String tokenCookie,
            RedirectAttributes redirectAttributes) {

        String tokenEfectivo = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        Integer id = guardarRecibo("FABRICA", cliente, direccion, cedula, telefono, abono, descuento,
                nombresProducto, precios, cantidades, redirectAttributes);

        if (id == null) return conToken("redirect:/recibos/fabrica/nuevo", tokenEfectivo);

        redirectAttributes.addFlashAttribute("mensaje", "Recibo de fábrica generado correctamente.");
        return conToken("redirect:/recibos/imprimir/" + id, tokenEfectivo);
    }

    /** Formulario de edición, precargado con los datos actuales del recibo. */
    @PreAuthorize("hasAnyRole('FABRICA','ADMIN')")
    @GetMapping("/fabrica/editar/{id}")
    public String editarFabrica(@PathVariable("id") int id, Model model, RedirectAttributes redirectAttributes) {
        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();
        if (!puedeVerRecibo(recibo) || !"FABRICA".equals(recibo.getOrigen())) {
            redirectAttributes.addFlashAttribute("error", "No tienes acceso para editar ese recibo.");
            return "redirect:/recibos/fabrica";
        }
        model.addAttribute("recibo", recibo);
        return "recibo/fabrica/editar";
    }

    @PreAuthorize("hasAnyRole('FABRICA','ADMIN')")
    @PostMapping("/fabrica/editar/{id}")
    public String guardarEdicionFabrica(
            @PathVariable("id") int id,
            @RequestParam String cliente,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String cedula,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false, defaultValue = "0") BigDecimal abono,
            @RequestParam(required = false, defaultValue = "0") BigDecimal descuento,
            @RequestParam List<String> nombresProducto,
            @RequestParam List<BigDecimal> precios,
            @RequestParam List<Integer> cantidades,
            @RequestParam(value = "token", required = false) String tokenParam,
            @CookieValue(value = "authToken", required = false) String tokenCookie,
            RedirectAttributes redirectAttributes) {

        String tokenEfectivo = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        Integer resultado = actualizarRecibo(id, "FABRICA", cliente, direccion, cedula, telefono, abono, descuento,
                nombresProducto, precios, cantidades, redirectAttributes);

        if (resultado == null) return conToken("redirect:/recibos/fabrica/editar/" + id, tokenEfectivo);

        redirectAttributes.addFlashAttribute("mensaje", "Recibo actualizado correctamente.");
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
            return "redirect:/recibos/" + (tieneRol("FABRICA") ? "fabrica" : "almacen")
                    + "?error=Sin+acceso+a+ese+recibo";
        }

        asignarNumeroSecuencial(recibo);
        model.addAttribute("recibo", recibo);
        return "recibo/imprimir";
    }

    /** Genera el PDF del recibo. Se abre "inline" para que en el celular se pueda usar
     *  el botón nativo de "Compartir" del visor de PDF, y para que el fetch() del botón
     *  de compartir en el frontend pueda descargarlo como blob sin problema. */
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','FABRICA','ADMIN')")
    @GetMapping("/pdf/{id}")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable("id") int id) {
        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();

        if (!puedeVerRecibo(recibo)) {
            return ResponseEntity.status(403).build();
        }

        try {
            asignarNumeroSecuencial(recibo);
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
            return conToken("redirect:/recibos/" + (tieneRol("FABRICA") ? "fabrica" : "almacen"), tokenEfectivo);
        }

        if (firmaBase64 == null || firmaBase64.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "No se recibió ninguna firma. Intenta de nuevo.");
            return conToken("redirect:/recibos/imprimir/" + id, tokenEfectivo);
        }

        recibo.setFirma(firmaBase64);
        recibo.setFirmaFecha(LocalDateTime.now(ZONA_COLOMBIA));
        reciboCajaRepository.save(recibo);

        redirectAttributes.addFlashAttribute("mensaje", "Firma guardada correctamente.");
        return conToken("redirect:/recibos/imprimir/" + id, tokenEfectivo);
    }

    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','FABRICA','ADMIN')")
    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();
        String origen = recibo.getOrigen();
        String volverA = "FABRICA".equals(origen) ? "/recibos/fabrica" : "/recibos/almacen";

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

    private String conToken(String redirectUrl, String token) {
        if (token == null || token.isBlank()) return redirectUrl;
        String separador = redirectUrl.contains("?") ? "&" : "?";
        return redirectUrl + separador + "token=" + token;
    }

    private void asignarNumeroSecuencial(ReciboCaja recibo) {
        long menores = reciboCajaRepository.countByIdLessThan(recibo.getId());
        recibo.setNumeroMostrado((int) menores);
    }

    private void cargarListado(String origenFijo, String cliente, String desde, String hasta, int pagina, Model model) {
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

        int totalFiltrados = filtrados.size();
        int totalPaginas = (int) Math.ceil((double) totalFiltrados / TAMANO_PAGINA);
        if (totalPaginas == 0) totalPaginas = 1;
        int paginaActual = Math.max(0, Math.min(pagina, totalPaginas - 1));
        int desdeIdx = paginaActual * TAMANO_PAGINA;
        int hastaIdx = Math.min(desdeIdx + TAMANO_PAGINA, totalFiltrados);
        List<ReciboCaja> pagina_ = (desdeIdx < hastaIdx) ? filtrados.subList(desdeIdx, hastaIdx) : new ArrayList<>();

        pagina_.forEach(this::asignarNumeroSecuencial);

        model.addAttribute("recibos", pagina_);
        model.addAttribute("cliente", cliente != null ? cliente : "");
        model.addAttribute("desde", desde != null ? desde : "");
        model.addAttribute("hasta", hasta != null ? hasta : "");
        model.addAttribute("paginaActual", paginaActual);
        model.addAttribute("totalPaginas", totalPaginas);
        model.addAttribute("totalRecibosFiltrados", totalFiltrados);
    }

    private Integer guardarRecibo(String origen, String cliente, String direccion, String cedula, String telefono,
                                   BigDecimal abono, BigDecimal descuento,
                                   List<String> nombresProducto, List<BigDecimal> precios,
                                   List<Integer> cantidades, RedirectAttributes redirectAttributes) {

        if (cliente == null || cliente.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Debes indicar el nombre del cliente.");
            return null;
        }

        ReciboCaja recibo = new ReciboCaja();
        recibo.setOrigen(origen);
        recibo.setCreadoPor(nombreUsuarioActual());

        if (!aplicarDatosRecibo(recibo, cliente, direccion, cedula, telefono, abono, descuento,
                nombresProducto, precios, cantidades, redirectAttributes)) {
            return null;
        }

        reciboCajaRepository.save(recibo);
        return recibo.getId();
    }

    /**
     * Actualiza un recibo YA EXISTENTE (edición): conserva su id, número,
     * firma y fecha de creación original; solo cambia los datos del
     * cliente, productos, descuento y abono. Las líneas de productos se
     * reemplazan por completo (gracias a orphanRemoval=true en la entidad,
     * las líneas viejas que ya no estén se borran solas).
     */
    private Integer actualizarRecibo(int id, String origenEsperado, String cliente, String direccion,
                                      String cedula, String telefono, BigDecimal abono, BigDecimal descuento,
                                      List<String> nombresProducto, List<BigDecimal> precios,
                                      List<Integer> cantidades, RedirectAttributes redirectAttributes) {

        ReciboCaja recibo = reciboCajaRepository.findById(id).orElseThrow();

        if (!puedeVerRecibo(recibo) || !origenEsperado.equals(recibo.getOrigen())) {
            redirectAttributes.addFlashAttribute("error", "No tienes acceso para editar ese recibo.");
            return null;
        }

        if (cliente == null || cliente.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Debes indicar el nombre del cliente.");
            return null;
        }

        // Se reemplazan todas las líneas de productos por las nuevas.
        recibo.getItems().clear();

        if (!aplicarDatosRecibo(recibo, cliente, direccion, cedula, telefono, abono, descuento,
                nombresProducto, precios, cantidades, redirectAttributes)) {
            return null;
        }

        reciboCajaRepository.save(recibo);
        return recibo.getId();
    }

    /**
     * Lógica común a "crear" y "editar": llena cliente/productos/totales
     * sobre un ReciboCaja que ya viene con origen definido (nuevo o
     * existente). Devuelve false si algo no es válido (y ya dejó el
     * mensaje de error en redirectAttributes), true si todo quedó bien.
     */
    private boolean aplicarDatosRecibo(ReciboCaja recibo, String cliente, String direccion, String cedula,
                                        String telefono, BigDecimal abono, BigDecimal descuento,
                                        List<String> nombresProducto, List<BigDecimal> precios,
                                        List<Integer> cantidades, RedirectAttributes redirectAttributes) {

        recibo.setCliente(cliente.trim());
        recibo.setDireccion(direccion != null ? direccion.trim() : "");
        recibo.setCedula(cedula != null ? cedula.trim() : "");
        recibo.setTelefono(telefono != null ? telefono.trim() : "");

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
            return false;
        }

        recibo.setTotal(total);

        // ── Descuento: no puede ser negativo ni superar el total bruto ──
        BigDecimal descuentoSeguro = (descuento != null && descuento.compareTo(BigDecimal.ZERO) >= 0)
                ? descuento : BigDecimal.ZERO;
        if (descuentoSeguro.compareTo(total) > 0) descuentoSeguro = total;
        recibo.setDescuento(descuentoSeguro);

        BigDecimal totalConDescuento = total.subtract(descuentoSeguro);

        BigDecimal abonoSeguro = (abono != null && abono.compareTo(BigDecimal.ZERO) >= 0) ? abono : BigDecimal.ZERO;
        if (abonoSeguro.compareTo(totalConDescuento) > 0) abonoSeguro = totalConDescuento;
        recibo.setAbono(abonoSeguro);
        recibo.setSaldo(totalConDescuento.subtract(abonoSeguro));

        return true;
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