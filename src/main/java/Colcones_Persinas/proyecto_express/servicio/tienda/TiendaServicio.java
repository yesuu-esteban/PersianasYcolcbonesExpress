package Colcones_Persinas.proyecto_express.servicio.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.ItemOrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.TelaTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.ProductoTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.TelaTiendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de compra de la tienda virtual:
 *  - Cotiza el carrito (siempre en el servidor).
 *  - Crea la orden antes de mandar al cliente a pagar.
 *  - Aplica el resultado de Wompi o de Addi. Si el pago se aprueba, el pedido queda en "Nuevo"
 *    en la pantalla Pedidos de la tienda y la plata se anota sola en la contabilidad DE LA TIENDA.
 *    La tienda es un módulo aparte: ya no crea nada en Almacén ni en la contabilidad de Almacén.
 *  - Permite corregir los datos del cliente y cambiar el estado del pedido desde la administración.
 */
@Service
public class TiendaServicio {

    /**
     * Vendedor con el que la tienda creaba antes sus pedidos en Almacén. Ya no se crean,
     * pero Almacén lo usa para no mostrar esos pedidos viejos (ahora se ven en la tienda).
     */
    public static final String VENDEDOR_TIENDA = "Tienda virtual";
    /** Así queda marcada la compra cuando el cliente eligió pagar a cuotas con Addi. */
    public static final String MEDIO_ADDI = "ADDI";
    private static final int MAX_LINEAS = 30;

    private final ProductoTiendaRepository productoRepository;
    private final TelaTiendaRepository telaRepository;
    private final OrdenTiendaRepository ordenRepository;
    private final PrecioTiendaServicio precioServicio;
    private final ContabilidadTiendaServicio contabilidadTienda;

    public TiendaServicio(ProductoTiendaRepository productoRepository, TelaTiendaRepository telaRepository,
                          OrdenTiendaRepository ordenRepository, PrecioTiendaServicio precioServicio,
                          ContabilidadTiendaServicio contabilidadTienda) {
        this.productoRepository = productoRepository;
        this.telaRepository = telaRepository;
        this.ordenRepository = ordenRepository;
        this.precioServicio = precioServicio;
        this.contabilidadTienda = contabilidadTienda;
    }

