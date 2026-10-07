package Colcones_Persinas.proyecto_express.controlador.tienda;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import Colcones_Persinas.proyecto_express.repository.almacen.PedidoTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.Locale;

/**
 * Rastreo de pedidos de la tienda virtual. Es PÚBLICO (no requiere iniciar sesión):
 * el cliente escribe su número de pedido (la referencia que empieza por PCX) y ve en qué va.
 *
 *   /tienda/rastrear            → formulario para escribir el número
 *   /tienda/rastrear?numero=X   → resultado (lo que envía el formulario)
 *   /tienda/rastrear/X          → resultado (enlace directo para guardar o compartir)
 *
 * El avance sale del estado que el pedido tiene en Almacén, así que no hay que
 * actualizar nada aparte: basta con cambiar el estado en el listado de Almacén.
 *
 * La página NO muestra dirección, cédula, celular ni correo: solo el primer nombre,
 * lo que se pidió y el avance.
 */
@Controller
@RequestMapping("/tienda/rastrear")
public class RastreoTiendaControlador {

    private static final String VISTA = "tienda/rastreo";

    private final OrdenTiendaRepository ordenRepository;
    private final PedidoTiendaRepository pedidoTiendaRepository;

    /** Los mismos datos de contacto que usa el resto de la tienda (encabezado y pie). */
    @Value("${tienda.whatsapp:573041354963}")
    private String whatsapp;

    @Value("${tienda.telefonos:304 135 4963, 312 206 5950, 314 866 0215}")
    private String telefonos;

    public RastreoTiendaControlador(OrdenTiendaRepository ordenRepository,
                                    PedidoTiendaRepository pedidoTiendaRepository) {
        this.ordenRepository = ordenRepository;
        this.pedidoTiendaRepository = pedidoTiendaRepository;
    }

    @ModelAttribute
    public void datosComunes(Model model) {
        model.addAttribute("whatsapp", whatsapp);
        model.addAttribute("telefonos", Arrays.stream(telefonos.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    @GetMapping({"", "/"})
    public String rastrear(@RequestParam(name = "numero", required = false) String numero, Model model) {
        return mostrar(numero, model);
    }

    @GetMapping("/{numero}")
    public String rastrearConEnlace(@PathVariable String numero, Model model) {
        return mostrar(numero, model);
    }

    private String mostrar(String numero, Model model) {
        String limpio = normalizar(numero);
        model.addAttribute("numero", limpio);
        model.addAttribute("buscado", !limpio.isEmpty());
        if (limpio.isEmpty()) return VISTA;

        OrdenTienda orden = ordenRepository.findByReferencia(limpio).orElse(null);
        model.addAttribute("orden", orden);

        if (orden != null && OrdenTienda.APROBADA.equals(orden.getEstado())) {
            String estadoAlmacen = null;
            if (orden.getPedidoTiendaId() != null) {
                estadoAlmacen = pedidoTiendaRepository.findById(orden.getPedidoTiendaId())
                        .map(PedidoTienda::getEstado).orElse(null);
            }
            model.addAttribute("paso", pasoSegunAlmacen(estadoAlmacen));
        }
        return VISTA;
    }

    /**
     * Convierte el estado del pedido en Almacén en el paso que ve el cliente:
     *   Pendiente              → 1  Pago recibido
     *   Pedido                 → 2  En preparación
     *   En Bodega              → 3  Listo para entregar o instalar
     *   Instalado / Terminado  → 4  Entregado
     * Si el pedido ya no está en Almacén o tiene otro estado, se queda en el paso 1.
     */
    static int pasoSegunAlmacen(String estadoAlmacen) {
        if (estadoAlmacen == null) return 1;
        switch (estadoAlmacen.trim().toLowerCase(Locale.ROOT)) {
            case "pedido":
                return 2;
            case "en bodega":
                return 3;
            case "instalado":
            case "terminado":
                return 4;
            default:
                return 1;
        }
    }

    /**
     * Deja el número como se guarda (PCX + 12 números + guion + 5 caracteres), aunque el
     * cliente lo escriba en minúsculas, con espacios o sin el guion.
     */
    static String normalizar(String numero) {
        if (numero == null) return "";
        String s = numero.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (s.matches("PCX\\d{12}[A-Z0-9]{5}")) return s.substring(0, 15) + "-" + s.substring(15);
        String tal = numero.trim().toUpperCase(Locale.ROOT);
        return tal.length() > 40 ? tal.substring(0, 40) : tal;
    }
}