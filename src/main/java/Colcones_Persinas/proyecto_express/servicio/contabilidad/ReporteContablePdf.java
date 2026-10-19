package Colcones_Persinas.proyecto_express.servicio.contabilidad;

import Colcones_Persinas.proyecto_express.servicio.contabilidad.ReporteContableServicio.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Pasa un reporte de contabilidad a PDF (hoja carta/A4 vertical), para imprimir o mandar por WhatsApp:
 * encabezado, totales por sección, categoría y subcategoría, el cuadro final y la lista de movimientos.
 */
@Component
public class ReporteContablePdf {

    private static final Color MORADO = new Color(42, 27, 66);
    private static final Color ROSADO = new Color(236, 21, 97);
    private static final Color GRIS = new Color(242, 240, 246);
    private static final Color AMARILLO = new Color(255, 248, 214);
    private static final Color ROJO = new Color(200, 40, 40);
    private static final Color VERDE = new Color(30, 130, 60);

    private final Font fMarca = new Font(Font.HELVETICA, 9, Font.BOLD, ROSADO);
    private final Font fTitulo = new Font(Font.HELVETICA, 16, Font.BOLD, MORADO);
    private final Font fNormal = new Font(Font.HELVETICA, 9);
    private final Font fPequeno = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
    private final Font fNegrita = new Font(Font.HELVETICA, 9, Font.BOLD);
    private final Font fBlanca = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private final Font fDetalle = new Font(Font.HELVETICA, 8);
    private final Font fDetalleRojo = new Font(Font.HELVETICA, 8, Font.NORMAL, ROJO);
    private final Font fDetalleVerde = new Font(Font.HELVETICA, 8, Font.NORMAL, VERDE);

    public byte[] generar(Reporte r) throws DocumentException {
        Document doc = new Document(PageSize.A4, 32, 32, 30, 30);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, salida);
        doc.open();

        // ── Encabezado ──
        doc.add(new Paragraph("PERSIANAS EXPRESS · CONTABILIDAD", fMarca));
        doc.add(new Paragraph(r.getTitulo(), fTitulo));
        doc.add(new Paragraph(r.getPeriodoTexto(), fNegrita));
        doc.add(new Paragraph(r.getFiltrosTexto(), fNormal));
        if (!r.getAvisoInicio().isEmpty()) doc.add(new Paragraph(r.getAvisoInicio(), fNormal));
        doc.add(new Paragraph(r.getGenerado(), fPequeno));
        doc.add(espacio());

        if (r.isVacio()) {
            doc.add(new Paragraph("No hay movimientos con lo que escogiste en este periodo.", fNegrita));
            doc.add(espacio());
        }

        // ── Totales por sección → categoría → subcategoría ──
        for (Seccion sec : r.getSecciones()) {
            PdfPTable t = new PdfPTable(new float[]{6f, 1.4f, 2.4f});
            t.setWidthPercentage(100);
            t.setSpacingAfter(10);
            t.addCell(celda(sec.getNombre().toUpperCase(), fBlanca, MORADO, Element.ALIGN_LEFT));
            t.addCell(celda(sec.getCantidad() + " mov.", fBlanca, MORADO, Element.ALIGN_CENTER));
            t.addCell(celda(pesos(sec.getTotal()), fBlanca, MORADO, Element.ALIGN_RIGHT));
            for (GrupoCat g : sec.getGrupos()) {
                t.addCell(celda(g.getNombre(), fNegrita, GRIS, Element.ALIGN_LEFT));
                t.addCell(celda(String.valueOf(g.getCantidad()), fNegrita, GRIS, Element.ALIGN_CENTER));
                t.addCell(celda(pesos(g.getTotal()), fNegrita, GRIS, Element.ALIGN_RIGHT));
                for (LineaSub l : g.getSubs()) {
                    PdfPCell nombre = celda(l.getNombre(), fNormal, null, Element.ALIGN_LEFT);
                    nombre.setPaddingLeft(16);
                    t.addCell(nombre);
                    t.addCell(celda(String.valueOf(l.getCantidad()), fNormal, null, Element.ALIGN_CENTER));
                    t.addCell(celda(pesos(l.getTotal()), fNormal, null, Element.ALIGN_RIGHT));
                }
            }
            doc.add(t);
        }

