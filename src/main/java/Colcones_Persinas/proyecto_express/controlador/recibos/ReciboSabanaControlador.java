package Colcones_Persinas.proyecto_express.controlador.recibos;

import Colcones_Persinas.proyecto_express.modelo.recibos.ReciboSabana;
import Colcones_Persinas.proyecto_express.modelo.recibos.ReciboSabanaItem;
import Colcones_Persinas.proyecto_express.repository.recibos.ReciboSabanaRepository;
import Colcones_Persinas.proyecto_express.servicio.recibos.ReciboSabanaPdfServicio;

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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recibos de caja de la sede Sabana (La Calera). Rutas "/recibos/sabana/**".
 *
 * Es un módulo aparte de los recibos de Almacén y Fábrica: tabla propia, numeración
 * propia (0001, 0002…) y encabezado propio (Giovanny Andrés Romero Fagua · Fábrica: Calle 15 #14-20,
 * Sede Granada y Sede La Calera: Calle 9 #7A-53 · Celular 3041354963 - 3115580796).
 *
 * SOLO lo pueden usar el administrador Fabián (usuario "Fabian", rol ADMIN) y Giovanny
 * (usuario "giovanny", rol SABANA). Los demás administradores y usuarios no lo ven en el
 * portal y, si escriben la dirección a mano, se les devuelve al portal.
 */
@Controller
@RequestMapping("/recibos/sabana")
@PreAuthorize("hasAnyRole('ADMIN','SABANA')")
public class ReciboSabanaControlador {

    /** Usuarios que pueden entrar a los recibos de Sabana (en minúscula): Fabián y Giovanny. */
    public static final Set<String> USUARIOS_PERMITIDOS = Set.of("fabian", "giovanny");

    private static final int TAMANO_PAGINA = 10;
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");
    private static final String SIN_ACCESO = "redirect:/portal";

    @Autowired
    private ReciboSabanaRepository reciboRepository;

    @Autowired
    private ReciboSabanaPdfServicio pdfServicio;

    // ═══════════════════════════════════════════════════════════════
    // LISTADO, NUEVO Y EDITAR
    // ═══════════════════════════════════════════════════════════════

    @GetMapping
    public String listar(@RequestParam(required = false) String cliente,
                         @RequestParam(required = false) String desde,
                         @RequestParam(required = false) String hasta,
                         @RequestParam(required = false, defaultValue = "0") int pagina,
                         Model model) {
        if (!puedeEntrar()) return SIN_ACCESO;
        cargarListado(cliente, desde, hasta, pagina, model);
        return "recibos/sabana/listado";
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        if (!puedeEntrar()) return SIN_ACCESO;
        return "recibos/sabana/nuevo";
    }

    @PostMapping("/guardar")
    public String guardar(@RequestParam String cliente,
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
                          RedirectAttributes ra) {
        if (!puedeEntrar()) return SIN_ACCESO;
        String token = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        if (cliente == null || cliente.isBlank()) {
            ra.addFlashAttribute("error", "Debes indicar el nombre del cliente.");
            return conToken("redirect:/recibos/sabana/nuevo", token);
        }

        ReciboSabana recibo = new ReciboSabana();
        recibo.setCreadoPor(nombreUsuarioActual());
        recibo.setFecha(LocalDateTime.now(ZONA_COLOMBIA));
        if (!aplicarDatos(recibo, cliente, direccion, cedula, telefono, abono, descuento,
                nombresProducto, precios, cantidades, ra)) {
            return conToken("redirect:/recibos/sabana/nuevo", token);
        }
        recibo.setNumero(reciboRepository.maxNumero() + 1);
        reciboRepository.save(recibo);

        ra.addFlashAttribute("mensaje", "Recibo de Sabana generado correctamente.");
        return conToken("redirect:/recibos/sabana/imprimir/" + recibo.getId(), token);
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable int id, Model model, RedirectAttributes ra) {
        if (!puedeEntrar()) return SIN_ACCESO;
        Optional<ReciboSabana> recibo = reciboRepository.findById(id);
        if (recibo.isEmpty()) {
            ra.addFlashAttribute("error", "Ese recibo ya no existe.");
            return "redirect:/recibos/sabana";
        }
        model.addAttribute("recibo", recibo.get());
        return "recibos/sabana/editar";
    }

    @PostMapping("/editar/{id}")
    public String guardarEdicion(@PathVariable int id,
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
                                 RedirectAttributes ra) {
        if (!puedeEntrar()) return SIN_ACCESO;
        String token = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        Optional<ReciboSabana> encontrado = reciboRepository.findById(id);
        if (encontrado.isEmpty()) {
            ra.addFlashAttribute("error", "Ese recibo ya no existe.");
            return conToken("redirect:/recibos/sabana", token);
        }
        if (cliente == null || cliente.isBlank()) {
            ra.addFlashAttribute("error", "Debes indicar el nombre del cliente.");
            return conToken("redirect:/recibos/sabana/editar/" + id, token);
        }

        ReciboSabana recibo = encontrado.get();
        recibo.getItems().clear();   // las líneas viejas se borran solas (orphanRemoval)
        if (!aplicarDatos(recibo, cliente, direccion, cedula, telefono, abono, descuento,
                nombresProducto, precios, cantidades, ra)) {
            return conToken("redirect:/recibos/sabana/editar/" + id, token);
        }
        reciboRepository.save(recibo);

        ra.addFlashAttribute("mensaje", "Recibo actualizado correctamente.");
        return conToken("redirect:/recibos/sabana/imprimir/" + id, token);
    }

    // ═══════════════════════════════════════════════════════════════
    // IMPRIMIR / PDF / FIRMAR / ELIMINAR
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/imprimir/{id}")
    public String imprimir(@PathVariable int id, Model model, RedirectAttributes ra) {
        if (!puedeEntrar()) return SIN_ACCESO;
        Optional<ReciboSabana> recibo = reciboRepository.findById(id);
        if (recibo.isEmpty()) {
            ra.addFlashAttribute("error", "Ese recibo ya no existe.");
            return "redirect:/recibos/sabana";
        }
        model.addAttribute("recibo", recibo.get());
        model.addAttribute("nombreResponsable", ReciboSabanaPdfServicio.NOMBRE_RESPONSABLE);
        model.addAttribute("sedes", ReciboSabanaPdfServicio.SEDES);
        model.addAttribute("celular", ReciboSabanaPdfServicio.CELULAR);
        return "recibos/sabana/imprimir";
    }

    /** El PDF se abre "inline" para poder compartirlo desde el celular. */
    @GetMapping("/pdf/{id}")
    public ResponseEntity<byte[]> pdf(@PathVariable int id) {
        if (!puedeEntrar()) return ResponseEntity.status(403).build();
        Optional<ReciboSabana> recibo = reciboRepository.findById(id);
        if (recibo.isEmpty()) return ResponseEntity.notFound().build();
        try {
            byte[] pdf = pdfServicio.generarPdf(recibo.get());
            String nombreArchivo = "recibo_sabana_" + recibo.get().getNumeroFormateado() + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreArchivo + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdf.length)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/firmar/{id}")
    public String firmar(@PathVariable int id,
                         @RequestParam String firmaBase64,
                         @RequestParam(value = "token", required = false) String tokenParam,
                         @CookieValue(value = "authToken", required = false) String tokenCookie,
                         RedirectAttributes ra) {
        if (!puedeEntrar()) return SIN_ACCESO;
        String token = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenCookie;

        Optional<ReciboSabana> encontrado = reciboRepository.findById(id);
        if (encontrado.isEmpty()) {
            ra.addFlashAttribute("error", "Ese recibo ya no existe.");
            return conToken("redirect:/recibos/sabana", token);
        }
        if (firmaBase64 == null || firmaBase64.isBlank()) {
            ra.addFlashAttribute("error", "No se recibió ninguna firma. Intenta de nuevo.");
            return conToken("redirect:/recibos/sabana/imprimir/" + id, token);
        }
        ReciboSabana recibo = encontrado.get();
        recibo.setFirma(firmaBase64);
        recibo.setFirmaFecha(LocalDateTime.now(ZONA_COLOMBIA));
        reciboRepository.save(recibo);

        ra.addFlashAttribute("mensaje", "Firma guardada correctamente.");
        return conToken("redirect:/recibos/sabana/imprimir/" + id, token);
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable int id, RedirectAttributes ra) {
        if (!puedeEntrar()) return SIN_ACCESO;
        try {
            reciboRepository.deleteById(id);
            ra.addFlashAttribute("mensaje", "Recibo eliminado.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo eliminar el recibo: " + e.getMessage());
        }
        return "redirect:/recibos/sabana";
    }

    // ═══════════════════════════════════════════════════════════════
    // AYUDAS
    // ═══════════════════════════════════════════════════════════════

    /** true = el usuario conectado es Fabián o Giovanny. */
    public static boolean puedeEntrar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return false;
        return USUARIOS_PERMITIDOS.contains(auth.getName().trim().toLowerCase(Locale.ROOT));
    }

    private void cargarListado(String cliente, String desde, String hasta, int pagina, Model model) {
        LocalDate fDesde = (desde != null && !desde.isBlank()) ? LocalDate.parse(desde) : null;
        LocalDate fHasta = (hasta != null && !hasta.isBlank()) ? LocalDate.parse(hasta) : null;
        String buscado = cliente != null ? cliente.trim().toLowerCase(Locale.ROOT) : "";

        List<ReciboSabana> filtrados = reciboRepository.findAllByOrderByIdDesc().stream()
                .filter(r -> buscado.isEmpty()
                        || (r.getCliente() != null && r.getCliente().toLowerCase(Locale.ROOT).contains(buscado)))
                .filter(r -> fDesde == null || (r.getFecha() != null && !r.getFecha().toLocalDate().isBefore(fDesde)))
                .filter(r -> fHasta == null || (r.getFecha() != null && !r.getFecha().toLocalDate().isAfter(fHasta)))
                .collect(Collectors.toList());

        int total = filtrados.size();
        int totalPaginas = Math.max(1, (int) Math.ceil((double) total / TAMANO_PAGINA));
        int paginaActual = Math.max(0, Math.min(pagina, totalPaginas - 1));
        int desdeIdx = paginaActual * TAMANO_PAGINA;
        int hastaIdx = Math.min(desdeIdx + TAMANO_PAGINA, total);
        List<ReciboSabana> deLaPagina = desdeIdx < hastaIdx ? filtrados.subList(desdeIdx, hastaIdx) : new ArrayList<>();

        model.addAttribute("recibos", deLaPagina);
        model.addAttribute("cliente", cliente != null ? cliente : "");
        model.addAttribute("desde", desde != null ? desde : "");
        model.addAttribute("hasta", hasta != null ? hasta : "");
        model.addAttribute("paginaActual", paginaActual);
        model.addAttribute("totalPaginas", totalPaginas);
        model.addAttribute("totalRecibosFiltrados", total);
    }

    /**
     * Llena cliente, productos y totales del recibo (al crear y al editar).
     * Devuelve false si algo no es válido (y deja el mensaje de error).
     */
    private boolean aplicarDatos(ReciboSabana recibo, String cliente, String direccion, String cedula,
                                 String telefono, BigDecimal abono, BigDecimal descuento,
                                 List<String> nombresProducto, List<BigDecimal> precios,
                                 List<Integer> cantidades, RedirectAttributes ra) {
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
            if (precio.signum() <= 0 || cantidad <= 0) continue;

            ReciboSabanaItem item = new ReciboSabanaItem();
            item.setNombre(nombre.trim());
            item.setPrecio(precio);
            item.setCantidad(cantidad);
            recibo.agregarItem(item);
            total = total.add(precio.multiply(BigDecimal.valueOf(cantidad)));
        }
        if (recibo.getItems().isEmpty()) {
            ra.addFlashAttribute("error", "Ningún producto válido fue agregado (verifica precio y cantidad).");
            return false;
        }
        recibo.setTotal(total);

        // Descuento: ni negativo ni mayor al total
        BigDecimal desc = (descuento != null && descuento.signum() >= 0) ? descuento : BigDecimal.ZERO;
        if (desc.compareTo(total) > 0) desc = total;
        recibo.setDescuento(desc);

        BigDecimal totalConDescuento = total.subtract(desc);
        BigDecimal ab = (abono != null && abono.signum() >= 0) ? abono : BigDecimal.ZERO;
        if (ab.compareTo(totalConDescuento) > 0) ab = totalConDescuento;
        recibo.setAbono(ab);
        recibo.setSaldo(totalConDescuento.subtract(ab));
        return true;
    }

    private String conToken(String redirectUrl, String token) {
        if (token == null || token.isBlank()) return redirectUrl;
        return redirectUrl + (redirectUrl.contains("?") ? "&" : "?") + "token=" + token;
    }

    private String nombreUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "desconocido";
    }
}