package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.ReciboCaja;
import Colcones_Persinas.proyecto_express.modelo.ReciboCajaItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class ReciboPdfServicio {

    private static final NumberFormat FMT_MONEDA = NumberFormat.getInstance(new Locale("es", "CO"));

    public byte[] generarPdf(ReciboCaja recibo) throws DocumentException, IOException {
        Document documento = new Document(PageSize.A5, 24, 24, 24, 24);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfWriter.getInstance(documento, salida);
        documento.open();

        Font fuenteTitulo = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(27, 77, 62));
        Font fuenteSlogan = new Font(Font.HELVETICA, 8, Font.BOLD);
        Font fuenteNormal = new Font(Font.HELVETICA, 9);
        Font fuenteNegrita = new Font(Font.HELVETICA, 9, Font.BOLD);
        Font fuenteEncabezadoTabla = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);

        // ── Encabezado ──
        Paragraph titulo = new Paragraph("PERSIANAS Y COLCHONES EXPRESS", fuenteTitulo);
        documento.add(titulo);
        documento.add(new Paragraph("SOLUCIONES RÁPIDAS, CALIDAD ÚNICA", fuenteSlogan));
        documento.add(new Paragraph(
                "Sede Granada: Calle 8 #23-16 Local 6 | Sede Av. Bolívar: Cra 14 #12N-05\n" +
                "Celular: 3041354963 - 3122065950 - 3148660215", fuenteNormal));
        documento.add(new Paragraph(" "));

        Paragraph numeroRecibo = new Paragraph("RECIBO DE CAJA Nº " + recibo.getNumero()
                + "  ·  Origen: " + recibo.getOrigen(), fuenteNegrita);
        documento.add(numeroRecibo);
        documento.add(new Paragraph(" "));

        // ── Datos del cliente ──
        PdfPTable tablaCliente = new PdfPTable(2);
        tablaCliente.setWidthPercentage(100);
        agregarCelda(tablaCliente, "Cliente: " + valorOGuion(recibo.getCliente()), fuenteNormal);
        agregarCelda(tablaCliente, "Fecha: " + recibo.getFechaFormateada(), fuenteNormal);
        agregarCelda(tablaCliente, "Dirección: " + valorOGuion(recibo.getDireccion()), fuenteNormal);
        agregarCelda(tablaCliente, "Cédula: " + valorOGuion(recibo.getCedula()), fuenteNormal);
        agregarCelda(tablaCliente, "Teléfono: " + valorOGuion(recibo.getTelefono()), fuenteNormal);
        agregarCelda(tablaCliente, "Generado por: " + valorOGuion(recibo.getCreadoPor()), fuenteNormal);
        documento.add(tablaCliente);
        documento.add(new Paragraph(" "));

        // ── Tabla de productos ──
        PdfPTable tablaProductos = new PdfPTable(new float[]{1.5f, 4f, 2f, 2f});
        tablaProductos.setWidthPercentage(100);
        agregarEncabezado(tablaProductos, "CANT.", fuenteEncabezadoTabla);
        agregarEncabezado(tablaProductos, "DESCRIPCIÓN", fuenteEncabezadoTabla);
        agregarEncabezado(tablaProductos, "VR. UNIT.", fuenteEncabezadoTabla);
        agregarEncabezado(tablaProductos, "VR. TOTAL", fuenteEncabezadoTabla);

        for (ReciboCajaItem item : recibo.getItems()) {
            agregarCeldaTabla(tablaProductos, String.valueOf(item.getCantidad()), fuenteNormal, Element.ALIGN_CENTER);
            agregarCeldaTabla(tablaProductos, item.getNombre(), fuenteNormal, Element.ALIGN_LEFT);
            agregarCeldaTabla(tablaProductos, "$" + FMT_MONEDA.format(item.getPrecio()), fuenteNormal, Element.ALIGN_RIGHT);
            agregarCeldaTabla(tablaProductos, "$" + FMT_MONEDA.format(item.getTotalLinea()), fuenteNormal, Element.ALIGN_RIGHT);
        }
        documento.add(tablaProductos);
        documento.add(new Paragraph(" "));

        // ── Totales ──
        PdfPTable tablaTotales = new PdfPTable(2);
        tablaTotales.setWidthPercentage(60);
        tablaTotales.setHorizontalAlignment(Element.ALIGN_RIGHT);
        agregarFilaTotal(tablaTotales, "SUBTOTAL", recibo.getTotal(), fuenteNormal, fuenteNegrita);
        agregarFilaTotal(tablaTotales, "ABONO", recibo.getAbono(), fuenteNormal, fuenteNegrita);
        agregarFilaTotal(tablaTotales, "SALDO", recibo.getSaldo(), fuenteNormal, fuenteNegrita);
        documento.add(tablaTotales);

        documento.add(new Paragraph(" "));
        documento.add(new Paragraph(" "));
        documento.add(new Paragraph("_______________________________", fuenteNormal));
        documento.add(new Paragraph("FIRMA Y SELLO", fuenteNormal));
        documento.add(new Paragraph(" "));

        Font fuentePie = new Font(Font.HELVETICA, 7, Font.ITALIC);
        documento.add(new Paragraph(
                "Este documento se asimila a una letra de cambio para todos los efectos legales. "
                        + "Artículo N° 774 del Código de Comercio.", fuentePie));

        documento.close();
        return salida.toByteArray();
    }

    private String valorOGuion(String texto) {
        return (texto == null || texto.isBlank()) ? "—" : texto;
    }

    private void agregarCelda(PdfPTable tabla, String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(3);
        tabla.addCell(celda);
    }

    private void agregarEncabezado(PdfPTable tabla, String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBackgroundColor(new Color(51, 51, 51));
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda.setPadding(5);
        tabla.addCell(celda);
    }

    private void agregarCeldaTabla(PdfPTable tabla, String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(4);
        tabla.addCell(celda);
    }

    private void agregarFilaTotal(PdfPTable tabla, String etiqueta, BigDecimal valor, Font fuenteEtiqueta, Font fuenteValor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Phrase(etiqueta, fuenteEtiqueta));
        celdaEtiqueta.setBackgroundColor(new Color(242, 242, 242));
        celdaEtiqueta.setPadding(5);
        tabla.addCell(celdaEtiqueta);

        PdfPCell celdaValor = new PdfPCell(new Phrase("$" + FMT_MONEDA.format(valor), fuenteValor));
        celdaValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celdaValor.setPadding(5);
        tabla.addCell(celdaValor);
    }
}