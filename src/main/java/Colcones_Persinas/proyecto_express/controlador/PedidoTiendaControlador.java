package Colcones_Persinas.proyecto_express.controlador;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import Colcones_Persinas.proyecto_express.modelo.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.DetallePedidoTienda;
import Colcones_Persinas.proyecto_express.repository.PedidoTiendaRepository;

@Controller
@RequestMapping("/tienda")
public class PedidoTiendaControlador {

    @Autowired
    private PedidoTiendaRepository pedidoTiendaRepository;

    @PreAuthorize("hasAnyRole('TIENDA','ADMIN')")
    @GetMapping("/nuevo")
    public String mostrarFormulario(Model model) {
        PedidoTienda pedido = new PedidoTienda();
        pedido.agregarDetalle(new DetallePedidoTienda());
        model.addAttribute("pedidoTienda", pedido);
        return "tienda/formulario";
    }

    @PreAuthorize("hasAnyRole('TIENDA','ADMIN')")
    @PostMapping("/guardar")
    public String guardarPedido(@ModelAttribute PedidoTienda pedidoTienda, RedirectAttributes redirectAttributes) {
        List<String> errores = validarProductos(pedidoTienda.getDetalles());
        errores.addAll(validarPrecios(pedidoTienda));
        if (!errores.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", String.join(" ", errores));
            return "redirect:/tienda/nuevo";
        }

        recalcularTotales(pedidoTienda);
        pedidoTiendaRepository.save(pedidoTienda);
        redirectAttributes.addFlashAttribute("mensaje", "Pedido registrado correctamente.");
        return "redirect:/tienda/listado";
    }

    // ─── Listado con filtros independientes (nombre, cédula, dirección, fecha de entrega, pago) ───
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/listado")
    public String listarPedidos(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String cedula,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String fechaEntrega,
            @RequestParam(required = false) String pagado,
            Model model) {

        // Siempre ordenado por ID ascendente: así la posición de cada pedido en la
        // tabla nunca cambia por editar/crear otros pedidos.
        List<PedidoTienda> todos = pedidoTiendaRepository.findAllByOrderByIdAsc();

        LocalDate fechaFiltro = null;
        if (fechaEntrega != null && !fechaEntrega.isBlank()) {
            try {
                fechaFiltro = LocalDate.parse(fechaEntrega);
            } catch (Exception ignored) {
                // Si viene una fecha mal formada, simplemente no se filtra por fecha.
            }
        }
        final LocalDate fechaFiltroFinal = fechaFiltro;

        List<PedidoTienda> pedidos = todos.stream()
                .filter(p -> nombre == null || nombre.isBlank()
                        || (p.getNombreCliente() != null
                            && p.getNombreCliente().toLowerCase().contains(nombre.trim().toLowerCase())))
                .filter(p -> cedula == null || cedula.isBlank()
                        || (p.getCedula() != null
                            && p.getCedula().toLowerCase().contains(cedula.trim().toLowerCase())))
                .filter(p -> direccion == null || direccion.isBlank()
                        || (p.getDireccion() != null
                            && p.getDireccion().toLowerCase().contains(direccion.trim().toLowerCase())))
                .filter(p -> fechaFiltroFinal == null
                        || (p.getFechaEntrega() != null
                            && p.getFechaEntrega().toLocalDate().equals(fechaFiltroFinal)))
                .filter(p -> {
                    if (pagado == null || pagado.isBlank()) return true;
                    boolean esPagado = "Pagado Completo".equals(p.getEstadoPago());
                    if (pagado.equalsIgnoreCase("si")) return esPagado;
                    if (pagado.equalsIgnoreCase("no")) return !esPagado;
                    return true;
                })
                .collect(Collectors.toList());

        model.addAttribute("pedidos", pedidos);
        model.addAttribute("nombre", nombre != null ? nombre : "");
        model.addAttribute("cedula", cedula != null ? cedula : "");
        model.addAttribute("direccion", direccion != null ? direccion : "");
        model.addAttribute("fechaEntrega", fechaEntrega != null ? fechaEntrega : "");
        model.addAttribute("pagado", pagado != null ? pagado : "");
        model.addAttribute("puedeCrearPedidos", puedeGestionarPedidos());
        return "tienda/listado";
    }

