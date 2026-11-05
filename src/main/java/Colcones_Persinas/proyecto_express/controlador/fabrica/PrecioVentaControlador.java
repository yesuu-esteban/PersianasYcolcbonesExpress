package Colcones_Persinas.proyecto_express.controlador.fabrica;

import Colcones_Persinas.proyecto_express.modelo.fabrica.Insumo;
import Colcones_Persinas.proyecto_express.repository.fabrica.InsumoRepository;
import Colcones_Persinas.proyecto_express.servicio.fabrica.PrecioVentaServicio;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pantalla "Precios de venta" de Fábrica (/inventario/precios-venta):
 *  - Persiana blackout (todo incluido): valor del m² por ancho de rollo.
 *  - Solo tela: valor del m² por ancho de rollo.
 *  - Insumos en venta directa: valor por unidad o por metro.
 * Lo que se guarda aquí es lo que sale escrito por defecto en las ventas.
 */
@Controller
@RequestMapping("/inventario/precios-venta")
public class PrecioVentaControlador {

    private final PrecioVentaServicio precioVentaServicio;
    private final InsumoRepository insumoRepository;

    public PrecioVentaControlador(PrecioVentaServicio precioVentaServicio, InsumoRepository insumoRepository) {
        this.precioVentaServicio = precioVentaServicio;
        this.insumoRepository = insumoRepository;
    }

    /** Una fila de la pantalla. */
    public static class Fila {
        private final String clave;
        private final String nombre;
        private final Double valor;

        public Fila(String clave, String nombre, Double valor) {
            this.clave = clave;
            this.nombre = nombre;
            this.valor = valor;
        }

        public String getClave() { return clave; }
        public String getNombre() { return nombre; }
        public Double getValor() { return valor; }
    }

    @GetMapping
    public String ver(Model model) {
        List<Fila> persianas = new ArrayList<>();
        List<Fila> telas = new ArrayList<>();
        for (double a : PrecioVentaServicio.ANCHOS) {
            String cp = PrecioVentaServicio.clavePersiana(a);
            persianas.add(new Fila(cp, PrecioVentaServicio.nombrePersiana(a), precioVentaServicio.valor(cp)));
            String ct = PrecioVentaServicio.claveTela(a);
            telas.add(new Fila(ct, PrecioVentaServicio.nombreTela(a), precioVentaServicio.valor(ct)));
        }
        List<Fila> insumos = new ArrayList<>();
        for (Insumo i : insumoRepository.findAllByOrderByNombreAsc()) {
            String c = PrecioVentaServicio.claveInsumo(i.getId());
            insumos.add(new Fila(c, nombreInsumo(i), precioVentaServicio.valor(c)));
        }
        model.addAttribute("persianas", persianas);
        model.addAttribute("telas", telas);
        model.addAttribute("insumos", insumos);
        return "fabrica/precios_venta";
    }

    /** Recibe "precio[CLAVE]" de cada fila y guarda solo los que cambiaron. */
    @PostMapping("/guardar")
    public String guardar(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        String usuario = usuarioActual();
        int cambiados = 0;
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            if (!k.startsWith("precio[") || !k.endsWith("]")) continue;
            String clave = k.substring(7, k.length() - 1);
            if (!clave.matches("(PERSIANA|TELA)_\\d\\.\\d\\d|INSUMO_\\d+")) continue;
            Double valor = leer(e.getValue());
            if (valor == null) continue;
            if (precioVentaServicio.guardar(clave, nombreDe(clave), valor, usuario)) cambiados++;
        }
        ra.addFlashAttribute("mensaje", cambiados > 0
                ? "Se guardaron " + cambiados + " precio(s). Desde ya salen así en las ventas nuevas."
                : "No cambió ningún precio.");
        return "redirect:/inventario/precios-venta";
    }

    private String nombreDe(String clave) {
        if (clave.startsWith("PERSIANA_")) return PrecioVentaServicio.nombrePersiana(Double.parseDouble(clave.substring(9)));
        if (clave.startsWith("TELA_")) return PrecioVentaServicio.nombreTela(Double.parseDouble(clave.substring(5)));
        try {
            int id = Integer.parseInt(clave.substring(7));
            return insumoRepository.findById(id).map(this::nombreInsumo).orElse(clave);
        } catch (NumberFormatException e) {
            return clave;
        }
    }

    private String nombreInsumo(Insumo i) {
        return i.getNombre() + (Boolean.TRUE.equals(i.getTieneMedida()) ? " (por metro)" : " (por unidad)");
    }

    private static Double leer(String texto) {
        if (texto == null) return null;
        String t = texto.replace("$", "").replace(" ", "").trim();
        if (t.isEmpty()) return null;
        if (t.contains(",") && !t.contains(".")) t = t.replace(',', '.');
        try { return Double.parseDouble(t); } catch (NumberFormatException e) { return null; }
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }
}