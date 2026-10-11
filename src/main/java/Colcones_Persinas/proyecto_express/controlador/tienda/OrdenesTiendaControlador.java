package Colcones_Persinas.proyecto_express.controlador.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.ItemOrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import Colcones_Persinas.proyecto_express.servicio.tienda.TiendaServicio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Pedidos de la tienda virtual (TIENDA_ADMIN y ADMIN). La tienda es un módulo aparte:
 * sus pedidos se ven y se manejan solo aquí, no en Almacén.
 *
 *  - Lista de pedidos por páginas, con filtros por estado y buscador.
 *  - Cambiar en qué va cada pedido pagado: Nuevo → En fabricación → Listo → Despachado → Entregado,
 *    o Cancelado. El cliente ve ese avance en la página de rastreo.
 *  - Corregir los datos del cliente, eliminar compras (pruebas, intentos sin pagar) y
 *    avisarle al cliente por WhatsApp con su número de pedido y el enlace de rastreo.
 *  - Productos de Dropi: el filtro "Por pedir en Dropi" y anotar el número del pedido hecho allá.
 *  - /tienda-admin/api/pedidos-nuevos: cuántos pedidos pagados siguen en Nuevo (aviso del portal y del menú).
 */
@Controller
@RequestMapping("/tienda-admin")
@PreAuthorize("hasAnyRole('TIENDA_ADMIN','ADMIN')")
public class OrdenesTiendaControlador {

    /** Cuántos pedidos se muestran por página. */
    private static final int POR_PAGINA = 10;

    private static final List<String> PAGO_APROBADO = List.of(OrdenTienda.APROBADA);
    private static final List<String> PAGO_NO_COMPLETADO = List.of(OrdenTienda.RECHAZADA, OrdenTienda.ANULADA, OrdenTienda.ERROR);
    private static final List<String> PAGO_TODOS = List.of(OrdenTienda.PENDIENTE, OrdenTienda.APROBADA,
            OrdenTienda.RECHAZADA, OrdenTienda.ANULADA, OrdenTienda.ERROR);
    private static final List<String> TODOS_LOS_ESTADOS_PEDIDO = new ArrayList<>(OrdenTienda.ESTADOS_PEDIDO.keySet());

