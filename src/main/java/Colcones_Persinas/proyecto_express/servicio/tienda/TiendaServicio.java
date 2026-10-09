package Colcones_Persinas.proyecto_express.servicio.tienda;

import Colcones_Persinas.proyecto_express.modelo.almacen.DetallePedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.ItemOrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.TelaTienda;
import Colcones_Persinas.proyecto_express.repository.almacen.PedidoTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.ProductoTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.TelaTiendaRepository;
import Colcones_Persinas.proyecto_express.servicio.contabilidad.ContabilidadTiendaVirtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lógica de compra de la tienda virtual:
 *  - Cotiza el carrito (siempre en el servidor).
 *  - Crea la orden antes de mandar al cliente a pagar.
 *  - Aplica el resultado de Wompi o de Addi y, si el pago se aprueba, crea el pedido en Almacén
 *    y anota la plata como ingreso en Contabilidad (en la cuenta elegida para la tienda).
 *  - Permite corregir los datos del cliente de una compra desde la administración.
 */
@Service
public class TiendaServicio {

    public static final String VENDEDOR_TIENDA = "Tienda virtual";
    /** Así queda marcada la compra cuando el cliente eligió pagar a cuotas con Addi. */
    public static final String MEDIO_ADDI = "ADDI";
    private static final int MAX_LINEAS = 30;

    private final ProductoTiendaRepository productoRepository;
    private final TelaTiendaRepository telaRepository;
    private final OrdenTiendaRepository ordenRepository;
    private final PedidoTiendaRepository pedidoTiendaRepository;
    private final PrecioTiendaServicio precioServicio;
    private final ContabilidadTiendaVirtual contabilidadTienda;

    public TiendaServicio(ProductoTiendaRepository productoRepository, TelaTiendaRepository telaRepository,
                          OrdenTiendaRepository ordenRepository, PedidoTiendaRepository pedidoTiendaRepository,
                          PrecioTiendaServicio precioServicio, ContabilidadTiendaVirtual contabilidadTienda) {
        this.productoRepository = productoRepository;
        this.telaRepository = telaRepository;
        this.ordenRepository = ordenRepository;
        this.pedidoTiendaRepository = pedidoTiendaRepository;
        this.precioServicio = precioServicio;
        this.contabilidadTienda = contabilidadTienda;
    }

    /** Lo que guarda el navegador en el carrito (sin precios: los precios los pone el servidor). */
    public record ItemCarrito(Integer productoId, Integer telaId, String color, Integer anchoCm, Integer altoCm,
                              String lado, Integer cantidad) {}

    /** Una línea del carrito ya cotizada por el servidor. */
    public record LineaCotizada(int indice, boolean ok, String mensaje, String producto, String slug, Integer imagenId,
                                String detalle, BigDecimal precioUnitario, BigDecimal subtotal, int cantidad) {}

    /** Datos que el cliente escribe al pagar. */
    public record DatosCliente(String nombre, String cedula, String email, String telefono,
                               String direccion, String ciudad, String notas) {}

    // ═══════════════════════════════════════════════════════════════
    // CARRITO
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<LineaCotizada> cotizarCarrito(List<ItemCarrito> items) {
        List<LineaCotizada> lineas = new ArrayList<>();
        if (items == null) return lineas;
        for (int i = 0; i < items.size() && i < MAX_LINEAS; i++) {
            lineas.add(cotizarLinea(i, items.get(i)));
        }
        return lineas;
    }

    private LineaCotizada cotizarLinea(int i, ItemCarrito it) {
        ProductoTienda p = it.productoId() != null ? productoRepository.findById(it.productoId()).orElse(null) : null;
        if (p == null || !p.isActivo()) {
            return new LineaCotizada(i, false, "Este producto ya no está disponible.", "Producto no disponible",
                    null, null, "", null, null, 0);
        }
        TelaTienda tela = it.telaId() != null ? telaRepository.findById(it.telaId()).orElse(null) : null;
        String error = validarOpciones(p, tela, it);
        PrecioTiendaServicio.Cotizacion c = precioServicio.cotizar(p, tela, it.anchoCm(), it.altoCm(), it.cantidad());
        if (error == null && !c.ok()) error = c.mensaje();
        String detalle = detalle(p, tela, it);
        if (error != null) {
            return new LineaCotizada(i, false, error, p.getNombre(), p.getSlug(), p.getImagenId(), detalle, null, null,
                    it.cantidad() != null ? it.cantidad() : 1);
        }
        return new LineaCotizada(i, true, null, p.getNombre(), p.getSlug(), p.getImagenId(), detalle,
                c.precioUnitario(), c.subtotal(), c.cantidad());
    }