    /**
     * Lo que guarda el navegador en el carrito (sin precios: los precios los pone el servidor).
     * cabezal y contrario pueden venir vacíos (carritos guardados antes de estas opciones): cuentan como "no".
     * sistema ("BASTON" / "CONTROL") y apertura solo vienen en los rieles de onda serena.
     */
    public record ItemCarrito(Integer productoId, Integer telaId, String color, Integer anchoCm, Integer altoCm,
                              String lado, Integer cantidad, Boolean cabezal, Boolean contrario,
                              String sistema, String apertura) {
        public boolean conCabezal() { return Boolean.TRUE.equals(cabezal); }
        public boolean alContrario() { return Boolean.TRUE.equals(contrario); }
    }

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
        PrecioTiendaServicio.Cotizacion c = precioServicio.cotizar(p, tela, it.anchoCm(), it.altoCm(), it.cantidad(),
                it.conCabezal() && p.isCabezalDisponible(), it.sistema());
        if (error == null && !c.ok()) error = c.mensaje();
        String detalle = detalle(p, tela, it);
        if (error != null) {
            return new LineaCotizada(i, false, error, p.getNombre(), p.getSlug(), p.getImagenId(), detalle, null, null,
                    it.cantidad() != null ? it.cantidad() : 1);
        }
        return new LineaCotizada(i, true, null, p.getNombre(), p.getSlug(), p.getImagenId(), detalle,
                c.precioUnitario(), c.subtotal(), c.cantidad());
    }

    /** Revisa color, lado del mando, cabezal, enrollado y (en rieles) sistema y apertura. Devuelve el error o null. */
    private String validarOpciones(ProductoTienda p, TelaTienda tela, ItemCarrito it) {
        if (p.isRiel()) {
            if (p.precioRielPorMetro(it.sistema()) == null) return "Elige si lo quieres con bastón o con control.";
            if (!ProductoTienda.APERTURAS_RIEL.contains(it.apertura() == null ? "" : it.apertura())) {
                return "Elige hacia dónde abre la cortina.";
            }
            return null;
        }
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
        if (it.conCabezal() && !p.isCabezalDisponible()) return "Este producto no tiene la opción de cabezal.";
        if (it.alContrario() && !p.isContrarioDisponible()) return "Este producto no se puede pedir enrollado al contrario.";
        return null;
    }

    private String detalle(ProductoTienda p, TelaTienda tela, ItemCarrito it) {
        List<String> partes = new ArrayList<>();
        if (p.isRiel()) {
            if (it.anchoCm() != null) partes.add(ItemOrdenTienda.enMetros(it.anchoCm()) + " m de ancho");
            String sistema = ProductoTienda.textoSistema(it.sistema());
            if (!sistema.isEmpty()) partes.add(sistema);
            String apertura = ItemOrdenTienda.textoApertura(it.apertura());
            if (!apertura.isEmpty()) partes.add(apertura);
            return String.join(", ", partes);
        }
        if (tela != null) partes.add(tela.getNombre());
        if (it.color() != null && !it.color().isBlank()) partes.add("color " + it.color().trim());
        if (p.isPorMetro() && it.anchoCm() != null && it.altoCm() != null) {
            partes.add(ItemOrdenTienda.enMetros(it.anchoCm()) + " × " + ItemOrdenTienda.enMetros(it.altoCm()) + " m");
        }
        if (p.isPorMetro() && p.isConMando() && it.lado() != null && !it.lado().isBlank()) partes.add("mando a la " + it.lado().toLowerCase());
        if (it.conCabezal() && p.isCabezalDisponible()) partes.add(ItemOrdenTienda.TEXTO_CABEZAL);
        if (it.alContrario() && p.isContrarioDisponible()) partes.add(ItemOrdenTienda.TEXTO_CONTRARIO);
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
            PrecioTiendaServicio.Cotizacion c = precioServicio.cotizar(p, tela, it.anchoCm(), it.altoCm(), it.cantidad(),
                    it.conCabezal(), it.sistema());
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
                item.setConCabezal(it.conCabezal());
                item.setEnrolladoContrario(it.alContrario());
            } else if (p.isRiel()) {
                item.setAnchoCm(it.anchoCm());
                item.setSistemaRiel(ProductoTienda.textoSistema(it.sistema()));
                item.setAperturaRiel(it.apertura());
            } else if (p.isDeDropi()) {
                // Lo despacha Dropi: se guarda con qué pedirlo allá y cuánto cuesta
                item.setProveedor(ProductoTienda.PROVEEDOR_DROPI);
                item.setCodigoProveedor(p.getCodigoProveedor());
                item.setCostoProveedor(p.getCostoProveedor());
                item.setEnlaceProveedor(p.getEnlaceProveedor());
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
     * Es seguro llamarlo varias veces con el mismo pago: una compra ya aprobada no se vuelve a procesar
     * y la venta se anota una sola vez en la contabilidad de la tienda.
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
                    LocalDateTime ahora = LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA);
                    orden.setEstado(OrdenTienda.APROBADA);
                    orden.setFechaPago(ahora);
                    // Queda en "Nuevo" en la pantalla Pedidos de la tienda
                    orden.setEstadoPedido(OrdenTienda.NUEVO);
                    orden.setFechaEstadoPedido(ahora);
                    // La plata se anota sola en la contabilidad de la tienda cuando el pago termine de guardarse.
                    // Si algo falla allá, el pago no se afecta.
                    contabilidadTienda.anotarVentaCuandoSeGuarde(orden.getId());
                }
            }
            case "DECLINED" -> orden.setEstado(OrdenTienda.RECHAZADA);
            case "VOIDED"   -> orden.setEstado(OrdenTienda.ANULADA);
            case "ERROR"    -> orden.setEstado(OrdenTienda.ERROR);
            default         -> orden.setEstado(OrdenTienda.PENDIENTE);
        }
        return ordenRepository.save(orden);
    }

    // ═══════════════════════════════════════════════════════════════
    // ADMINISTRACIÓN DE COMPRAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Corrige los datos de contacto y entrega de una compra (pantalla "Pedidos").
     * No toca los productos, el total ni el estado del pago: esos vienen del pago.
     */
    @Transactional
    public void editarDatosCliente(int ordenId, DatosCliente d) {
        validarCliente(d);
        OrdenTienda orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("Ese pedido ya no existe."));

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
    }

    /**
     * Cambia en qué va un pedido pagado: Nuevo, En fabricación, Listo, Despachado, Entregado o Cancelado.
     * Se puede devolver a un estado anterior si se eligió mal.
     *
     * @param guia transportadora y número de guía (solo se guarda si el estado es Despachado; puede ir vacío)
     */
    @Transactional
    public OrdenTienda cambiarEstadoPedido(int ordenId, String estado, String guia) {
        OrdenTienda orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("Ese pedido ya no existe."));
        if (!orden.isPagada()) {
            throw new IllegalArgumentException("Solo se puede cambiar el estado de un pedido pagado.");
        }
        if (estado == null || !OrdenTienda.ESTADOS_PEDIDO.containsKey(estado)) {
            throw new IllegalArgumentException("Elige un estado de la lista.");
        }
        if (OrdenTienda.DESPACHADO.equals(estado)) {
            String g = limpio(guia);
            orden.setGuiaEnvio(g.length() > 200 ? g.substring(0, 200) : g);
        }
        if (!estado.equals(orden.getEstadoPedidoActual())) {
            orden.setEstadoPedido(estado);
            orden.setFechaEstadoPedido(LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA));
        }
        return ordenRepository.save(orden);
    }

    /**
     * Anota el número de pedido (o la guía) con que se pidió en Dropi un producto de este pedido.
     * Vacío = se borra (vuelve a quedar "por pedir en Dropi").
     */
    @Transactional
    public ItemOrdenTienda anotarPedidoProveedor(int ordenId, int itemId, String numero) {
        OrdenTienda orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("Ese pedido ya no existe."));
        if (!orden.isPagada()) throw new IllegalArgumentException("Ese pedido no está pagado.");
        ItemOrdenTienda item = orden.getItems().stream().filter(i -> i.getId() == itemId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ese producto ya no está en el pedido."));
        if (!item.isDeDropi()) throw new IllegalArgumentException("Ese producto no es de Dropi.");

        String n = limpio(numero);
        if (n.length() > 200) n = n.substring(0, 200);
        item.setPedidoProveedor(n.isEmpty() ? null : n);
        item.setFechaPedidoProveedor(n.isEmpty() ? null : LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA));
        ordenRepository.save(orden);
        return item;
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