    /** Los filtros de la pantalla, en el orden en que se muestran. */
    static final Map<String, String> FILTROS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("activos", "Por atender");
        m.put("dropi", "Por pedir en Dropi");
        m.put("nuevos", "Nuevos");
        m.put("fabricacion", "En fabricación");
        m.put("listos", "Listos");
        m.put("despachados", "Despachados");
        m.put("entregados", "Entregados");
        m.put("cancelados", "Cancelados");
        m.put("pendientes", "Esperando pago");
        m.put("fallidas", "Pago no completado");
        m.put("todas", "Todas");
        FILTROS = Collections.unmodifiableMap(m);
    }

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OrdenTiendaRepository ordenRepository;
    private final TiendaServicio tiendaServicio;

    /**
     * Dominio propio de la tienda (variable APP_DOMINIO_TIENDA), si ya se configuró.
     * Se usa para armar el enlace de rastreo que se le envía al cliente.
     */
    @Value("${app.dominio-tienda:}")
    private String dominioTienda;

    public OrdenesTiendaControlador(OrdenTiendaRepository ordenRepository, TiendaServicio tiendaServicio) {
        this.ordenRepository = ordenRepository;
        this.tiendaServicio = tiendaServicio;
    }

    // ═══════════════════════════════════════════════════════════════
    // LISTA DE PEDIDOS
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/ordenes")
    public String ordenes(@RequestParam(name = "ver", required = false, defaultValue = "activos") String ver,
                          @RequestParam(name = "q", required = false, defaultValue = "") String q,
                          @RequestParam(name = "pagina", required = false, defaultValue = "0") int pagina,
                          Model model) {
        String filtro = filtroValido(ver);
        String busqueda = q.trim();
        if (busqueda.length() > 60) busqueda = busqueda.substring(0, 60);

        Page<OrdenTienda> resultado = buscar(filtro, busqueda, Math.max(pagina, 0));
        // Si se eliminó el último pedido de una página, se muestra la última página que quede
        if (resultado.getTotalPages() > 0 && resultado.getNumber() >= resultado.getTotalPages()) {
            resultado = buscar(filtro, busqueda, resultado.getTotalPages() - 1);
        }

        model.addAttribute("ordenes", resultado.getContent());
        model.addAttribute("ver", filtro);
        model.addAttribute("q", busqueda);
        model.addAttribute("filtros", FILTROS);
        model.addAttribute("conteos", conteos());
        model.addAttribute("estadosPedido", OrdenTienda.ESTADOS_PEDIDO);
        model.addAttribute("siguienteEstado", siguientesEstados(resultado.getContent()));
        model.addAttribute("paginaActual", resultado.getNumber());
        model.addAttribute("totalPaginas", Math.max(resultado.getTotalPages(), 1));
        model.addAttribute("totalFiltradas", resultado.getTotalElements());

        // Enlace de WhatsApp de cada pedido pagado, con el mensaje según su estado
        String urlRastreo = urlBaseTienda() + "/tienda/rastrear/";
        Map<String, String> enlacesWhatsapp = new HashMap<>();
        for (OrdenTienda o : resultado.getContent()) {
            String enlace = enlaceWhatsappRastreo(o, urlRastreo);
            if (enlace != null) enlacesWhatsapp.put(o.getReferencia(), enlace);
        }
        model.addAttribute("enlacesWhatsapp", enlacesWhatsapp);
        return "tienda-admin/ordenes";
    }

    private Page<OrdenTienda> buscar(String filtro, String busqueda, int pagina) {
        String q = busqueda.isEmpty() ? "%" : "%" + busqueda.toLowerCase(Locale.ROOT) + "%";
        PageRequest pag = PageRequest.of(pagina, POR_PAGINA);
        switch (filtro) {
            case "dropi":       return ordenRepository.porPedirEnDropi(q, pag);
            case "nuevos":      return ordenRepository.buscar(PAGO_APROBADO, List.of(OrdenTienda.NUEVO), q, pag);
            case "fabricacion": return ordenRepository.buscar(PAGO_APROBADO, List.of(OrdenTienda.EN_FABRICACION), q, pag);
            case "listos":      return ordenRepository.buscar(PAGO_APROBADO, List.of(OrdenTienda.LISTO), q, pag);
            case "despachados": return ordenRepository.buscar(PAGO_APROBADO, List.of(OrdenTienda.DESPACHADO), q, pag);
            case "entregados":  return ordenRepository.buscar(PAGO_APROBADO, List.of(OrdenTienda.ENTREGADO), q, pag);
            case "cancelados":  return ordenRepository.buscar(PAGO_APROBADO, List.of(OrdenTienda.CANCELADO), q, pag);
            case "pendientes":  return ordenRepository.buscar(List.of(OrdenTienda.PENDIENTE), TODOS_LOS_ESTADOS_PEDIDO, q, pag);
            case "fallidas":    return ordenRepository.buscar(PAGO_NO_COMPLETADO, TODOS_LOS_ESTADOS_PEDIDO, q, pag);
            case "todas":       return ordenRepository.buscar(PAGO_TODOS, TODOS_LOS_ESTADOS_PEDIDO, q, pag);
            default:            return ordenRepository.buscar(PAGO_APROBADO, OrdenTienda.ESTADOS_ACTIVOS, q, pag);
        }
    }

    /** Cuántos pedidos hay en cada filtro (para los numeritos de los botones). */
    private Map<String, Long> conteos() {
        Map<String, Long> porEstado = new HashMap<>();
        for (Object[] fila : ordenRepository.contarPorEstadoPedido()) {
            String estado = fila[0] == null || fila[0].toString().isBlank() ? OrdenTienda.NUEVO : fila[0].toString();
            porEstado.merge(estado, ((Number) fila[1]).longValue(), Long::sum);
        }
        Map<String, Long> c = new HashMap<>();
        c.put("nuevos", porEstado.getOrDefault(OrdenTienda.NUEVO, 0L));
        c.put("fabricacion", porEstado.getOrDefault(OrdenTienda.EN_FABRICACION, 0L));
        c.put("listos", porEstado.getOrDefault(OrdenTienda.LISTO, 0L));
        c.put("despachados", porEstado.getOrDefault(OrdenTienda.DESPACHADO, 0L));
        c.put("entregados", porEstado.getOrDefault(OrdenTienda.ENTREGADO, 0L));
        c.put("cancelados", porEstado.getOrDefault(OrdenTienda.CANCELADO, 0L));
        c.put("activos", c.get("nuevos") + c.get("fabricacion") + c.get("listos") + c.get("despachados"));
        c.put("dropi", ordenRepository.contarPorPedirEnDropi());
        c.put("pendientes", ordenRepository.countByEstado(OrdenTienda.PENDIENTE));
        c.put("fallidas", ordenRepository.countByEstadoIn(PAGO_NO_COMPLETADO));
        c.put("todas", ordenRepository.count());
        return c;
    }

    /** Para el botón de avance rápido: el estado que sigue después del actual (referencia → estado). */
    private static Map<String, String> siguientesEstados(List<OrdenTienda> ordenes) {
        Map<String, String> m = new HashMap<>();
        for (OrdenTienda o : ordenes) {
            String siguiente = siguienteEstado(o.getEstadoPedidoActual());
            if (o.isPagada() && siguiente != null) m.put(o.getReferencia(), siguiente);
        }
        return m;
    }

    static String siguienteEstado(String actual) {
        int i = OrdenTienda.ESTADOS_ACTIVOS.indexOf(actual);
        if (i < 0) return null;   // Entregado o Cancelado: no hay siguiente
        return i + 1 < OrdenTienda.ESTADOS_ACTIVOS.size() ? OrdenTienda.ESTADOS_ACTIVOS.get(i + 1) : OrdenTienda.ENTREGADO;
    }

    /** Solo se aceptan los filtros conocidos. "pagadas" (el nombre de antes) muestra los pedidos por atender. */
    static String filtroValido(String ver) {
        return ver != null && FILTROS.containsKey(ver) ? ver : "activos";
    }

    // ═══════════════════════════════════════════════════════════════
    // CAMBIAR EL ESTADO DEL PEDIDO
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/orden/{id}/estado")
    public String cambiarEstado(@PathVariable int id,
                                @RequestParam(name = "estado", required = false) String estado,
                                @RequestParam(name = "guia", required = false) String guia,
                                @RequestParam(name = "ver", required = false, defaultValue = "activos") String ver,
                                @RequestParam(name = "q", required = false, defaultValue = "") String q,
                                @RequestParam(name = "pagina", required = false, defaultValue = "0") int pagina,
                                RedirectAttributes ra) {
        try {
            OrdenTienda o = tiendaServicio.cambiarEstadoPedido(id, estado, guia);
            String mensaje = "Pedido " + o.getReferencia() + " de " + o.getPrimerNombre() + ": ahora está en \""
                    + o.getEstadoPedidoEtiqueta() + "\".";
            if (o.isCancelada()) {
                mensaje += " Si le devolviste la plata al cliente, anótalo como gasto \"Devoluciones\" en Contabilidad.";
            }
            ra.addFlashAttribute("mensaje", mensaje);
            ra.addFlashAttribute("resaltar", o.getReferencia());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return volverALista(ra, ver, q, pagina);
    }

    // ═══════════════════════════════════════════════════════════════
    // PRODUCTOS DE DROPI: ANOTAR QUE YA SE PIDIERON ALLÁ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Guarda el número de pedido (o la guía) que dio Dropi para un producto de la compra.
     * Con eso el producto deja de salir en "Por pedir en Dropi". Vacío = todavía no se ha pedido.
     */
    @PostMapping("/orden/{id}/item/{itemId}/proveedor")
    public String anotarPedidoDropi(@PathVariable int id, @PathVariable int itemId,
                                    @RequestParam(name = "numero", required = false, defaultValue = "") String numero,
                                    @RequestParam(name = "ver", required = false, defaultValue = "activos") String ver,
                                    @RequestParam(name = "q", required = false, defaultValue = "") String q,
                                    @RequestParam(name = "pagina", required = false, defaultValue = "0") int pagina,
                                    RedirectAttributes ra) {
        try {
            ItemOrdenTienda it = tiendaServicio.anotarPedidoProveedor(id, itemId, numero);
            OrdenTienda o = it.getOrden();
            String referencia = o != null ? o.getReferencia() : "";
            ra.addFlashAttribute("mensaje", it.getPedidoProveedor() == null
                    ? "\"" + it.getProductoNombre() + "\" del pedido " + referencia + " volvió a quedar por pedir en Dropi."
                    : "Listo: \"" + it.getProductoNombre() + "\" del pedido " + referencia + " quedó pedido en Dropi ("
                      + it.getPedidoProveedor() + ").");
            if (!referencia.isEmpty()) ra.addFlashAttribute("resaltar", referencia);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return volverALista(ra, ver, q, pagina);
    }

    // ═══════════════════════════════════════════════════════════════
    // EDITAR DATOS DEL CLIENTE / ELIMINAR
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/orden/{id}/editar")
    public String editarOrden(@PathVariable int id,
                              @RequestParam(name = "ver", required = false, defaultValue = "activos") String ver,
                              @RequestParam(name = "q", required = false, defaultValue = "") String q,
                              @RequestParam(name = "pagina", required = false, defaultValue = "0") int pagina,
                              Model model, RedirectAttributes ra) {
        OrdenTienda orden = ordenRepository.findById(id).orElse(null);
        if (orden == null) {
            ra.addFlashAttribute("error", "Ese pedido ya no existe.");
            return "redirect:/tienda-admin/ordenes";
        }
        model.addAttribute("orden", orden);
        model.addAttribute("ver", filtroValido(ver));
        model.addAttribute("q", q.trim());
        model.addAttribute("pagina", Math.max(pagina, 0));
        return "tienda-admin/orden_form";
    }

    @PostMapping("/orden/{id}/guardar")
    public String guardarOrden(@PathVariable int id,
                               @RequestParam Map<String, String> f,
                               @RequestParam(name = "ver", required = false, defaultValue = "activos") String ver,
                               @RequestParam(name = "q", required = false, defaultValue = "") String q,
                               @RequestParam(name = "pagina", required = false, defaultValue = "0") int pagina,
                               Model model, RedirectAttributes ra) {
        try {
            tiendaServicio.editarDatosCliente(id, new TiendaServicio.DatosCliente(
                    f.get("nombre"), f.get("cedula"), f.get("email"), f.get("telefono"),
                    f.get("direccion"), f.get("ciudad"), f.get("notas")));
            ra.addFlashAttribute("mensaje", "Datos del pedido actualizados.");
        } catch (IllegalArgumentException e) {
            OrdenTienda orden = ordenRepository.findById(id).orElse(null);
            if (orden == null) {
                ra.addFlashAttribute("error", e.getMessage());
                return "redirect:/tienda-admin/ordenes";
            }
            // Se vuelve a mostrar el formulario con lo que se escribió, para corregirlo
            model.addAttribute("orden", orden);
            model.addAttribute("datos", f);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("ver", filtroValido(ver));
            model.addAttribute("q", q.trim());
            model.addAttribute("pagina", Math.max(pagina, 0));
            return "tienda-admin/orden_form";
        }
        return volverALista(ra, ver, q, pagina);
    }

    /**
     * Elimina una compra de la lista (sirve para limpiar pruebas e intentos sin pagar).
     * NO devuelve dinero en Wompi ni en Addi, y si su venta ya estaba anotada en la contabilidad
     * de la tienda, ese ingreso se queda (se borra aparte en Contabilidad → Movimientos).
     */
    @PostMapping("/orden/{id}/eliminar")
    @Transactional
    public String eliminarOrden(@PathVariable int id,
                                @RequestParam(name = "ver", required = false, defaultValue = "activos") String ver,
                                @RequestParam(name = "q", required = false, defaultValue = "") String q,
                                @RequestParam(name = "pagina", required = false, defaultValue = "0") int pagina,
                                RedirectAttributes ra) {
        OrdenTienda orden = ordenRepository.findById(id).orElse(null);
        if (orden == null) {
            ra.addFlashAttribute("error", "Ese pedido ya no existe.");
        } else {
            String referencia = orden.getReferencia();
            boolean pagada = orden.isPagada();
            ordenRepository.delete(orden);
            ra.addFlashAttribute("mensaje", "Pedido " + referencia + " eliminado de la lista."
                    + (pagada ? " Si su venta estaba anotada en Contabilidad, ese ingreso sigue allá." : ""));
        }
        return volverALista(ra, ver, q, pagina);
    }

    private static String volverALista(RedirectAttributes ra, String ver, String q, int pagina) {
        ra.addAttribute("ver", filtroValido(ver));
        if (q != null && !q.isBlank()) ra.addAttribute("q", q.trim());
        ra.addAttribute("pagina", Math.max(pagina, 0));
        return "redirect:/tienda-admin/ordenes";
    }

    // ═══════════════════════════════════════════════════════════════
    // AVISO DE PEDIDOS NUEVOS (portal y menú de la tienda)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/api/pedidos-nuevos")
    @ResponseBody
    public Map<String, Object> pedidosNuevos() {
        List<Map<String, Object>> pedidos = new ArrayList<>();
        for (OrdenTienda o : ordenRepository.pedidosNuevos(PageRequest.of(0, 20))) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("referencia", o.getReferencia());
            fila.put("cliente", o.getNombreCliente());
            fila.put("fecha", o.getFechaPago() != null ? o.getFechaPago().format(FECHA_CORTA) : "");
            fila.put("total", o.getTotal());
            pedidos.add(fila);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cantidad", ordenRepository.contarPedidosNuevos());
        r.put("pedidos", pedidos);
        return r;
    }

    // ═══════════════════════════════════════════════════════════════
    // WHATSAPP AL CLIENTE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Enlace que abre WhatsApp en el chat del cliente con el mensaje ya escrito según en qué va
     * su pedido, con el número de pedido y la dirección para rastrearlo.
     * Devuelve null si la compra no está pagada o el celular no sirve para WhatsApp.
     */
    static String enlaceWhatsappRastreo(OrdenTienda o, String urlRastreo) {
        if (!o.isPagada()) return null;
        String celular = celularParaWhatsapp(o.getTelefono());
        if (celular == null) return null;

        String nombre = o.getPrimerNombre();
        String ref = "*" + o.getReferencia() + "*";
        String enlace = urlRastreo + o.getReferencia();
        StringBuilder m = new StringBuilder("Hola").append(nombre == null || nombre.isBlank() ? "" : ", " + nombre).append(". ");
        switch (o.getEstadoPedidoActual()) {
            case OrdenTienda.EN_FABRICACION:
                m.append("Tu pedido ").append(ref).append(" de P.C Express ya está en fabricación.\n")
                 .append("Puedes ver en qué va aquí:\n").append(enlace);
                break;
            case OrdenTienda.LISTO:
                m.append("Tu pedido ").append(ref).append(" de P.C Express está listo. ")
                 .append("Queremos acordar contigo el día de la entrega o la instalación.\n")
                 .append("Puedes verlo aquí:\n").append(enlace);
                break;
            case OrdenTienda.DESPACHADO:
                m.append("Tu pedido ").append(ref).append(" de P.C Express ya va en camino.\n");
                if (o.getGuiaEnvio() != null && !o.getGuiaEnvio().isBlank()) {
                    m.append("Envío: ").append(o.getGuiaEnvio().trim()).append("\n");
                }
                m.append("Puedes verlo aquí:\n").append(enlace);
                break;
            case OrdenTienda.ENTREGADO:
                m.append("Tu pedido ").append(ref).append(" de P.C Express quedó entregado. ¡Gracias por tu compra!\n")
                 .append("Si necesitas algo, escríbenos por aquí.");
                break;
            case OrdenTienda.CANCELADO:
                m.append("Te escribimos de P.C Express por tu pedido ").append(ref).append(", que quedó cancelado. ")
                 .append("Si tienes alguna duda, escríbenos por aquí.");
                break;
            default:
                m.append("Recibimos el pago de tu pedido en P.C Express.\n")
                 .append("Tu número de pedido es ").append(ref).append(".\n")
                 .append("Puedes ver en qué va aquí:\n").append(enlace);
        }
        return "https://wa.me/" + celular + "?text="
                + URLEncoder.encode(m.toString(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Deja el celular como lo pide WhatsApp: solo números y con el 57 de Colombia adelante.
     * "312 304 3450" → "573123043450". Devuelve null si no parece un celular.
     */
    static String celularParaWhatsapp(String telefono) {
        if (telefono == null) return null;
        String n = telefono.replaceAll("\\D", "");
        if (n.length() == 10 && n.startsWith("3")) return "57" + n;                 // celular colombiano
        if (n.length() == 12 && n.startsWith("573")) return n;                       // ya trae el 57
        if (telefono.trim().startsWith("+") && n.length() >= 10 && n.length() <= 15) return n;   // de otro país
        return null;
    }

    /** Dirección pública de la tienda: el dominio propio si existe; si no, la misma por la que se entró. */
    private String urlBaseTienda() {
        if (dominioTienda != null && !dominioTienda.isBlank()) {
            return "https://" + dominioTienda.split(",")[0].trim();
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}