    /** Revisa color y lado del mando. Devuelve el mensaje de error, o null si todo está bien. */
    private String validarOpciones(ProductoTienda p, TelaTienda tela, ItemCarrito it) {
        if (p.isPorMetro() && tela != null && !tela.getListaColores().isEmpty()) {
            boolean colorValido = it.color() != null && tela.getListaColores().stream()
                    .anyMatch(c -> c.equalsIgnoreCase(it.color().trim()));
            if (!colorValido) return "Elige un color de la lista.";
        }
        if (p.isPorMetro() && p.isConMando()) {
            if (!"Izquierda".equals(it.lado()) && !"Derecha".equals(it.lado())) {
                return "Elige de qué lado va el mando.";
            }
        }
        return null;
    }

    private String detalle(ProductoTienda p, TelaTienda tela, ItemCarrito it) {
        List<String> partes = new ArrayList<>();
        if (tela != null) partes.add(tela.getNombre());
        if (it.color() != null && !it.color().isBlank()) partes.add("color " + it.color().trim());
        if (p.isPorMetro() && it.anchoCm() != null && it.altoCm() != null) {
            partes.add(ItemOrdenTienda.enMetros(it.anchoCm()) + " × " + ItemOrdenTienda.enMetros(it.altoCm()) + " m");
        }
        if (p.isPorMetro() && p.isConMando() && it.lado() != null && !it.lado().isBlank()) partes.add("mando a la " + it.lado().toLowerCase());
        return String.join(", ", partes);
    }

    // ═══════════════════════════════════════════════════════════════
    // ORDEN
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public OrdenTienda crearOrden(DatosCliente d, List<ItemCarrito> items) {
        validarCliente(d);
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Tu carrito está vacío.");
        if (items.size() > MAX_LINEAS) throw new IllegalArgumentException("El carrito tiene demasiados productos. Escríbenos por WhatsApp.");

        OrdenTienda orden = new OrdenTienda();
        orden.setReferencia(generarReferencia());
        orden.setNombreCliente(limpio(d.nombre()));
        orden.setCedula(limpio(d.cedula()));
        orden.setEmail(limpio(d.email()));
        orden.setTelefono(limpio(d.telefono()));
        orden.setDireccion(limpio(d.direccion()));
        orden.setCiudad(limpio(d.ciudad()));
        orden.setNotas(limpio(d.notas()));

        BigDecimal total = BigDecimal.ZERO;
        for (ItemCarrito it : items) {
            ProductoTienda p = it.productoId() != null ? productoRepository.findById(it.productoId()).orElse(null) : null;
            if (p == null || !p.isActivo()) throw new IllegalArgumentException("Uno de los productos ya no está disponible. Revisa tu carrito.");
            TelaTienda tela = it.telaId() != null ? telaRepository.findById(it.telaId()).orElse(null) : null;

            String error = validarOpciones(p, tela, it);
            if (error != null) throw new IllegalArgumentException(p.getNombre() + ": " + error);
            PrecioTiendaServicio.Cotizacion c = precioServicio.cotizar(p, tela, it.anchoCm(), it.altoCm(), it.cantidad());
            if (!c.ok()) throw new IllegalArgumentException(p.getNombre() + ": " + c.mensaje());

            ItemOrdenTienda item = new ItemOrdenTienda();
            item.setProductoId(p.getId());
            item.setProductoNombre(p.getNombre());
            item.setTelaNombre(tela != null ? tela.getNombre() : "");
            item.setColor(it.color() != null ? it.color().trim() : "");
            if (p.isPorMetro()) {
                item.setAnchoCm(it.anchoCm());
                item.setAltoCm(it.altoCm());
                item.setLadoMando(p.isConMando() ? it.lado() : "");
                item.setM2(c.m2());
                item.setRollo(c.rollo());
            }
            item.setCantidad(c.cantidad());
            item.setPrecioUnitario(c.precioUnitario());
            item.setSubtotal(c.subtotal());
            orden.agregarItem(item);
            total = total.add(c.subtotal());
        }
        orden.setTotal(total);
        return ordenRepository.save(orden);
    }

