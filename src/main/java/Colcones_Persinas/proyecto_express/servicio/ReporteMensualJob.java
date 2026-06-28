package Colcones_Persinas.proyecto_express.servicio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Job que se ejecuta automáticamente el primer día de cada mes a las 2:00 AM
 * y guarda en disco el reporte Excel del mes anterior.
 *
 * La carpeta de destino se configura en application.properties:
 *   reportes.carpeta=/ruta/a/reportes
 *
 * Si no se configura, usa "./reportes" relativo al directorio de la app.
 */
@Component
public class ReporteMensualJob {

    private final ReporteExcelServicio reporteExcelServicio;

    @Value("${reportes.carpeta:./reportes}")
    private String carpetaReportes;

    public ReporteMensualJob(ReporteExcelServicio reporteExcelServicio) {
        this.reporteExcelServicio = reporteExcelServicio;
    }

    /**
     * Cron: segundo=0, minuto=0, hora=2, día=1, cualquier mes, cualquier año.
     * Es decir: el día 1 de cada mes a las 02:00:00 AM.
     */
    @Scheduled(cron = "0 0 2 1 * *")
    public void generarReporteMesAnterior() {
        // Tomamos el mes ANTERIOR al actual
        YearMonth mesAnterior = YearMonth.now().minusMonths(1);
        LocalDateTime desde = mesAnterior.atDay(1).atStartOfDay();
        LocalDateTime hasta = mesAnterior.atEndOfMonth().atTime(23, 59, 59);

        try {
            reporteExcelServicio.generarReporteEnDisco(desde, hasta, carpetaReportes);
            System.out.println("[ReporteMensual] ✓ Reporte de " + mesAnterior + " generado correctamente.");
        } catch (Exception e) {
            System.err.println("[ReporteMensual] ✗ Error al generar reporte de " + mesAnterior + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}