        // ── Cuadro final ──
        PdfPTable fin = new PdfPTable(new float[]{6f, 2.4f});
        fin.setWidthPercentage(70);
        fin.setHorizontalAlignment(Element.ALIGN_RIGHT);
        fin.setSpacingAfter(12);
        if (r.isGeneral()) {
            tituloCuadro(fin, "RESULTADO DEL PERIODO");
            fila(fin, "Ingresos", r.getTotalIngresos(), false);
            fila(fin, "- Costos de venta", r.getTotalCostos(), false);
            fila(fin, "= Utilidad bruta", r.getUtilidadBruta(), true);
            fila(fin, "- Gastos", r.getTotalGastos(), false);
            fila(fin, "= Utilidad neta", r.getUtilidadNeta(), true);
            if (r.getTotalOtros().signum() != 0) {
                fila(fin, "- No son gasto (activos, préstamos, retiros)", r.getTotalOtros(), false);
                fila(fin, "= Plata que quedó en el periodo", r.getResultadoEnCaja(), true);
            }
            doc.add(fin);

            PdfPTable hoy = new PdfPTable(new float[]{6f, 2.4f});
            hoy.setWidthPercentage(70);
            hoy.setHorizontalAlignment(Element.ALIGN_RIGHT);
            hoy.setSpacingAfter(12);
            tituloCuadro(hoy, "CONSOLIDADO DE HOY (no depende del periodo)");
            for (SaldoCuenta c : r.getSaldos()) fila(hoy, c.getNombre(), c.getSaldo(), false);
            fila(hoy, "TOTAL EN CUENTAS", r.getTotalCuentas(), true);
            fila(hoy, "+ Cuentas por cobrar", r.getPorCobrar(), false);
            fila(hoy, "- Cuentas por pagar", r.getPorPagar(), false);
            fila(hoy, "= Total + por cobrar - por pagar", r.getPosicionNeta(), true);
            doc.add(hoy);
        } else {
            tituloCuadro(fin, "TOTALES");
            if (r.isHayIngresos()) fila(fin, "Total ingresos", r.getTotalIngresos(), !r.isHayEgresos());
            if (r.isHayEgresos()) fila(fin, "Total egresos", r.getTotalEgresos(), !r.isHayIngresos());
            if (r.isHayIngresos() && r.isHayEgresos()) fila(fin, "= Ingresos - egresos", r.getNeto(), true);
            fin.addCell(celda("Movimientos", fNormal, null, Element.ALIGN_LEFT));
            fin.addCell(celda(String.valueOf(r.getCantidad()), fNormal, null, Element.ALIGN_RIGHT));
            doc.add(fin);
        }

        // ── Detalle ──
        if (!r.isVacio()) {
            Paragraph t = new Paragraph("Detalle de movimientos (" + r.getCantidad() + ")", fNegrita);
            t.setSpacingAfter(4);
            doc.add(t);
            PdfPTable d = new PdfPTable(new float[]{1.5f, 3.4f, 1.8f, 4f, 1.9f});
            d.setWidthPercentage(100);
            d.setHeaderRows(1);
            for (String h : new String[]{"Fecha", "Categoría / subcategoría", "Cuenta", "A quién · descripción", "Valor"}) {
                d.addCell(celda(h, fBlanca, MORADO, Element.ALIGN_CENTER));
            }
            for (FilaDetalle f : r.getDetalle()) {
                d.addCell(celda(f.getFecha(), fDetalle, null, Element.ALIGN_CENTER));
                d.addCell(celda(f.getSubcategoria() + "\n" + f.getCategoria(), fDetalle, null, Element.ALIGN_LEFT));
                d.addCell(celda(f.getCuenta(), fDetalle, null, Element.ALIGN_LEFT));
                String quien = f.getTercero();
                if (!f.getDescripcion().isEmpty()) quien = quien.isEmpty() ? f.getDescripcion() : quien + " · " + f.getDescripcion();
                d.addCell(celda(quien, fDetalle, null, Element.ALIGN_LEFT));
                Font fv = f.getValor().signum() < 0 ? fDetalleRojo : (f.isDeIngreso() ? fDetalleVerde : fDetalle);
                d.addCell(celda(pesos(f.getValor()), fv, null, Element.ALIGN_RIGHT));
            }
            doc.add(d);
            Paragraph nota = new Paragraph("En verde, los ingresos. Un valor negativo es una devolución (resta en su categoría). "
                    + "Los traslados entre cuentas no salen aquí porque no son ingreso ni gasto.", fPequeno);
            nota.setSpacingBefore(6);
            doc.add(nota);
        }

        doc.close();
        return salida.toByteArray();
    }

    private void tituloCuadro(PdfPTable t, String titulo) {
        PdfPCell c = celda(titulo, fBlanca, MORADO, Element.ALIGN_LEFT);
        c.setColspan(2);
        t.addCell(c);
    }

    private void fila(PdfPTable t, String etiqueta, BigDecimal valor, boolean resaltar) {
        Color fondo = resaltar ? AMARILLO : null;
        Font f = resaltar ? fNegrita : fNormal;
        t.addCell(celda(etiqueta, f, fondo, Element.ALIGN_LEFT));
        t.addCell(celda(pesos(valor), f, fondo, Element.ALIGN_RIGHT));
    }

    private static PdfPCell celda(String texto, Font fuente, Color fondo, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        c.setBorderColor(new Color(220, 214, 230));
        if (fondo != null) c.setBackgroundColor(fondo);
        return c;
    }

    private static Paragraph espacio() {
        return new Paragraph(" ");
    }

    private static String pesos(BigDecimal v) {
        BigDecimal valor = v == null ? BigDecimal.ZERO : v;
        String numero = NumberFormat.getIntegerInstance(new Locale("es", "CO")).format(valor.abs());
        return (valor.signum() < 0 ? "-$" : "$") + numero;
    }
}