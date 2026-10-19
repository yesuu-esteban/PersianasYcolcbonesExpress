package Colcones_Persinas.proyecto_express.servicio.contabilidad;

import Colcones_Persinas.proyecto_express.servicio.contabilidad.ReporteContableServicio.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Date;

/**
 * Pasa un reporte de contabilidad a Excel (.xlsx) con dos hojas:
 *  - "Resumen": totales por sección, categoría y subcategoría (y en el general, saldos, por cobrar y por pagar).
 *  - "Detalle": cada movimiento, con filtros en los encabezados para buscar y ordenar.
 */
@Component
public class ReporteContableExcel {

    private static final String MONEDA = "\"$\"#,##0;[Red]-\"$\"#,##0";

    public byte[] generar(Reporte r) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            hojaResumen(wb, e, r);
            hojaDetalle(wb, e, r);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HOJA "RESUMEN"
    // ═══════════════════════════════════════════════════════════════

    private void hojaResumen(Workbook wb, Estilos e, Reporte r) {
        Sheet s = wb.createSheet("Resumen");
        s.setColumnWidth(0, 34 * 256);
        s.setColumnWidth(1, 34 * 256);
        s.setColumnWidth(2, 14 * 256);
        s.setColumnWidth(3, 18 * 256);

        int fila = encabezado(s, e, r, 3);

        if (r.isVacio()) {
            texto(s.createRow(fila++), 0, "No hay movimientos con lo que escogiste en este periodo.", e.normal);
        }

        for (Seccion sec : r.getSecciones()) {
            Row rs = s.createRow(fila++);
            texto(rs, 0, sec.getNombre().toUpperCase(), e.seccion);
            texto(rs, 1, "", e.seccion);
            texto(rs, 2, sec.getCantidad() + " mov.", e.seccion);
            plata(rs, 3, sec.getTotal(), e.seccionMoneda);

            Row h = s.createRow(fila++);
            String[] titulos = {"Categoría", "Subcategoría", "Movimientos", "Total"};
            for (int i = 0; i < titulos.length; i++) texto(h, i, titulos[i], e.encabezado);

            for (GrupoCat g : sec.getGrupos()) {
                Row rg = s.createRow(fila++);
                texto(rg, 0, g.getNombre(), e.negrita);
                texto(rg, 1, "", e.negrita);
                numero(rg, 2, g.getCantidad(), e.negritaCentro);
                plata(rg, 3, g.getTotal(), e.negritaMoneda);
                for (LineaSub l : g.getSubs()) {
                    Row rl = s.createRow(fila++);
                    texto(rl, 0, "", e.normal);
                    texto(rl, 1, l.getNombre(), e.normal);
                    numero(rl, 2, l.getCantidad(), e.centro);
                    plata(rl, 3, l.getTotal(), e.moneda);
                }
            }
            fila++;
        }

        // Cuadro final
        if (r.isGeneral()) {
            fila = tituloCuadro(s, e, fila, "RESULTADO DEL PERIODO");
            fila = lineaTotal(s, e, fila, "Ingresos", r.getTotalIngresos(), false);
            fila = lineaTotal(s, e, fila, "− Costos de venta", r.getTotalCostos(), false);
            fila = lineaTotal(s, e, fila, "= Utilidad bruta", r.getUtilidadBruta(), true);
            fila = lineaTotal(s, e, fila, "− Gastos", r.getTotalGastos(), false);
            fila = lineaTotal(s, e, fila, "= Utilidad neta", r.getUtilidadNeta(), true);
            if (r.getTotalOtros().signum() != 0) {
                fila = lineaTotal(s, e, fila, "− No son gasto (activos, préstamos, retiros)", r.getTotalOtros(), false);
                fila = lineaTotal(s, e, fila, "= Plata que quedó en el periodo", r.getResultadoEnCaja(), true);
            }
            fila++;
            fila = tituloCuadro(s, e, fila, "CONSOLIDADO DE HOY (no depende del periodo)");
            for (SaldoCuenta c : r.getSaldos()) fila = lineaTotal(s, e, fila, c.getNombre(), c.getSaldo(), false);
            fila = lineaTotal(s, e, fila, "TOTAL EN CUENTAS", r.getTotalCuentas(), true);
            fila = lineaTotal(s, e, fila, "+ Cuentas por cobrar", r.getPorCobrar(), false);
            fila = lineaTotal(s, e, fila, "− Cuentas por pagar", r.getPorPagar(), false);
            lineaTotal(s, e, fila, "= Total + por cobrar − por pagar", r.getPosicionNeta(), true);
        } else {
            fila = tituloCuadro(s, e, fila, "TOTALES");
            if (r.isHayIngresos()) fila = lineaTotal(s, e, fila, "Total ingresos", r.getTotalIngresos(), !r.isHayEgresos());
            if (r.isHayEgresos()) fila = lineaTotal(s, e, fila, "Total egresos", r.getTotalEgresos(), !r.isHayIngresos());
            if (r.isHayIngresos() && r.isHayEgresos()) fila = lineaTotal(s, e, fila, "= Ingresos − egresos", r.getNeto(), true);
            lineaTotal(s, e, fila, "Movimientos", null, false, r.getCantidad());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HOJA "DETALLE"
    // ═══════════════════════════════════════════════════════════════

    private void hojaDetalle(Workbook wb, Estilos e, Reporte r) {
        Sheet s = wb.createSheet("Detalle");
        int[] anchos = {12, 10, 28, 28, 18, 26, 40, 16};
        for (int i = 0; i < anchos.length; i++) s.setColumnWidth(i, anchos[i] * 256);

        int fila = encabezado(s, e, r, 7);
        int filaTitulos = fila;
        Row h = s.createRow(fila++);
        String[] titulos = {"Fecha", "Tipo", "Categoría", "Subcategoría", "Cuenta", "A quién / de quién", "Descripción", "Valor"};
        for (int i = 0; i < titulos.length; i++) texto(h, i, titulos[i], e.encabezado);

        for (FilaDetalle d : r.getDetalle()) {
            Row rd = s.createRow(fila++);
            Cell cf = rd.createCell(0);
            if (d.getFechaDia() != null) {
                cf.setCellValue(Date.from(d.getFechaDia().atStartOfDay(ZoneId.of("America/Bogota")).toInstant()));
                cf.setCellStyle(e.fecha);
            } else {
                cf.setCellValue(d.getFecha());
                cf.setCellStyle(e.centro);
            }
            texto(rd, 1, d.getTipo(), e.centro);
            texto(rd, 2, d.getCategoria(), e.normal);
            texto(rd, 3, d.getSubcategoria(), e.normal);
            texto(rd, 4, d.getCuenta(), e.normal);
            texto(rd, 5, d.getTercero(), e.normal);
            texto(rd, 6, d.getDescripcion(), e.normal);
            plata(rd, 7, d.getValor(), e.moneda);
        }
        if (!r.isVacio()) {
            s.setAutoFilter(new CellRangeAddress(filaTitulos, fila - 1, 0, titulos.length - 1));
            s.createFreezePane(0, filaTitulos + 1);
        }
        if (r.isHayIngresos() && r.isHayEgresos()) {
            Row rt = s.createRow(fila++);
            for (int i = 0; i < 7; i++) texto(rt, i, i == 0 ? "INGRESOS − EGRESOS" : "", e.total);
            plata(rt, 7, r.getNeto(), e.totalMoneda);
        } else if (!r.isVacio()) {
            Row rt = s.createRow(fila++);
            for (int i = 0; i < 7; i++) texto(rt, i, i == 0 ? "TOTAL" : "", e.total);
            plata(rt, 7, r.isHayIngresos() ? r.getTotalIngresos() : r.getTotalEgresos(), e.totalMoneda);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AYUDAS
    // ═══════════════════════════════════════════════════════════════

    /** Título, periodo, filtros y quién lo sacó. Devuelve la siguiente fila libre. */
    private int encabezado(Sheet s, Estilos e, Reporte r, int ultimaColumna) {
        int fila = 0;
        fila = lineaEncabezado(s, fila, "PERSIANAS EXPRESS · CONTABILIDAD", e.marca, ultimaColumna);
        fila = lineaEncabezado(s, fila, r.getTitulo(), e.titulo, ultimaColumna);
        fila = lineaEncabezado(s, fila, r.getPeriodoTexto(), e.subtitulo, ultimaColumna);
        fila = lineaEncabezado(s, fila, r.getFiltrosTexto(), e.subtitulo, ultimaColumna);
        if (!r.getAvisoInicio().isEmpty()) fila = lineaEncabezado(s, fila, r.getAvisoInicio(), e.subtitulo, ultimaColumna);
        fila = lineaEncabezado(s, fila, r.getGenerado(), e.pequeno, ultimaColumna);
        return fila + 1;
    }

    private int lineaEncabezado(Sheet s, int fila, String valor, CellStyle estilo, int ultimaColumna) {
        Row row = s.createRow(fila);
        Cell c = row.createCell(0);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
        if (ultimaColumna > 0) s.addMergedRegion(new CellRangeAddress(fila, fila, 0, ultimaColumna));
        return fila + 1;
    }

    private int tituloCuadro(Sheet s, Estilos e, int fila, String titulo) {
        Row r = s.createRow(fila);
        texto(r, 0, titulo, e.seccion);
        texto(r, 1, "", e.seccion);
        texto(r, 2, "", e.seccion);
        texto(r, 3, "", e.seccion);
        return fila + 1;
    }

    private int lineaTotal(Sheet s, Estilos e, int fila, String etiqueta, BigDecimal valor, boolean resaltar) {
        return lineaTotal(s, e, fila, etiqueta, valor, resaltar, null);
    }

    private int lineaTotal(Sheet s, Estilos e, int fila, String etiqueta, BigDecimal valor, boolean resaltar, Integer cantidad) {
        Row r = s.createRow(fila);
        texto(r, 0, etiqueta, resaltar ? e.total : e.normal);
        texto(r, 1, "", resaltar ? e.total : e.normal);
        texto(r, 2, "", resaltar ? e.total : e.normal);
        if (cantidad != null) numero(r, 3, cantidad, resaltar ? e.total : e.centro);
        else plata(r, 3, valor, resaltar ? e.totalMoneda : e.moneda);
        return fila + 1;
    }

    private static void texto(Row r, int col, String valor, CellStyle estilo) {
        Cell c = r.createCell(col);
        c.setCellValue(valor == null ? "" : valor);
        c.setCellStyle(estilo);
    }

    private static void plata(Row r, int col, BigDecimal valor, CellStyle estilo) {
        Cell c = r.createCell(col);
        c.setCellValue(valor == null ? 0 : valor.doubleValue());
        c.setCellStyle(estilo);
    }

    private static void numero(Row r, int col, int valor, CellStyle estilo) {
        Cell c = r.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    /** Los estilos del archivo (mismos colores del panel: morado y rosado). */
    private static class Estilos {
        final CellStyle marca, titulo, subtitulo, pequeno, encabezado, seccion, seccionMoneda;
        final CellStyle normal, centro, fecha, moneda, negrita, negritaCentro, negritaMoneda, total, totalMoneda;

        Estilos(Workbook wb) {
            DataFormat formato = wb.createDataFormat();
            Font fMarca = fuente(wb, 10, true, IndexedColors.PINK.getIndex());
            Font fTitulo = fuente(wb, 15, true, null);
            Font fNormal = fuente(wb, 10, false, null);
            Font fPequeno = fuente(wb, 9, false, IndexedColors.GREY_50_PERCENT.getIndex());
            Font fBlanca = fuente(wb, 10, true, IndexedColors.WHITE.getIndex());
            Font fNegrita = fuente(wb, 10, true, null);

            marca = estilo(wb, fMarca, null, HorizontalAlignment.LEFT, false);
            titulo = estilo(wb, fTitulo, null, HorizontalAlignment.LEFT, false);
            subtitulo = estilo(wb, fNormal, null, HorizontalAlignment.LEFT, false);
            pequeno = estilo(wb, fPequeno, null, HorizontalAlignment.LEFT, false);
            encabezado = estilo(wb, fBlanca, IndexedColors.VIOLET, HorizontalAlignment.CENTER, true);
            seccion = estilo(wb, fBlanca, IndexedColors.PLUM, HorizontalAlignment.LEFT, true);
            seccionMoneda = estilo(wb, fBlanca, IndexedColors.PLUM, HorizontalAlignment.RIGHT, true);
            seccionMoneda.setDataFormat(formato.getFormat(MONEDA));

            normal = estilo(wb, fNormal, null, HorizontalAlignment.LEFT, true);
            centro = estilo(wb, fNormal, null, HorizontalAlignment.CENTER, true);
            fecha = estilo(wb, fNormal, null, HorizontalAlignment.CENTER, true);
            fecha.setDataFormat(formato.getFormat("dd/mm/yyyy"));
            moneda = estilo(wb, fNormal, null, HorizontalAlignment.RIGHT, true);
            moneda.setDataFormat(formato.getFormat(MONEDA));

            negrita = estilo(wb, fNegrita, IndexedColors.GREY_25_PERCENT, HorizontalAlignment.LEFT, true);
            negritaCentro = estilo(wb, fNegrita, IndexedColors.GREY_25_PERCENT, HorizontalAlignment.CENTER, true);
            negritaMoneda = estilo(wb, fNegrita, IndexedColors.GREY_25_PERCENT, HorizontalAlignment.RIGHT, true);
            negritaMoneda.setDataFormat(formato.getFormat(MONEDA));

            total = estilo(wb, fNegrita, IndexedColors.LEMON_CHIFFON, HorizontalAlignment.LEFT, true);
            totalMoneda = estilo(wb, fNegrita, IndexedColors.LEMON_CHIFFON, HorizontalAlignment.RIGHT, true);
            totalMoneda.setDataFormat(formato.getFormat(MONEDA));
        }

        private static Font fuente(Workbook wb, int tamano, boolean negrita, Short color) {
            Font f = wb.createFont();
            f.setFontName("Arial");
            f.setFontHeightInPoints((short) tamano);
            f.setBold(negrita);
            if (color != null) f.setColor(color);
            return f;
        }

        private static CellStyle estilo(Workbook wb, Font f, IndexedColors fondo, HorizontalAlignment alineacion, boolean bordes) {
            CellStyle cs = wb.createCellStyle();
            cs.setFont(f);
            cs.setAlignment(alineacion);
            cs.setVerticalAlignment(VerticalAlignment.CENTER);
            if (bordes) {
                cs.setBorderBottom(BorderStyle.THIN);
                cs.setBorderTop(BorderStyle.THIN);
                cs.setBorderLeft(BorderStyle.THIN);
                cs.setBorderRight(BorderStyle.THIN);
            }
            if (fondo != null) {
                cs.setFillForegroundColor(fondo.getIndex());
                cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return cs;
        }
    }
}