    // ─── Editar pedido ──────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('TIENDA','ADMIN')")
    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable("id") int id, Model model) {
        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();
        if (pedido.getDetalles().isEmpty()) {
            pedido.agregarDetalle(new DetallePedidoTienda());
        }
        model.addAttribute("pedidoTienda", pedido);
        return "tienda/editar_pedido";
    }

    @PreAuthorize("hasAnyRole('TIENDA','ADMIN')")
    @PostMapping("/editar/{id}")
    public String guardarEdicion(
            @PathVariable("id") int id,
            @ModelAttribute PedidoTienda formPedido,
            RedirectAttributes redirectAttributes) {

        List<String> errores = validarProductos(formPedido.getDetalles());
        errores.addAll(validarPrecios(formPedido));
        if (!errores.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", String.join(" ", errores));
            return "redirect:/tienda/editar/" + id;
        }

        // Se edita el mismo registro (mismo ID) que ya existía: nunca se crea uno nuevo
        // ni se reasigna el ID, por eso su posición en el listado ordenado no cambia.
        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();

        pedido.setNombreCliente(formPedido.getNombreCliente());
        pedido.setCedula(formPedido.getCedula());
        pedido.setDireccion(formPedido.getDireccion());
        pedido.setTelefono(formPedido.getTelefono());
        pedido.setDescripcion(formPedido.getDescripcion());
        pedido.setVendedor(formPedido.getVendedor());
        pedido.setFabrica(formPedido.getFabrica());
        pedido.setFechaEntrega(formPedido.getFechaEntrega());
        pedido.setAbono(formPedido.getAbono());
        pedido.setDescuento(formPedido.getDescuento());
        pedido.setMetodoPago(formPedido.getMetodoPago());
        pedido.setEstado(formPedido.getEstado());

        pedido.getDetalles().clear();
        for (DetallePedidoTienda d : formPedido.getDetalles()) {
            if (d.getProducto() == null || d.getProducto().isBlank()) continue;
            d.setId(0);
            pedido.agregarDetalle(d);
        }

        recalcularTotales(pedido);
        pedidoTiendaRepository.save(pedido);

        redirectAttributes.addFlashAttribute("mensaje", "Pedido #" + id + " actualizado correctamente.");
        return "redirect:/tienda/listado";
    }

    // ─── Eliminar pedido ────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('TIENDA','ADMIN')")
    @PostMapping("/eliminar/{id}")
    public String eliminarPedido(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        try {
            pedidoTiendaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "Pedido #" + id + " eliminado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el pedido: " + e.getMessage());
        }
        return "redirect:/tienda/listado";
    }

    // ─── Cambiar estado del pedido (Tienda_Admin sí puede) ─────────────
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @PostMapping("/actualizar-estado/{id}")
    public String actualizarEstado(
            @PathVariable("id") int id,
            @RequestParam String estado,
            RedirectAttributes redirectAttributes) {
        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();
        pedido.setEstado(estado);
        pedidoTiendaRepository.save(pedido);
        redirectAttributes.addFlashAttribute("mensaje", "Estado actualizado a \"" + estado + "\".");
        return "redirect:/tienda/listado";
    }

    // ─── Agregar abono (Tienda_Admin sí puede) ──────────────────────────
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @PostMapping("/abonar/{id}")
    public String agregarAbono(
            @PathVariable("id") int id,
            @RequestParam BigDecimal monto,
            RedirectAttributes redirectAttributes) {

        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();

        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error", "El monto del abono debe ser mayor a 0.");
            return "redirect:/tienda/listado";
        }
        if (monto.compareTo(pedido.getSaldo()) > 0) {
            redirectAttributes.addFlashAttribute("error",
                "El abono (" + monto + ") no puede ser mayor al saldo pendiente (" + pedido.getSaldo() + ").");
            return "redirect:/tienda/listado";
        }

        pedido.setAbono(pedido.getAbono().add(monto));
        pedido.setSaldo(pedido.getPrecioCliente().subtract(pedido.getAbono()));
        pedidoTiendaRepository.save(pedido);

        redirectAttributes.addFlashAttribute("mensaje",
            "Abono de " + monto + " registrado. Saldo restante: " + pedido.getSaldo());
        return "redirect:/tienda/listado";
    }

    // ─── Vista de impresión de un pedido ────────────────────────────────
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/imprimir/{id}")
    public String imprimirPedido(@PathVariable("id") int id, Model model) {
        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();
        model.addAttribute("pedidoTienda", pedido);
        return "tienda/imprimir_pedido";
    }

    // ─── Compartir pedido por WhatsApp (sin descargar ni imprimir nada) ─
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/compartir/{id}")
    public String compartirWhatsapp(@PathVariable("id") int id) {
        PedidoTienda pedido = pedidoTiendaRepository.findById(id).orElseThrow();

        String mensaje = construirMensajeWhatsapp(pedido);
        String textoCodificado = java.net.URLEncoder.encode(mensaje, java.nio.charset.StandardCharsets.UTF_8);

        String telefono = limpiarTelefono(pedido.getTelefono());
        String url = (telefono != null)
                ? "https://wa.me/" + telefono + "?text=" + textoCodificado
                : "https://wa.me/?text=" + textoCodificado;

        return "redirect:" + url;
    }

    // ─── Reporte de ventas por rango de fechas ──────────────────────────
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/reporte")
    public String verReporte(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            Model model) {

        List<PedidoTienda> pedidosFiltrados;

        if (desde != null && !desde.isBlank() && hasta != null && !hasta.isBlank()) {
            LocalDateTime fechaDesde = LocalDate.parse(desde).atStartOfDay();
            LocalDateTime fechaHasta = LocalDate.parse(hasta).atTime(23, 59, 59);

            pedidosFiltrados = pedidoTiendaRepository.findAllByOrderByIdAsc().stream()
                    .filter(p -> p.getFechaPedido() != null
                            && !p.getFechaPedido().isBefore(fechaDesde)
                            && !p.getFechaPedido().isAfter(fechaHasta))
                    .collect(Collectors.toList());
        } else {
            pedidosFiltrados = pedidoTiendaRepository.findAllByOrderByIdAsc();
        }

        int totalPedidos = pedidosFiltrados.size();
        BigDecimal sumaTotal = pedidosFiltrados.stream()
                .map(p -> p.getPrecioCliente() != null ? p.getPrecioCliente() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaAbonado = pedidosFiltrados.stream()
                .map(p -> p.getAbono() != null ? p.getAbono() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaPendiente = pedidosFiltrados.stream()
                .map(p -> p.getSaldo() != null ? p.getSaldo() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaCostoFabrica = pedidosFiltrados.stream()
                .map(PedidoTienda::getCostoFabricaTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaUtilidad = pedidosFiltrados.stream()
                .map(PedidoTienda::getUtilidadEstimada)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<PedidoTienda>> porVendedor = pedidosFiltrados.stream()
                .collect(Collectors.groupingBy(p ->
                        (p.getVendedor() == null || p.getVendedor().isBlank()) ? "Sin asignar" : p.getVendedor()));

        List<Map<String, Object>> resumenVendedores = new ArrayList<>();
        for (Map.Entry<String, List<PedidoTienda>> entry : porVendedor.entrySet()) {
            List<PedidoTienda> lista = entry.getValue();
            BigDecimal sumaVendedor = lista.stream()
                    .map(p -> p.getPrecioCliente() != null ? p.getPrecioCliente() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal utilidadVendedor = lista.stream()
                    .map(PedidoTienda::getUtilidadEstimada)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> fila = new HashMap<>();
            fila.put("vendedor", entry.getKey());
            fila.put("cantidadPedidos", lista.size());
            fila.put("totalVendido", sumaVendedor);
            fila.put("utilidad", utilidadVendedor);
            resumenVendedores.add(fila);
        }
        resumenVendedores.sort((a, b) ->
                ((BigDecimal) b.get("totalVendido")).compareTo((BigDecimal) a.get("totalVendido")));

        model.addAttribute("pedidos", pedidosFiltrados);
        model.addAttribute("desde", desde != null ? desde : "");
        model.addAttribute("hasta", hasta != null ? hasta : "");
        model.addAttribute("totalPedidos", totalPedidos);
        model.addAttribute("sumaTotal", sumaTotal);
        model.addAttribute("sumaAbonado", sumaAbonado);
        model.addAttribute("sumaPendiente", sumaPendiente);
        model.addAttribute("sumaCostoFabrica", sumaCostoFabrica);
        model.addAttribute("sumaUtilidad", sumaUtilidad);
        model.addAttribute("resumenVendedores", resumenVendedores);

        return "tienda/reporte";
    }

    // ─── Helpers ────────────────────────────────────────────────────────
    private List<String> validarProductos(List<DetallePedidoTienda> detalles) {
        List<String> errores = new ArrayList<>();
        if (detalles == null) return errores;

        for (DetallePedidoTienda d : detalles) {
            if (d.getProducto() == null || d.getProducto().isBlank()) continue;

            if (d.getCantidad() <= 0) {
                errores.add("\"" + d.getProducto() + "\": la cantidad debe ser mayor a 0.");
            }

            BigDecimal precio = d.getPrecioUnitario();
            if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
                errores.add("\"" + d.getProducto() + "\": el precio unitario debe ser mayor a 0.");
            }
        }

        return errores;
    }

    private List<String> validarPrecios(PedidoTienda pedido) {
        List<String> errores = new ArrayList<>();

        if (pedido.getDescuento() != null && pedido.getDescuento().compareTo(BigDecimal.ZERO) < 0) {
            errores.add("El descuento no puede ser negativo.");
        }

        return errores;
    }

    private void recalcularTotales(PedidoTienda pedido) {
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedidoTienda d : pedido.getDetalles()) {
            // Normaliza a mayúsculas lo que se guarda de aquí en adelante
            if (d.getProducto() != null) d.setProducto(d.getProducto().trim().toUpperCase());
            if (d.getMaterial() != null) d.setMaterial(d.getMaterial().trim().toUpperCase());

            BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(d.getCantidad()));
            d.setSubtotal(subtotal);

            BigDecimal precioFab = d.getPrecioFabricaUnitario() != null ? d.getPrecioFabricaUnitario() : BigDecimal.ZERO;
            d.setSubtotalFabrica(precioFab.multiply(BigDecimal.valueOf(d.getCantidad())));

            d.setPedidoTienda(pedido);
            total = total.add(subtotal);
        }
        pedido.setTotal(total);

        BigDecimal descuento = pedido.getDescuento() != null ? pedido.getDescuento() : BigDecimal.ZERO;
        if (descuento.compareTo(BigDecimal.ZERO) < 0) descuento = BigDecimal.ZERO;
        pedido.setDescuento(descuento);

        BigDecimal abono = pedido.getAbono() != null ? pedido.getAbono() : BigDecimal.ZERO;
        if (abono.compareTo(BigDecimal.ZERO) < 0) abono = BigDecimal.ZERO;
        pedido.setAbono(abono);

        pedido.setSaldo(pedido.getPrecioCliente().subtract(abono));
    }

    /**
     * Deja el teléfono solo con dígitos y le agrega el indicativo de Colombia (57)
     * si parece un celular local de 10 dígitos. Si el número ya trae indicativo
     * (más de 10 dígitos) se deja tal cual. Si no hay teléfono válido, retorna null
     * y wa.me simplemente abre el selector de contactos.
     */
    private String limpiarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String digitos = telefono.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) return null;
        if (digitos.length() == 10) return "57" + digitos;
        if (digitos.length() > 10) return digitos;
        return null;
    }

    private String formatearMonto(BigDecimal monto) {
        if (monto == null) monto = BigDecimal.ZERO;
        return java.text.NumberFormat.getInstance(new java.util.Locale("es", "CO")).format(monto);
    }

    private String construirMensajeWhatsapp(PedidoTienda pedido) {
        StringBuilder sb = new StringBuilder();

        sb.append("*PERSIANAS Y CORTINAS EXPRESS*\n");
        sb.append("Pedido #").append(pedido.getId()).append("\n\n");

        sb.append("*Cliente:* ").append(pedido.getNombreCliente()).append("\n");
        if (pedido.getCedula() != null && !pedido.getCedula().isBlank())
            sb.append("*Cédula:* ").append(pedido.getCedula()).append("\n");
        if (pedido.getDireccion() != null && !pedido.getDireccion().isBlank())
            sb.append("*Dirección:* ").append(pedido.getDireccion()).append("\n");
        if (pedido.getTelefono() != null && !pedido.getTelefono().isBlank())
            sb.append("*Teléfono:* ").append(pedido.getTelefono()).append("\n");
        if (pedido.getDescripcion() != null && !pedido.getDescripcion().isBlank())
            sb.append("*Descripción:* ").append(pedido.getDescripcion()).append("\n");

        sb.append("\n");
        if (pedido.getFechaEntrega() != null) {
            sb.append("*Fecha de entrega:* ")
              .append(pedido.getFechaEntrega().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
              .append("\n");
        }
        sb.append("*Estado del pedido:* ").append(pedido.getEstado()).append("\n\n");

        sb.append("*Productos:*\n");
        if (pedido.getDetalles() == null || pedido.getDetalles().isEmpty()) {
            sb.append("Sin productos registrados.\n");
        } else {
            for (DetallePedidoTienda d : pedido.getDetalles()) {
                sb.append("- ").append(d.getProducto());
                if (d.getMaterial() != null && !d.getMaterial().isBlank()) {
                    sb.append(" (").append(d.getMaterial()).append(")");
                }
                sb.append(" x").append(d.getCantidad());
                sb.append(" — $").append(formatearMonto(d.getSubtotal())).append("\n");
            }
        }

        sb.append("\n*Total:* $").append(formatearMonto(pedido.getTotal())).append("\n");
        if (pedido.getDescuento() != null && pedido.getDescuento().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("*Descuento:* $").append(formatearMonto(pedido.getDescuento())).append("\n");
        }
        sb.append("*Precio cliente:* $").append(formatearMonto(pedido.getPrecioCliente())).append("\n");
        sb.append("*Abonado:* $").append(formatearMonto(pedido.getAbono())).append("\n");
        sb.append("*Saldo pendiente:* $").append(formatearMonto(pedido.getSaldo())).append("\n");
        sb.append("*Estado de pago:* ").append(pedido.getEstadoPago()).append("\n");

        if (pedido.getMetodoPago() != null && !pedido.getMetodoPago().isBlank()) {
            sb.append("*Método de pago:* ").append(pedido.getMetodoPago()).append("\n");
        }

        return sb.toString();
    }

    private boolean puedeGestionarPedidos() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TIENDA") || a.getAuthority().equals("ROLE_ADMIN"));
    }
}