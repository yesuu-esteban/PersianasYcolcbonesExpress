package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.servicio.ReporteExcelServicio;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/reportes")
public class ReporteControlador {

    private final ReporteExcelServicio reporteExcelServicio;
    private static final DateTimeFormatter FMT_ARCHIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    public ReporteControlador(ReporteExcelServicio reporteExcelServicio) {
        this.reporteExcelServicio = reporteExcelServicio;
    }

    // ─── Pantalla de reportes ─────────────────────────────────────
    @GetMapping
    public String verPantalla(Model model) {
        // Precarga el mes actual como valores por defecto en el formulario
        YearMonth mesActual = YearMonth.now();
        model.addAttribute("mesDefecto", mesActual.toString()); // "2026-06"
        return "reportes";
    }

    // ─── Descarga por MES ─────────────────────────────────────────
    @GetMapping("/descargar-mes")
    public ResponseEntity<byte[]> descargarPorMes(
            @RequestParam String mes) { // formato: "2026-06"
        try {
            YearMonth ym = YearMonth.parse(mes);
            LocalDateTime desde = ym.atDay(1).atStartOfDay();
            LocalDateTime hasta = ym.atEndOfMonth().atTime(23, 59, 59);

            byte[] datos = reporteExcelServicio.generarReporteBytes(desde, hasta);
            String nombre = "reporte_" + mes + ".xlsx";
            return respuestaExcel(datos, nombre);

        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ─── Descarga por RANGO LIBRE ─────────────────────────────────
    @GetMapping("/descargar-rango")
    public ResponseEntity<byte[]> descargarPorRango(
            @RequestParam String desde,
            @RequestParam String hasta) {
        try {
            LocalDateTime dtDesde = LocalDateTime.parse(desde + "T00:00:00");
            LocalDateTime dtHasta = LocalDateTime.parse(hasta + "T23:59:59");

            byte[] datos = reporteExcelServicio.generarReporteBytes(dtDesde, dtHasta);
            String nombre = "reporte_" + desde + "_a_" + hasta + ".xlsx";
            return respuestaExcel(datos, nombre);

        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ─── Helper ───────────────────────────────────────────────────
    private ResponseEntity<byte[]> respuestaExcel(byte[] datos, String nombreArchivo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreArchivo + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(datos.length)
                .body(datos);
    }
}