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

    // ─── Listado con búsqueda por nombre o cédula ───────────────────────
    @PreAuthorize("hasAnyRole('TIENDA','TIENDA_ADMIN','ADMIN')")
    @GetMapping("/listado")
    public String listarPedidos(
            @RequestParam(required = false) String buscar,
            Model model) {

        List<PedidoTienda> todos = pedidoTiendaRepository.findAll();

        List<PedidoTienda> pedidos;
        if (buscar != null && !buscar.isBlank()) {
            String q = buscar.trim().toLowerCase();
            pedidos = todos.stream()
                    .filter(p ->
                        (p.getNombreCliente() != null && p.getNombreCliente().toLowerCase().contains(q)) ||
                        (p.getCedula() != null && p.getCedula().toLowerCase().contains(q))
                    )
                    .collect(Collectors.toList());
        } else {
            pedidos = todos;
        }

        model.addAttribute("pedidos", pedidos);
        model.addAttribute("buscar", buscar != null ? buscar : "");
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

            pedidosFiltrados = pedidoTiendaRepository.findAll().stream()
                    .filter(p -> p.getFechaPedido() != null
                            && !p.getFechaPedido().isBefore(fechaDesde)
                            && !p.getFechaPedido().isAfter(fechaHasta))
                    .collect(Collectors.toList());
        } else {
            pedidosFiltrados = pedidoTiendaRepository.findAll();
        }

        // ── Totales generales ──
        int totalPedidos = pedidosFiltrados.size();

        BigDecimal sumaTotal = pedidosFiltrados.stream()
                .map(PedidoTienda::getPrecioCliente)
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
        BigDecimal sumaUtilidad = sumaTotal.subtract(sumaCostoFabrica);

        // ── Desglose por vendedor ──
        Map<String, List<PedidoTienda>> porVendedor = pedidosFiltrados.stream()
                .collect(Collectors.groupingBy(p ->
                        (p.getVendedor() == null || p.getVendedor().isBlank()) ? "Sin asignar" : p.getVendedor()));

        List<Map<String, Object>> resumenVendedores = new ArrayList<>();
        for (Map.Entry<String, List<PedidoTienda>> entry : porVendedor.entrySet()) {
            List<PedidoTienda> lista = entry.getValue();

            BigDecimal sumaVendedor = lista.stream()
                    .map(PedidoTienda::getPrecioCliente)
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

            BigDecimal precioFabrica = d.getPrecioFabricaUnitario();
            if (precioFabrica != null && precioFabrica.compareTo(BigDecimal.ZERO) < 0) {
                errores.add("\"" + d.getProducto() + "\": el precio de fábrica no puede ser negativo.");
            }
        }

        return errores;
    }

    /** Valida que el descuento (si se indicó) no sea negativo. */
    private List<String> validarPrecios(PedidoTienda pedido) {
        List<String> errores = new ArrayList<>();

        if (pedido.getDescuento() != null && pedido.getDescuento().compareTo(BigDecimal.ZERO) < 0) {
            errores.add("El descuento no puede ser negativo.");
        }

        return errores;
    }

    private void recalcularTotales(PedidoTienda pedido) {
        // Suma automática de los productos: precio de venta y costo de fábrica, línea por línea
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedidoTienda d : pedido.getDetalles()) {
            BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal precioFabrica = d.getPrecioFabricaUnitario() != null ? d.getPrecioFabricaUnitario() : BigDecimal.ZERO;
            BigDecimal cantidad = BigDecimal.valueOf(d.getCantidad());

            BigDecimal subtotal = precio.multiply(cantidad);
            BigDecimal subtotalFabrica = precioFabrica.multiply(cantidad);

            d.setSubtotal(subtotal);
            d.setSubtotalFabrica(subtotalFabrica);
            d.setPedidoTienda(pedido);

            total = total.add(subtotal);
        }
        pedido.setTotal(total);

        // Descuento (no puede ser negativo)
        BigDecimal descuento = pedido.getDescuento() != null ? pedido.getDescuento() : BigDecimal.ZERO;
        if (descuento.compareTo(BigDecimal.ZERO) < 0) descuento = BigDecimal.ZERO;
        pedido.setDescuento(descuento);

        // Abono (no puede ser negativo)
        BigDecimal abono = pedido.getAbono() != null ? pedido.getAbono() : BigDecimal.ZERO;
        if (abono.compareTo(BigDecimal.ZERO) < 0) abono = BigDecimal.ZERO;
        pedido.setAbono(abono);

        // Saldo = precio real al cliente (total - descuento) - abono
        BigDecimal saldo = pedido.getPrecioCliente().subtract(abono);
        if (saldo.compareTo(BigDecimal.ZERO) < 0) saldo = BigDecimal.ZERO;
        pedido.setSaldo(saldo);
    }

    private boolean puedeGestionarPedidos() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TIENDA") || a.getAuthority().equals("ROLE_ADMIN"));
    }
}