    private void validarCliente(DatosCliente d) {
        if (d == null) throw new IllegalArgumentException("Faltan tus datos.");
        if (vacio(d.nombre()))    throw new IllegalArgumentException("Escribe tu nombre completo.");
        if (vacio(d.cedula()) || d.cedula().replaceAll("\\D", "").length() < 5)
            throw new IllegalArgumentException("Escribe tu número de cédula.");
        if (vacio(d.email()) || !d.email().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("Escribe un correo válido. Ahí te llega el comprobante de pago.");
        if (vacio(d.telefono()) || d.telefono().replaceAll("\\D", "").length() < 7)
            throw new IllegalArgumentException("Escribe un celular para contactarte.");
        if (vacio(d.ciudad()))    throw new IllegalArgumentException("Escribe tu ciudad.");
        if (vacio(d.direccion())) throw new IllegalArgumentException("Escribe la dirección de entrega o instalación.");
    }

    // ═══════════════════════════════════════════════════════════════
    // RESULTADO DEL PAGO
    // ═══════════════════════════════════════════════════════════════

    /**
     * Aplica el estado que reporta Wompi (o Addi, ya pasado a los mismos nombres de Wompi).
     * Es seguro llamarlo varias veces con el mismo pago: el pedido de Almacén se crea una sola vez.
     */
    @Transactional
    public OrdenTienda aplicarPago(String referencia, String transaccionId, String estadoWompi,
                                   long centavos, String metodoPago) {
        OrdenTienda orden = ordenRepository.bloquearPorReferencia(referencia).orElse(null);
        if (orden == null) return null;
        if (OrdenTienda.APROBADA.equals(orden.getEstado())) return orden;   // ya procesada

        if (transaccionId != null) orden.setWompiTransaccionId(transaccionId);
        if (metodoPago != null) orden.setMetodoPago(metodoPago);

        switch (estadoWompi == null ? "" : estadoWompi) {
            case "APPROVED" -> {
                if (centavos != orden.getTotalEnCentavos()) {
                    orden.setEstado(OrdenTienda.ERROR);
                    orden.setNotas(limpio(orden.getNotas()) + " [El monto pagado (" + centavos / 100
                            + ") no coincide con el total de la orden. Revisar en "
                            + (MEDIO_ADDI.equals(orden.getMetodoPago()) ? "Addi" : "Wompi") + ".]");
                } else {
                    orden.setEstado(OrdenTienda.APROBADA);
                    orden.setFechaPago(LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA));
                    crearPedidoEnAlmacen(orden);
                }
            }
            case "DECLINED" -> orden.setEstado(OrdenTienda.RECHAZADA);
            case "VOIDED"   -> orden.setEstado(OrdenTienda.ANULADA);
            case "ERROR"    -> orden.setEstado(OrdenTienda.ERROR);
            default         -> orden.setEstado(OrdenTienda.PENDIENTE);
        }
        return ordenRepository.save(orden);
    }

    /** Crea el pedido en el listado de Almacén con lo comprado, ya pagado por completo. */
    private void crearPedidoEnAlmacen(OrdenTienda orden) {
        if (orden.getPedidoTiendaId() != null) return;

        PedidoTienda pedido = new PedidoTienda();
        pedido.setNombreCliente(orden.getNombreCliente());
        pedido.setCedula(orden.getCedula());
        pedido.setTelefono(orden.getTelefono());
        pedido.setDireccion(orden.getDireccion() + (vacio(orden.getCiudad()) ? "" : ", " + orden.getCiudad()));
        pedido.setFechaPedido(LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA));
        pedido.setVendedor(VENDEDOR_TIENDA);
        pedido.setFabrica("");
        pedido.setEstado("Pendiente");
        pedido.setMetodoPago(MEDIO_ADDI.equals(orden.getMetodoPago())
                ? "Addi"
                : "Wompi" + (vacio(orden.getMetodoPago()) ? "" : " - " + orden.getMetodoPago()));

        StringBuilder desc = new StringBuilder("Compra en la tienda virtual. Referencia ")
                .append(orden.getReferencia()).append(". Correo: ").append(orden.getEmail()).append('.');
        if (!vacio(orden.getNotas())) desc.append(" Notas del cliente: ").append(orden.getNotas());
        pedido.setDescripcion(desc.toString());

