package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.*;
import Colcones_Persinas.proyecto_express.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReporteExcelServicio {

    private final MaterialUsadoRepository materialUsadoRepository;
    private final PedidoRepository pedidoRepository;
    private final RolloTelaRepository rolloTelaRepository;
    private final RetazoTelaRepository retazoTelaRepository;
    private final InsumoRepository insumoRepository;
    private final PiezaInsumoRepository piezaInsumoRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_ARCHIVO = DateTimeFormatter.ofPattern("yyyy-MM");

    public ReporteExcelServicio(
            MaterialUsadoRepository materialUsadoRepository,
            PedidoRepository pedidoRepository,
            RolloTelaRepository rolloTelaRepository,
            RetazoTelaRepository retazoTelaRepository,
            InsumoRepository insumoRepository,
            PiezaInsumoRepository piezaInsumoRepository) {
        this.materialUsadoRepository = materialUsadoRepository;
        this.pedidoRepository = pedidoRepository;
        this.rolloTelaRepository = rolloTelaRepository;
        this.retazoTelaRepository = retazoTelaRepository;
        this.insumoRepository = insumoRepository;
        this.piezaInsumoRepository = piezaInsumoRepository;
    }

    // ═══════════════════════════════════════════════════════════════
    // PUNTO DE ENTRADA PRINCIPAL
    // ═══════════════════════════════════════════════════════════════

    /** Genera el Excel en memoria y lo devuelve como bytes (para descarga HTTP). */
    public byte[] generarReporteBytes(LocalDateTime desde, LocalDateTime hasta) throws IOException {
        try (Workbook wb = construirWorkbook(desde, hasta);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Genera el Excel y lo guarda en disco (para el job mensual). */
    public void generarReporteEnDisco(LocalDateTime desde, LocalDateTime hasta, String carpeta) throws IOException {
        Path dir = Paths.get(carpeta);
        Files.createDirectories(dir);
        String nombreArchivo = "reporte_" + FMT_ARCHIVO.format(desde) + ".xlsx";
        Path destino = dir.resolve(nombreArchivo);
        try (Workbook wb = construirWorkbook(desde, hasta);
             FileOutputStream out = new FileOutputStream(destino.toFile())) {
            wb.write(out);
        }
        System.out.println("[ReporteExcel] Guardado en: " + destino.toAbsolutePath());
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCCIÓN DEL WORKBOOK
    // ═══════════════════════════════════════════════════════════════

    private Workbook construirWorkbook(LocalDateTime desde, LocalDateTime hasta) {
        Workbook wb = new XSSFWorkbook();
        Estilos estilos = new Estilos(wb);

        List<MaterialUsado> materiales = materialUsadoRepository
                .findByFechaBetweenOrderByFechaAsc(desde, hasta);

        List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .filter(p -> {
                    LocalDateTime fc = p.getFechaCreacion();
                    return fc != null && !fc.isBefore(desde) && !fc.isAfter(hasta);
                })
                .sorted(Comparator.comparing(Pedido::getFechaCreacion))
                .collect(Collectors.toList());

        crearHojaResumen(wb, estilos, materiales, desde, hasta);
        crearHojaDetallePorPedido(wb, estilos, materiales, pedidos);
        crearHojaInventarioActual(wb, estilos);
        crearHojaPedidosPeriodo(wb, estilos, pedidos);

        return wb;
    }

    // ═══════════════════════════════════════════════════════════════
    // HOJA 1: RESUMEN GENERAL
    // ═══════════════════════════════════════════════════════════════

    private void crearHojaResumen(Workbook wb, Estilos e,
                                   List<MaterialUsado> materiales,
                                   LocalDateTime desde, LocalDateTime hasta) {
        Sheet s = wb.createSheet("Resumen");
        s.setColumnWidth(0, 28 * 256);
        s.setColumnWidth(1, 16 * 256);
        s.setColumnWidth(2, 14 * 256);
        s.setColumnWidth(3, 18 * 256);
        s.setColumnWidth(4, 18 * 256);

        // Título
        Row titulo = s.createRow(0);
        Cell cTitulo = titulo.createCell(0);
        cTitulo.setCellValue("REPORTE DE CONSUMO DE MATERIALES");
        cTitulo.setCellStyle(e.titulo);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        // Período
        Row periodo = s.createRow(1);
        Cell cPer = periodo.createCell(0);
        cPer.setCellValue("Período: " + FMT.format(desde) + "  →  " + FMT.format(hasta));
        cPer.setCellStyle(e.subtitulo);
        s.addMergedRegion(new CellRangeAddress(1, 1, 0, 4));

        s.createRow(2); // espacio

        // Encabezados
        String[] headers = {"Material", "Unidad", "Veces usado", "Total consumido", "Total m² (solo tela)"};
        Row hRow = s.createRow(3);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(e.encabezado);
        }

        // Agrupar por tipo de material
        Map<String, List<MaterialUsado>> agrupado = materiales.stream()
                .collect(Collectors.groupingBy(MaterialUsado::getTipoMaterial));

        int fila = 4;
        for (Map.Entry<String, List<MaterialUsado>> entry : agrupado.entrySet()
                .stream().sorted(Map.Entry.comparingByKey()).collect(Collectors.toList())) {

            String tipo = entry.getKey();
            List<MaterialUsado> lista = entry.getValue();

            boolean esTela = "TELA".equals(tipo) || "RETAZO".equals(tipo);
            boolean esPieza = !lista.isEmpty() && lista.get(0).getPiezaInsumoId() != null;

            String unidad = esTela ? "m²" : (esPieza ? "m" : "und.");
            double totalConsumido = lista.stream().mapToDouble(MaterialUsado::getMetrosUsados).sum();
            double totalM2 = esTela ? lista.stream()
                    .mapToDouble(m -> m.getMetrosCuadrados() != null ? m.getMetrosCuadrados() : 0.0).sum() : 0;

            Row r = s.createRow(fila++);
            r.setHeightInPoints(18);

            celda(r, 0, tipo, e.datoNormal);
            celda(r, 1, unidad, e.datoCentro);
            celdaNum(r, 2, lista.size(), e.datoCentro);
            celdaNum(r, 3, redondear(totalConsumido), e.datoNumero);
            if (esTela) celdaNum(r, 4, redondear(totalM2), e.datoNumero);
            else celda(r, 4, "—", e.datoCentro);
        }

        // Totales generales
        Row rTotales = s.createRow(fila + 1);
        celda(rTotales, 0, "TOTAL REGISTROS", e.total);
        celdaNum(rTotales, 2, materiales.size(), e.total);
    }

    // ═══════════════════════════════════════════════════════════════
    // HOJA 2: DETALLE POR PEDIDO
    // ═══════════════════════════════════════════════════════════════

    private void crearHojaDetallePorPedido(Workbook wb, Estilos e,
                                            List<MaterialUsado> materiales,
                                            List<Pedido> pedidos) {
        Sheet s = wb.createSheet("Detalle por Pedido");
        s.setColumnWidth(0, 10 * 256);
        s.setColumnWidth(1, 22 * 256);
        s.setColumnWidth(2, 22 * 256);
        s.setColumnWidth(3, 18 * 256);
        s.setColumnWidth(4, 20 * 256);
        s.setColumnWidth(5, 20 * 256);
        s.setColumnWidth(6, 14 * 256);
        s.setColumnWidth(7, 14 * 256);
        s.setColumnWidth(8, 14 * 256);
        s.setColumnWidth(9, 14 * 256);

        Row titulo = s.createRow(0);
        Cell cT = titulo.createCell(0);
        cT.setCellValue("DETALLE DE MATERIAL POR PEDIDO");
        cT.setCellStyle(e.titulo);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

        s.createRow(1);

        String[] headers = {"Pedido ID", "Distribuidor", "Cliente", "Descripción",
                "Material", "Fuente", "Cantidad", "Unidad", "Área m²", "Sobrante"};
        Row hRow = s.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(e.encabezado);
        }

        // Mapa pedido id → pedido para lookup rápido
        Map<Integer, Pedido> mapaPedidos = pedidos.stream()
                .collect(Collectors.toMap(Pedido::getId, p -> p, (a, b) -> a));

        int fila = 3;
        for (MaterialUsado m : materiales) {
            Pedido p = mapaPedidos.get(m.getPedidoId());
            boolean esTela = "TELA".equals(m.getTipoMaterial()) || "RETAZO".equals(m.getTipoMaterial());
            boolean esPieza = m.getPiezaInsumoId() != null;
            String unidad = esTela ? "m²" : (esPieza ? "m" : "und.");

            Row r = s.createRow(fila++);
            r.setHeightInPoints(17);
            celda(r, 0, String.valueOf(m.getPedidoId()), e.datoCentro);
            celda(r, 1, p != null ? p.getNombreDecorador()    : "—", e.datoNormal);
            celda(r, 2, p != null ? p.getNombreClienteFinal() : "—", e.datoNormal);
            celda(r, 3, p != null ? p.getDescripcion()        : "—", e.datoNormal);
            celda(r, 4, m.getTipoMaterial(), e.datoNormal);
            celda(r, 5, m.getFuenteDescripcion(), e.datoNormal);
            celdaNum(r, 6, m.getMetrosUsados(), e.datoNumero);
            celda(r, 7, unidad, e.datoCentro);
            if (m.getMetrosCuadrados() != null) celdaNum(r, 8, m.getMetrosCuadrados(), e.datoNumero);
            else celda(r, 8, "—", e.datoCentro);
            celdaNum(r, 9, m.getMetrosSobrantes(), e.datoNumero);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HOJA 3: INVENTARIO ACTUAL
    // ═══════════════════════════════════════════════════════════════

    private void crearHojaInventarioActual(Workbook wb, Estilos e) {
        Sheet s = wb.createSheet("Inventario Actual");

        Row titulo = s.createRow(0);
        Cell cT = titulo.createCell(0);
        cT.setCellValue("INVENTARIO ACTUAL — " + FMT.format(LocalDateTime.now()));
        cT.setCellStyle(e.titulo);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        // ── Sección: Rollos de tela ──────────────────────────────
        int fila = 2;
        Row secRollos = s.createRow(fila++);
        Cell cSec = secRollos.createCell(0);
        cSec.setCellValue("ROLLOS DE TELA");
        cSec.setCellStyle(e.seccion);
        s.addMergedRegion(new CellRangeAddress(fila - 1, fila - 1, 0, 5));

        String[] hRollos = {"ID", "Color", "Ancho (m)", "Largo inicial (m)", "Largo restante (m)", "Estado"};
        Row hrRow = s.createRow(fila++);
        anchos(s, new int[]{8, 14, 14, 20, 20, 12});
        for (int i = 0; i < hRollos.length; i++) {
            Cell c = hrRow.createCell(i);
            c.setCellValue(hRollos[i]);
            c.setCellStyle(e.encabezado);
        }

        List<RolloTela> rollos = rolloTelaRepository.findAllByOrderByColorAscAnchoAscLargoRestanteAsc();
        for (RolloTela r : rollos) {
            Row row = s.createRow(fila++);
            row.setHeightInPoints(17);
            celdaNum(row, 0, r.getId(), e.datoCentro);
            celda(row, 1, r.getColor(), e.datoNormal);
            celdaNum(row, 2, r.getAncho(), e.datoNumero);
            celdaNum(row, 3, r.getLargoInicial(), e.datoNumero);
            celdaNum(row, 4, r.getLargoRestante(), e.datoNumero);
            String estado = r.isAgotado() ? "Agotado" : (r.isRetazo() ? "Retazo (<3m)" : "Disponible");
            CellStyle estEstado = r.isAgotado() ? e.estadoMal : (r.isRetazo() ? e.estadoAlerta : e.estadoBien);
            celda(row, 5, estado, estEstado);
        }

        fila++;

        // ── Sección: Retazos de tela ─────────────────────────────
        Row secRetazos = s.createRow(fila++);
        Cell cSecR = secRetazos.createCell(0);
        cSecR.setCellValue("RETAZOS DE TELA");
        cSecR.setCellStyle(e.seccion);
        s.addMergedRegion(new CellRangeAddress(fila - 1, fila - 1, 0, 5));

        String[] hRetazos = {"ID", "Color", "Ancho (m)", "Alto (m)", "Área (m²)", ""};
        Row hrRetRow = s.createRow(fila++);
        for (int i = 0; i < hRetazos.length; i++) {
            Cell c = hrRetRow.createCell(i);
            c.setCellValue(hRetazos[i]);
            c.setCellStyle(e.encabezado);
        }

        List<RetazoTela> retazos = retazoTelaRepository.findAllByOrderByColorAscAnchoAscAltoAsc();
        for (RetazoTela r : retazos) {
            Row row = s.createRow(fila++);
            row.setHeightInPoints(17);
            celdaNum(row, 0, r.getId(), e.datoCentro);
            celda(row, 1, r.getColor(), e.datoNormal);
            celdaNum(row, 2, r.getAncho(), e.datoNumero);
            celdaNum(row, 3, r.getAlto(), e.datoNumero);
            celdaNum(row, 4, r.getArea(), e.datoNumero);
        }

        fila++;

        // ── Sección: Insumos ─────────────────────────────────────
        Row secInsumos = s.createRow(fila++);
        Cell cSecI = secInsumos.createCell(0);
        cSecI.setCellValue("INSUMOS");
        cSecI.setCellStyle(e.seccion);
        s.addMergedRegion(new CellRangeAddress(fila - 1, fila - 1, 0, 5));

        String[] hInsumos = {"ID", "Nombre", "Tipo", "Stock / Piezas", "Largo total restante (m)", "Estado"};
        Row hrInsRow = s.createRow(fila++);
        for (int i = 0; i < hInsumos.length; i++) {
            Cell c = hrInsRow.createCell(i);
            c.setCellValue(hInsumos[i]);
            c.setCellStyle(e.encabezado);
        }

        List<Insumo> insumos = insumoRepository.findAllByOrderByNombreAsc();
        for (Insumo ins : insumos) {
            Row row = s.createRow(fila++);
            row.setHeightInPoints(17);
            celdaNum(row, 0, ins.getId(), e.datoCentro);
            celda(row, 1, ins.getNombre(), e.datoNormal);

            if (Boolean.TRUE.equals(ins.getTieneMedida())) {
                List<PiezaInsumo> piezas = piezaInsumoRepository
                        .findByInsumoIdOrderByLargoRestanteAsc(ins.getId());
                long activas = piezas.stream().filter(p -> !p.isAgotada()).count();
                double totalM = piezas.stream().mapToDouble(PiezaInsumo::getLargoRestante).sum();
                celda(row, 2, "Por medida", e.datoNormal);
                celda(row, 3, activas + " pieza(s)", e.datoCentro);
                celdaNum(row, 4, redondear(totalM), e.datoNumero);
                celda(row, 5, activas == 0 ? "Sin stock" : "Disponible",
                        activas == 0 ? e.estadoMal : e.estadoBien);
            } else {
                int stock = ins.getStockUnidades() != null ? ins.getStockUnidades() : 0;
                celda(row, 2, "Por unidad", e.datoNormal);
                celdaNum(row, 3, stock, e.datoCentro);
                celda(row, 4, "—", e.datoCentro);
                celda(row, 5, stock == 0 ? "Sin stock" : "Disponible",
                        stock == 0 ? e.estadoMal : e.estadoBien);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HOJA 4: PEDIDOS DEL PERÍODO
    // ═══════════════════════════════════════════════════════════════

    private void crearHojaPedidosPeriodo(Workbook wb, Estilos e, List<Pedido> pedidos) {
        Sheet s = wb.createSheet("Pedidos del Período");
        s.setColumnWidth(0, 8  * 256);
        s.setColumnWidth(1, 22 * 256);
        s.setColumnWidth(2, 22 * 256);
        s.setColumnWidth(3, 18 * 256);
        s.setColumnWidth(4, 12 * 256);
        s.setColumnWidth(5, 12 * 256);
        s.setColumnWidth(6, 12 * 256);
        s.setColumnWidth(7, 18 * 256);
        s.setColumnWidth(8, 20 * 256);
        s.setColumnWidth(9, 22 * 256);

        Row titulo = s.createRow(0);
        Cell cT = titulo.createCell(0);
        cT.setCellValue("PEDIDOS DEL PERÍODO");
        cT.setCellStyle(e.titulo);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

        s.createRow(1);

        String[] headers = {"ID", "Distribuidor", "Cliente", "Descripción",
                "Ancho (m)", "Alto (m)", "Color", "Estado", "Fecha creación", "Tela / Rollo"};
        Row hRow = s.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(e.encabezado);
        }

        int fila = 3;
        for (Pedido p : pedidos) {
            Row r = s.createRow(fila++);
            r.setHeightInPoints(17);
            celdaNum(r, 0, p.getId(), e.datoCentro);
            celda(r, 1, p.getNombreDecorador(), e.datoNormal);
            celda(r, 2, p.getNombreClienteFinal(), e.datoNormal);
            celda(r, 3, p.getDescripcion(), e.datoNormal);
            celdaNum(r, 4, p.getAncho(), e.datoNumero);
            celdaNum(r, 5, p.getAltura(), e.datoNumero);
            celda(r, 6, p.getColorTelaDeseado(), e.datoCentro);

            String est = p.getEstado();
            CellStyle estEstado = "Listo para Despacho".equals(est) ? e.estadoBien
                    : ("En Proceso".equals(est) ? e.estadoAlerta : e.datoNormal);
            celda(r, 7, est, estEstado);
            celda(r, 8, p.getFechaCreacionFormateada(), e.datoCentro);
            celda(r, 9, p.getRolloParaCortar(), e.datoNormal);
        }

        // Fila de totales
        if (!pedidos.isEmpty()) {
            Row rTot = s.createRow(fila + 1);
            celda(rTot, 0, "TOTAL", e.total);
            celdaNum(rTot, 1, pedidos.size(), e.total);
            celda(rTot, 2, "pedido(s)", e.total);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ESTILOS CENTRALIZADOS
    // ═══════════════════════════════════════════════════════════════

    private static class Estilos {
        final CellStyle titulo, subtitulo, encabezado, seccion;
        final CellStyle datoNormal, datoCentro, datoNumero;
        final CellStyle estadoBien, estadoAlerta, estadoMal, total;

        Estilos(Workbook wb) {
            Font fTitulo = wb.createFont(); fTitulo.setBold(true); fTitulo.setFontHeightInPoints((short)14); fTitulo.setFontName("Arial");
            Font fEncab  = wb.createFont(); fEncab.setBold(true);  fEncab.setFontHeightInPoints((short)10);  fEncab.setFontName("Arial"); fEncab.setColor(IndexedColors.WHITE.getIndex());
            Font fSeccion= wb.createFont(); fSeccion.setBold(true); fSeccion.setFontHeightInPoints((short)11); fSeccion.setFontName("Arial"); fSeccion.setColor(IndexedColors.WHITE.getIndex());
            Font fNormal = wb.createFont(); fNormal.setFontHeightInPoints((short)10); fNormal.setFontName("Arial");
            Font fTotal  = wb.createFont(); fTotal.setBold(true);  fTotal.setFontHeightInPoints((short)10); fTotal.setFontName("Arial");

            titulo    = base(wb, fTitulo,  null, HorizontalAlignment.CENTER); titulo.setWrapText(false);
            subtitulo = base(wb, fNormal,  null, HorizontalAlignment.CENTER);

            encabezado = base(wb, fEncab, IndexedColors.DARK_TEAL, HorizontalAlignment.CENTER);
            seccion    = base(wb, fSeccion, IndexedColors.DARK_BLUE, HorizontalAlignment.LEFT);

            datoNormal  = base(wb, fNormal, null, HorizontalAlignment.LEFT);
            datoCentro  = base(wb, fNormal, null, HorizontalAlignment.CENTER);
            datoNumero  = base(wb, fNormal, null, HorizontalAlignment.RIGHT);
            datoNumero.setDataFormat(wb.createDataFormat().getFormat("#,##0.000"));

            estadoBien   = base(wb, fNormal, IndexedColors.LIGHT_GREEN,  HorizontalAlignment.CENTER);
            estadoAlerta = base(wb, fNormal, IndexedColors.LIGHT_YELLOW, HorizontalAlignment.CENTER);
            estadoMal    = base(wb, fNormal, IndexedColors.ROSE,         HorizontalAlignment.CENTER);

            total = base(wb, fTotal, IndexedColors.GREY_25_PERCENT, HorizontalAlignment.CENTER);
        }

    private CellStyle base(Workbook wb, Font font, IndexedColors bg, HorizontalAlignment align) {
        CellStyle cs = wb.createCellStyle();
        cs.setFont(font);
        cs.setAlignment(align);
        cs.setVerticalAlignment(VerticalAlignment.CENTER);
        cs.setBorderBottom(BorderStyle.THIN); 
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);   
        cs.setBorderRight(BorderStyle.THIN);
        
        // CORRECCIÓN AQUÍ:
        if (bg != null) {
            cs.setFillForegroundColor(bg.getIndex());
            cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return cs;
    }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void celda(Row r, int col, String val, CellStyle cs) {
        Cell c = r.createCell(col); c.setCellValue(val != null ? val : ""); c.setCellStyle(cs);
    }

    private void celdaNum(Row r, int col, double val, CellStyle cs) {
        Cell c = r.createCell(col); c.setCellValue(val); c.setCellStyle(cs);
    }

    private void celdaNum(Row r, int col, int val, CellStyle cs) {
        Cell c = r.createCell(col); c.setCellValue(val); c.setCellStyle(cs);
    }

    private void anchos(Sheet s, int[] anchos) {
        for (int i = 0; i < anchos.length; i++) s.setColumnWidth(i, anchos[i] * 256);
    }

    private double redondear(double v) { return Math.round(v * 1000.0) / 1000.0; }
}