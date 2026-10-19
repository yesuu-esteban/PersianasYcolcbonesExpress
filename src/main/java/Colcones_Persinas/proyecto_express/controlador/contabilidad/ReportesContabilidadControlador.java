package Colcones_Persinas.proyecto_express.controlador.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CategoriaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.ConfiguracionContable;
import Colcones_Persinas.proyecto_express.repository.contabilidad.CategoriaContableRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.CuentaContableRepository;
import Colcones_Persinas.proyecto_express.servicio.contabilidad.ContabilidadServicio;
import Colcones_Persinas.proyecto_express.servicio.contabilidad.ReporteContableExcel;
import Colcones_Persinas.proyecto_express.servicio.contabilidad.ReporteContablePdf;
import Colcones_Persinas.proyecto_express.servicio.contabilidad.ReporteContableServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Reportes de la contabilidad (Contabilidad → Reportes).
 *
 *   /contabilidad/reportes        escoger qué resumen sacar y verlo en pantalla
 *   /contabilidad/reportes/excel  el mismo resumen en Excel (.xlsx)
 *   /contabilidad/reportes/pdf    el mismo resumen en PDF
 *
 * Los tres reciben lo mismo: modo (general | personalizado), desde, hasta, sub (una o varias subcategorías),
 * cuenta (opcional, una o varias) y titulo (opcional).
 */
@Controller
@RequestMapping("/contabilidad/reportes")
@PreAuthorize("hasAnyRole('ADMIN','TIENDA_ADMIN')")
public class ReportesContabilidadControlador {

    private static final Logger log = LoggerFactory.getLogger(ReportesContabilidadControlador.class);
    /** En pantalla se muestran máximo estos movimientos; el Excel y el PDF traen todos. */
    private static final int MAX_EN_PANTALLA = 300;

    private final ReporteContableServicio reportes;
    private final ReporteContableExcel excel;
    private final ReporteContablePdf pdf;
    private final ContabilidadServicio contabilidad;
    private final CategoriaContableRepository categoriaRepository;
    private final CuentaContableRepository cuentaRepository;

    public ReportesContabilidadControlador(ReporteContableServicio reportes, ReporteContableExcel excel, ReporteContablePdf pdf,
                                           ContabilidadServicio contabilidad, CategoriaContableRepository categoriaRepository,
                                           CuentaContableRepository cuentaRepository) {
        this.reportes = reportes;
        this.excel = excel;
        this.pdf = pdf;
        this.contabilidad = contabilidad;
        this.categoriaRepository = categoriaRepository;
        this.cuentaRepository = cuentaRepository;
    }

    /** Para el aviso "La contabilidad empieza el …" y el límite de las fechas. */
    @ModelAttribute
    public void datosDeInicio(Model model) {
        ConfiguracionContable conf = contabilidad.configuracion();
        boolean hay = conf != null && conf.getFechaInicio() != null;
        model.addAttribute("fechaInicioTexto", hay ? conf.getFechaInicioFormateada() : "");
        model.addAttribute("fechaInicioIso", hay ? conf.getFechaInicio().toString() : null);
    }

    // ═══════════════════════════════════════════════════════════════
    // PANTALLA
    // ═══════════════════════════════════════════════════════════════