        for (ItemOrdenTienda it : orden.getItems()) {
            DetallePedidoTienda d = new DetallePedidoTienda();
            d.setProducto(it.getProductoNombre().toUpperCase(new Locale("es", "CO")));
            d.setMaterial(it.getDetalle().toUpperCase(new Locale("es", "CO")));
            d.setCantidad(it.getCantidad());
            d.setPrecioUnitario(it.getPrecioUnitario());
            d.setSubtotal(it.getSubtotal());
            d.setPrecioFabricaUnitario(BigDecimal.ZERO);
            d.setSubtotalFabrica(BigDecimal.ZERO);
            pedido.agregarDetalle(d);
        }

        pedido.setTotal(orden.getTotal());
        pedido.setDescuento(BigDecimal.ZERO);
        pedido.setAbono(orden.getTotal());   // ya está pagado completo
        pedido.setSaldo(BigDecimal.ZERO);

        pedidoTiendaRepository.save(pedido);
        orden.setPedidoTiendaId(pedido.getId());

        // La plata queda anotada sola en Contabilidad cuando el pago termine de guardarse.
        // Si algo falla allá, el pago y el pedido no se afectan.
        contabilidadTienda.anotarCuandoSeGuarde(pedido.getId(), MEDIO_ADDI.equals(orden.getMetodoPago()) ? "Addi" : "Wompi");
    }

    // ═══════════════════════════════════════════════════════════════
    // ADMINISTRACIÓN DE COMPRAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Corrige los datos de contacto y entrega de una compra (pantalla "Compras en línea").
     * No toca los productos, el total ni el estado: esos vienen del pago.
     *
     * Si la compra ya tiene su pedido en Almacén, los datos que cambiaron se copian
     * también allá, para no tener que editar en dos partes.
     *
     * @return true si además se actualizó el pedido de Almacén.
     */
    @Transactional
    public boolean editarDatosCliente(int ordenId, DatosCliente d) {
        validarCliente(d);
        OrdenTienda orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("Esa compra ya no existe."));

        boolean cambioNombre    = !limpio(d.nombre()).equals(limpio(orden.getNombreCliente()));
        boolean cambioCedula    = !limpio(d.cedula()).equals(limpio(orden.getCedula()));
        boolean cambioTelefono  = !limpio(d.telefono()).equals(limpio(orden.getTelefono()));
        boolean cambioDireccion = !limpio(d.direccion()).equals(limpio(orden.getDireccion()))
                || !limpio(d.ciudad()).equals(limpio(orden.getCiudad()));

        String notas = limpio(d.notas());
        if (notas.length() > 1000) notas = notas.substring(0, 1000);

        orden.setNombreCliente(limpio(d.nombre()));
        orden.setCedula(limpio(d.cedula()));
        orden.setEmail(limpio(d.email()));
        orden.setTelefono(limpio(d.telefono()));
        orden.setDireccion(limpio(d.direccion()));
        orden.setCiudad(limpio(d.ciudad()));
        orden.setNotas(notas);
        ordenRepository.save(orden);

        boolean hayCambiosParaAlmacen = cambioNombre || cambioCedula || cambioTelefono || cambioDireccion;
        if (orden.getPedidoTiendaId() == null || !hayCambiosParaAlmacen) return false;

        PedidoTienda pedido = pedidoTiendaRepository.findById(orden.getPedidoTiendaId()).orElse(null);
        if (pedido == null) return false;   // el pedido ya fue borrado en Almacén

        if (cambioNombre)   pedido.setNombreCliente(orden.getNombreCliente());
        if (cambioCedula)   pedido.setCedula(orden.getCedula());
        if (cambioTelefono) pedido.setTelefono(orden.getTelefono());
        if (cambioDireccion) {
            pedido.setDireccion(orden.getDireccion() + (vacio(orden.getCiudad()) ? "" : ", " + orden.getCiudad()));
        }
        pedidoTiendaRepository.save(pedido);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static final SecureRandom AZAR = new SecureRandom();
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** Ej: PCX261005143210-K7Q2M (fecha + 5 caracteres al azar). */
    private String generarReferencia() {
        String fecha = LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA).format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        StringBuilder sb = new StringBuilder("PCX").append(fecha).append('-');
        for (int i = 0; i < 5; i++) sb.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
        return sb.toString();
    }

    private static boolean vacio(String s) { return s == null || s.isBlank(); }
    private static String limpio(String s) { return s == null ? "" : s.trim(); }
}