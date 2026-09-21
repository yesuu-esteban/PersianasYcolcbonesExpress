package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.ReciboCaja;
import Colcones_Persinas.proyecto_express.modelo.ReciboCajaItem;
import com.lowagie.text.*;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Base64;
import java.util.Locale;

@Service
public class ReciboPdfServicio {

    private static final NumberFormat FMT_MONEDA = NumberFormat.getInstance(new Locale("es", "CO"));

    /**
     * Mismo logo circular de Persianas Express embebido en recibo.html (la
     * vista en pantalla). Se repite aquí en base64 porque este PDF se genera
     * completamente aparte, "a mano" con OpenPDF — NO reutiliza recibo.html
     * ni ningún motor de renderizado HTML, así que sin este bloque el logo
     * nunca aparecía en el PDF compartido, aunque sí se viera bien en pantalla.
     */
    private static final String LOGO_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAALQAAAC0CAMAAAAKE/YAAAAA/1BMVEXWnp6en57dICMuLi/hW1naO0Lm3+GzHB1fX2ClaGh4hIfZQj3ig3x0gX7kfYLkvsCnhHu7v8c/QkFXOju1wr3Fw7q/0839/f0AAAD0AwPXCAv16Of12NXwuLXWKCryycjVFhfpmJbrpqXWNzcsAgLliIdPBQaQCw6vDBDR2NfXRknjNzjadXPjdnX449tuCArYVlXjFxjiZ2pzd3ixtrXIx8fiRkjropyIh4fZZ2nyw7xISEhVV1dnZ2e3xsQXFhcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB3rl0VAAAAQHRSTlP/////////////////////////////////////////////////////////////////////////////////////73leyQAAEzJJREFUeNrVXQt74rgOdUKA0nZmdvc+Mth5pymQEgjv9/T//6tr2QkkIXZCoS1X8+3OQIEeFPlIlhQZ/f4/FHTTT3t5eXkdj3u9PpU2/K/XHo9f6bP3CRrQ9vptp1smmKKn2O8KNMXbx7hbJRjfCjm6AeAy9U5ASp7G/RsAR9ch7vUzGsbq21KLAs8zTds2DMO2f5neKhhqS1/NXgjcuxI3ugYxzuBdRq5pEL1ciGG70VLN4f4G0C/jk47fNNcWwc1Bt92pf7Sl/vjlS0FTJae/21oGtQBngMeTa9WNPgK5nfxWNXYN/XIx3Di1lP6HrBt9wC4SPYXBRxCnuMPkWuEPWMmloF8TyNbU1K8Tc2Olxv25oBPI2L9CyRl1Bz6H3R5/HuiU45ou0W8jxAtT2/4c0C9jBhk/rW4FmcNu8o/tfQboxDIs95aQGWyXG0n79dagXzgxO8FIv72QgDNJ7+WmoF/5JdwY+ueIMcWXKBvVUjP7SMvTP088/wJl1wDNHSCejvTPlNGQ2Uj75SaguWlYpv7ZwpWNX28AesyteaR/vhgx9+vXgn7pscDI1b9GXKeWYctBv/S/yDSOAQkzkf7LFaD5Elwa+tcJN5EKry4D/YIZaxD9K4UwypYvR1RBGzjQv1qCStRi0K+wKBxX/3phy9F5/QBojtnTv0M8R65rJLONb8JMScSS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";

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

        // ── Encabezado: logo + nombre/slogan/direcciones, lado a lado
        // (misma composición que recibo.html: .logo-titulo → img + texto-empresa) ──
        PdfPTable tablaEncabezado = new PdfPTable(new float[]{1f, 4f});
        tablaEncabezado.setWidthPercentage(100);

        PdfPCell celdaLogo = new PdfPCell();
        celdaLogo.setBorder(Rectangle.NO_BORDER);
        celdaLogo.setPadding(0);
        celdaLogo.setVerticalAlignment(Element.ALIGN_TOP);
        try {
            byte[] bytesLogo = Base64.getDecoder().decode(LOGO_BASE64);
            Image imagenLogo = Image.getInstance(bytesLogo);
            imagenLogo.scaleToFit(48, 48);
            celdaLogo.addElement(imagenLogo);
        } catch (Exception e) {
            // Si por algún motivo el base64 fallara, el recibo sigue
            // generándose igual, solo sin el logo — nunca se rompe el PDF.
        }
        tablaEncabezado.addCell(celdaLogo);

        PdfPCell celdaTexto = new PdfPCell();
        celdaTexto.setBorder(Rectangle.NO_BORDER);
        celdaTexto.setPadding(0);
        celdaTexto.setPaddingLeft(8);
        celdaTexto.addElement(new Paragraph("PERSIANAS EXPRESS", fuenteTitulo));
        celdaTexto.addElement(new Paragraph("SOLUCIONES RÁPIDAS, CALIDAD ÚNICA", fuenteSlogan));
        celdaTexto.addElement(new Paragraph(
                "Sede Granada: Calle 8 #23-16 Local 6 | Sede Av. Bolívar: Cra 14 #12N-05\n" +
                "Celular: 3041354963 - 3122065950 - 3148660215", fuenteNormal));
        tablaEncabezado.addCell(celdaTexto);

        documento.add(tablaEncabezado);
        documento.add(new Paragraph(" "));

        Paragraph numeroRecibo = new Paragraph("RECIBO DE CAJA Nº " + recibo.getNumeroFormateado()
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

        // ── Firma (dibujada o escrita, ambas llegan como imagen PNG en base64) ──
        if (recibo.getFirma() != null && !recibo.getFirma().isBlank()) {
            try {
                String datosBase64 = recibo.getFirma().contains(",")
                        ? recibo.getFirma().substring(recibo.getFirma().indexOf(",") + 1)
                        : recibo.getFirma();
                byte[] bytesImagen = Base64.getDecoder().decode(datosBase64);
                Image imagenFirma = Image.getInstance(bytesImagen);
                imagenFirma.scaleToFit(160, 60);
                documento.add(imagenFirma);
            } catch (Exception e) {
                documento.add(new Paragraph("_______________________________", fuenteNormal));
            }
        } else {
            documento.add(new Paragraph("_______________________________", fuenteNormal));
        }
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