    @GetMapping({"", "/"})
    public String pantalla(@RequestParam(required = false) String modo,
                           @RequestParam(required = false) String desde,
                           @RequestParam(required = false) String hasta,
                           @RequestParam(required = false) List<String> sub,
                           @RequestParam(required = false) List<String> cuenta,
                           @RequestParam(required = false) String titulo,
                           @RequestParam(required = false) String ver,
                           Model model) {
        LocalDate hoy = ContabilidadServicio.hoy();
        LocalDate inicio = contabilidad.fechaInicio();
        boolean general = !"personalizado".equals(modo);
        LocalDate fDesde = fecha(desde, hoy.withDayOfMonth(1));
        LocalDate fHasta = fecha(hasta, hoy);
        if (inicio != null && fDesde.isBefore(inicio)) fDesde = inicio;
        Set<Integer> subs = enteros(sub);
        Set<Integer> cuentas = enteros(cuenta);

        // Catálogo para escoger: categorías de ingresos y de egresos, y las cuentas
        List<CategoriaContable> ingresos = new ArrayList<>(), egresos = new ArrayList<>();
        for (CategoriaContable c : categoriaRepository.findAllByOrderByOrdenAscNombreAsc()) {
            if (c.getSubcategorias().isEmpty()) continue;
            (c.isDeIngreso() ? ingresos : egresos).add(c);
        }
        model.addAttribute("categoriasIngreso", ingresos);
        model.addAttribute("categoriasEgreso", egresos);
        model.addAttribute("cuentas", cuentaRepository.findAllByOrderByOrdenAscNombreAsc());

        // Lo que está escogido (para que el formulario quede igual al volver)
        model.addAttribute("general", general);
        model.addAttribute("desde", fDesde.toString());
        model.addAttribute("hasta", fHasta.toString());
        model.addAttribute("subsElegidas", subs);
        model.addAttribute("cuentasElegidas", cuentas);
        model.addAttribute("titulo", titulo == null ? "" : titulo);
        model.addAttribute("hoy", hoy.toString());

        // Atajos de periodo
        Map<String, String[]> atajos = new LinkedHashMap<>();
        atajos.put("Este mes", new String[]{hoy.withDayOfMonth(1).toString(), hoy.toString()});
        LocalDate mesPasado = hoy.withDayOfMonth(1).minusMonths(1);
        atajos.put("Mes pasado", new String[]{mesPasado.toString(), mesPasado.withDayOfMonth(mesPasado.lengthOfMonth()).toString()});
        atajos.put("Últimos 3 meses", new String[]{hoy.withDayOfMonth(1).minusMonths(2).toString(), hoy.toString()});
        atajos.put("Este año", new String[]{hoy.withDayOfYear(1).toString(), hoy.toString()});
        if (inicio != null && !inicio.isAfter(hoy)) atajos.put("Desde el inicio", new String[]{inicio.toString(), hoy.toString()});
        model.addAttribute("atajos", atajos);

        // Si se oprimió "Ver", se arma el reporte
        if (ver != null) {
            try {
                ReporteContableServicio.Reporte r = reportes.generar(filtro(general, fDesde, fHasta, subs, cuentas, titulo), usuarioActual());
                model.addAttribute("reporte", r);
                model.addAttribute("detalleEnPantalla", r.getDetalle().subList(0, Math.min(MAX_EN_PANTALLA, r.getDetalle().size())));
                model.addAttribute("detalleRecortado", r.getDetalle().size() > MAX_EN_PANTALLA);
                model.addAttribute("maxEnPantalla", MAX_EN_PANTALLA);
                model.addAttribute("consulta", consulta(general, fDesde, fHasta, subs, cuentas, titulo));
            } catch (IllegalArgumentException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        return "contabilidad/reportes";
    }

    // ═══════════════════════════════════════════════════════════════
    // DESCARGAS
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/excel")
    public ResponseEntity<byte[]> descargarExcel(@RequestParam(required = false) String modo,
                                                 @RequestParam(required = false) String desde,
                                                 @RequestParam(required = false) String hasta,
                                                 @RequestParam(required = false) List<String> sub,
                                                 @RequestParam(required = false) List<String> cuenta,
                                                 @RequestParam(required = false) String titulo) {
        try {
            ReporteContableServicio.Reporte r = armar(modo, desde, hasta, sub, cuenta, titulo);
            return archivo(excel.generar(r), r.getNombreArchivo() + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (IllegalArgumentException e) {
            return aviso(e.getMessage());
        } catch (Exception e) {
            log.error("[Contabilidad] No se pudo generar el Excel del reporte", e);
            return aviso("No se pudo generar el Excel: " + e.getMessage());
        }
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> descargarPdf(@RequestParam(required = false) String modo,
                                               @RequestParam(required = false) String desde,
                                               @RequestParam(required = false) String hasta,
                                               @RequestParam(required = false) List<String> sub,
                                               @RequestParam(required = false) List<String> cuenta,
                                               @RequestParam(required = false) String titulo) {
        try {
            ReporteContableServicio.Reporte r = armar(modo, desde, hasta, sub, cuenta, titulo);
            return archivo(pdf.generar(r), r.getNombreArchivo() + ".pdf", "application/pdf");
        } catch (IllegalArgumentException e) {
            return aviso(e.getMessage());
        } catch (Exception e) {
            log.error("[Contabilidad] No se pudo generar el PDF del reporte", e);
            return aviso("No se pudo generar el PDF: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AYUDAS
    // ═══════════════════════════════════════════════════════════════

    private ReporteContableServicio.Reporte armar(String modo, String desde, String hasta, List<String> sub,
                                                  List<String> cuenta, String titulo) {
        LocalDate hoy = ContabilidadServicio.hoy();
        return reportes.generar(filtro(!"personalizado".equals(modo), fecha(desde, hoy.withDayOfMonth(1)), fecha(hasta, hoy),
                enteros(sub), enteros(cuenta), titulo), usuarioActual());
    }

    private static ReporteContableServicio.Filtro filtro(boolean general, LocalDate desde, LocalDate hasta,
                                                         Set<Integer> subs, Set<Integer> cuentas, String titulo) {
        String t = titulo == null ? "" : titulo.trim();
        if (t.length() > 120) t = t.substring(0, 120);
        return new ReporteContableServicio.Filtro(general, desde, hasta, subs, cuentas, t);
    }

    /** El archivo para descargar, con su nombre. */
    private static ResponseEntity<byte[]> archivo(byte[] bytes, String nombre, String tipo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(bytes);
    }

    /** Si algo falta (por ejemplo, no se escogió ninguna categoría), una página corta que lo explica. */
    private static ResponseEntity<byte[]> aviso(String mensaje) {
        String seguro = mensaje == null ? "" : mensaje.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String html = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Reporte</title></head><body style=\"font-family:sans-serif;background:#1a0f2e;color:#fff;padding:32px\">"
                + "<h2>No se pudo sacar el reporte</h2><p>" + seguro + "</p>"
                + "<p><a href=\"javascript:history.back()\" style=\"color:#ff4d94\">← Volver</a></p></body></html>";
        return ResponseEntity.badRequest()
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(html.getBytes(StandardCharsets.UTF_8));
    }

    /** Los mismos datos del formulario como texto para los enlaces de descarga (sin el token). */
    private static String consulta(boolean general, LocalDate desde, LocalDate hasta, Set<Integer> subs,
                                   Set<Integer> cuentas, String titulo) {
        StringBuilder q = new StringBuilder();
        q.append("modo=").append(general ? "general" : "personalizado");
        q.append("&desde=").append(desde).append("&hasta=").append(hasta);
        if (!general) {
            for (Integer s : subs) q.append("&sub=").append(s);
            for (Integer c : cuentas) q.append("&cuenta=").append(c);
        }
        if (titulo != null && !titulo.isBlank()) q.append("&titulo=").append(URLEncoder.encode(titulo.trim(), StandardCharsets.UTF_8));
        return q.toString();
    }

    private static Set<Integer> enteros(List<String> valores) {
        Set<Integer> salida = new LinkedHashSet<>();
        if (valores == null) return salida;
        for (String v : valores) {
            if (v == null) continue;
            for (String parte : v.split(",")) {
                try { if (!parte.isBlank()) salida.add(Integer.valueOf(parte.trim())); } catch (NumberFormatException ignored) { }
            }
        }
        return salida;
    }

    private static LocalDate fecha(String s, LocalDate siNoHay) {
        try { return s == null || s.isBlank() ? siNoHay : LocalDate.parse(s.trim()); } catch (Exception e) { return siNoHay; }
    }

    private static String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }
}