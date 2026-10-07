package Colcones_Persinas.proyecto_express.servicio.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.ItemOrdenTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pago a cuotas con Addi.
 *
 * Cómo funciona:
 *  1. Con el Client ID y el Client Secret se le pide a Addi un permiso temporal (token).
 *  2. Se le envía a Addi la compra y los datos del cliente. Addi contesta con la dirección
 *     de su página, y a esa dirección se manda al cliente.
 *  3. Cuando Addi decide (aprobado, rechazado, abandonado), avisa a /tienda/addi/aviso.
 *     Ese aviso trae un usuario y una clave que solo Addi y esta tienda conocen.
 *
 * Se configura con variables (en Railway → Variables), sin tocar application.properties:
 *   ADDI_CLIENT_ID      el Client ID que entrega Addi
 *   ADDI_CLIENT_SECRET  el Client Secret que entrega Addi
 *   ADDI_MODO           "pruebas" (por defecto) o "produccion"
 * Si faltan las dos primeras, Addi queda apagado y la tienda no muestra el botón.
 */
@Service
public class AddiServicio {

    /** Error al hablar con Addi. El mensaje ya está escrito para mostrárselo al cliente. */
    public static class AddiException extends RuntimeException {
        public AddiException(String mensaje) { super(mensaje); }
    }

    private static final String MSG_NO_DISPONIBLE =
            "Addi no pudo iniciar tu solicitud en este momento. Inténtalo de nuevo o paga con otro medio.";
    private static final String MSG_CEDULA =
            "Addi no reconoció tu número de cédula. Revísalo e inténtalo de nuevo, o paga con otro medio.";

    private final String clientId;
    private final String clientSecret;
    private final boolean produccion;
    private final String urlToken;
    private final String audiencia;
    private final String urlApi;
    private final ObjectMapper json;
    private final HttpClient http;

    private String token;
    private Instant tokenVence = Instant.EPOCH;
    private String[] credencialesAviso;                       // [usuario, clave] con que Addi firma sus avisos
    private Instant credencialesConsultadas = Instant.EPOCH;

    @Autowired
    public AddiServicio(@Value("${ADDI_CLIENT_ID:}") String clientId,
                        @Value("${ADDI_CLIENT_SECRET:}") String clientSecret,
                        @Value("${ADDI_MODO:pruebas}") String modo,
                        ObjectMapper json) {
        this(clientId, clientSecret, esProduccion(modo),
                esProduccion(modo) ? "https://auth.addi.com/oauth/token" : "https://auth.addi-staging.com/oauth/token",
                esProduccion(modo) ? "https://api.addi.com" : "https://api.staging.addi.com",
                esProduccion(modo) ? "https://api.addi.com/v1/" : "https://api.addi-staging.com/v1/",
                json);
    }

    AddiServicio(String clientId, String clientSecret, boolean produccion,
                 String urlToken, String audiencia, String urlApi, ObjectMapper json) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.produccion = produccion;
        this.urlToken = urlToken;
        this.audiencia = audiencia;
        this.urlApi = urlApi;
        this.json = json;
        // NEVER: Addi contesta con una redirección a su página; no se sigue, se le entrega al cliente
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private static boolean esProduccion(String modo) {
        return modo != null && modo.trim().toLowerCase(Locale.ROOT).startsWith("prod");
    }

    public boolean isConfigurado() { return !clientId.isEmpty() && !clientSecret.isEmpty(); }
    public boolean isModoPruebas() { return !produccion; }

    // ═══════════════════════════════════════════════════════════════
    // ENVIAR LA COMPRA A ADDI
    // ═══════════════════════════════════════════════════════════════

    /**
     * Crea la solicitud en Addi y devuelve la dirección de Addi a la que hay que mandar al cliente.
     *
     * @param urlAviso   dirección pública de esta tienda donde Addi avisa el resultado
     * @param urlRegreso dirección a la que Addi devuelve al cliente al terminar
     * @param urlLogo    logo de la tienda que Addi muestra en su página
     */
    public String crearSolicitud(OrdenTienda orden, String urlAviso, String urlRegreso, String urlLogo) {
        if (!isConfigurado()) throw new AddiException(MSG_NO_DISPONIBLE);
        String cuerpo;
        try {
            cuerpo = json.writeValueAsString(cuerpoSolicitud(orden, urlAviso, urlRegreso, urlLogo));
        } catch (IOException e) {
            throw new AddiException(MSG_NO_DISPONIBLE);
        }

        for (int intento = 0; intento < 2; intento++) {
            HttpResponse<String> r = enviar(HttpRequest.newBuilder(URI.create(urlApi + "online-applications"))
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token(intento > 0))
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                    .build());

            int codigo = r.statusCode();
            String destino = r.headers().firstValue("Location").orElse("");
            if (codigo >= 300 && codigo < 400 && !destino.isBlank()) {
                return URI.create(urlApi).resolve(destino.trim()).toString();
            }
            if ((codigo == 401 || codigo == 403) && intento == 0) continue;   // permiso vencido: se pide otro y se repite

            System.err.println("[Addi] No se pudo crear la solicitud de " + orden.getReferencia()
                    + ". Código " + codigo + ". Respuesta: " + recortar(r.body()));
            String respuesta = r.body() == null ? "" : r.body().toLowerCase(Locale.ROOT);
            throw new AddiException(respuesta.contains("000-009") || respuesta.contains("documento") ? MSG_CEDULA : MSG_NO_DISPONIBLE);
        }
        throw new AddiException(MSG_NO_DISPONIBLE);
    }

    /** Lo que se le envía a Addi (mismo formato que usa el conector oficial de Addi). */
    static Map<String, Object> cuerpoSolicitud(OrdenTienda orden, String urlAviso, String urlRegreso, String urlLogo) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("lineOne", texto(orden.getDireccion()));
        direccion.put("city", texto(orden.getCiudad()));
        direccion.put("country", "CO");

        String[] nombre = nombreYApellido(orden.getNombreCliente());
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("idType", "CC");
        cliente.put("idNumber", texto(orden.getCedula()).replaceAll("\\D", ""));
        cliente.put("firstName", nombre[0]);
        cliente.put("lastName", nombre[1]);
        cliente.put("email", texto(orden.getEmail()));
        cliente.put("cellphone", celular(orden.getTelefono()));
        cliente.put("cellphoneCountryCode", "+57");
        cliente.put("address", direccion);

        List<Map<String, Object>> productos = new ArrayList<>();
        for (ItemOrdenTienda it : orden.getItems()) {
            String detalle = it.getDetalle();
            String nombreProducto = texto(it.getProductoNombre()) + (detalle == null || detalle.isBlank() ? "" : " - " + detalle);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("sku", it.getProductoId() != null ? String.valueOf(it.getProductoId()) : "0");
            p.put("name", nombreProducto.length() > 250 ? nombreProducto.substring(0, 250) : nombreProducto);
            p.put("quantity", it.getCantidad());
            p.put("unitPrice", it.getPrecioUnitario().setScale(0, RoundingMode.HALF_UP).longValue());
            p.put("tax", 0);
            p.put("pictureUrl", urlLogo);
            p.put("category", "hogar");
            productos.add(p);
        }

        Map<String, Object> regreso = new LinkedHashMap<>();
        regreso.put("logoUrl", urlLogo);
        regreso.put("callbackUrl", urlAviso);
        regreso.put("redirectionUrl", urlRegreso);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("orderId", orden.getReferencia());
        cuerpo.put("totalAmount", monto(orden.getTotal()));
        cuerpo.put("shippingAmount", "0.0");
        cuerpo.put("totalTaxesAmount", "0.0");
        cuerpo.put("currency", "COP");
        cuerpo.put("items", productos);
        cuerpo.put("client", cliente);
        cuerpo.put("shippingAddress", direccion);
        cuerpo.put("allyUrlRedirection", regreso);
        return cuerpo;
    }

    // ═══════════════════════════════════════════════════════════════
    // AVISO DE ADDI (resultado de la solicitud)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Revisa que el aviso venga de verdad de Addi: debe traer el usuario y la clave que Addi
     * le asignó a este comercio. Si no coinciden, se le piden de nuevo a Addi una vez
     * (por si los cambió) y se vuelve a comparar.
     */
    public boolean avisoAutorizado(String encabezadoAuthorization) {
        if (!isConfigurado() || encabezadoAuthorization == null) return false;
        String e = encabezadoAuthorization.trim();
        if (e.length() < 7 || !e.regionMatches(true, 0, "Basic ", 0, 6)) return false;

        String usuario, clave;
        try {
            String par = new String(Base64.getDecoder().decode(e.substring(6).trim()), StandardCharsets.UTF_8);
            int corte = par.indexOf(':');
            if (corte < 0) return false;
            usuario = par.substring(0, corte);
            clave = par.substring(corte + 1);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return coincide(credencialesAviso(false), usuario, clave) || coincide(credencialesAviso(true), usuario, clave);
    }

    private static boolean coincide(String[] guardadas, String usuario, String clave) {
        if (guardadas == null) return false;
        boolean u = MessageDigest.isEqual(guardadas[0].getBytes(StandardCharsets.UTF_8), usuario.getBytes(StandardCharsets.UTF_8));
        boolean c = MessageDigest.isEqual(guardadas[1].getBytes(StandardCharsets.UTF_8), clave.getBytes(StandardCharsets.UTF_8));
        return u && c;
    }

    private synchronized String[] credencialesAviso(boolean volverAPedir) {
        if (!volverAPedir && credencialesAviso != null) return credencialesAviso;
        // Aunque lleguen muchos avisos con datos malos, a Addi se le pregunta máximo una vez por minuto
        if (Instant.now().isBefore(credencialesConsultadas.plusSeconds(60))) return credencialesAviso;
        credencialesConsultadas = Instant.now();
        try {
            for (int intento = 0; intento < 2; intento++) {
                HttpResponse<String> r = enviar(HttpRequest.newBuilder(URI.create(urlApi + "online-applications/callback-credentials"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + token(intento > 0))
                        .GET().build());
                if ((r.statusCode() == 401 || r.statusCode() == 403) && intento == 0) continue;
                if (r.statusCode() != 200) {
                    System.err.println("[Addi] No se pudieron consultar las credenciales del aviso. Código " + r.statusCode() + ".");
                    break;
                }
                JsonNode n = json.readTree(r.body());
                String usuario = n.path("user").asText("");
                String clave = n.path("password").asText("");
                if (!usuario.isEmpty() && !clave.isEmpty()) credencialesAviso = new String[]{usuario, clave};
                break;
            }
        } catch (AddiException | IOException ex) {
            System.err.println("[Addi] No se pudieron consultar las credenciales del aviso: " + ex.getMessage());
        }
        return credencialesAviso;
    }

    /** Pasa el estado que usa Addi al que ya entiende TiendaServicio.aplicarPago (los de Wompi). */
    public static String estadoComoWompi(String estadoAddi) {
        switch (estadoAddi == null ? "" : estadoAddi.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVED":
                return "APPROVED";
            case "REJECTED":
            case "DECLINED":
                return "DECLINED";
            case "ABANDONED":
                return "VOIDED";
            case "INTERNAL_ERROR":
                return "ERROR";
            default:
                return "PENDING";
        }
    }

    /**
     * Valor aprobado por Addi, en centavos. Si el aviso no trae el valor, se usa el de la
     * compra (el aviso ya se comprobó que viene de Addi y es de esa compra).
     */
    public static long centavos(JsonNode valorAprobado, long siNoViene) {
        if (valorAprobado == null || valorAprobado.isMissingNode() || valorAprobado.isNull()) return siNoViene;
        try {
            return new BigDecimal(valorAprobado.asText().trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return siNoViene;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PERMISO (TOKEN) Y UTILIDADES
    // ═══════════════════════════════════════════════════════════════

    /** Permiso temporal de Addi. Se guarda y se reutiliza hasta poco antes de que venza. */
    private synchronized String token(boolean pedirNuevo) {
        if (!pedirNuevo && token != null && Instant.now().isBefore(tokenVence)) return token;

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("audience", audiencia);
        cuerpo.put("grant_type", "client_credentials");
        cuerpo.put("client_id", clientId);
        cuerpo.put("client_secret", clientSecret);
        try {
            HttpResponse<String> r = enviar(HttpRequest.newBuilder(URI.create(urlToken))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                    .build());
            if (r.statusCode() != 200) {
                System.err.println("[Addi] Addi no aceptó el Client ID o el Client Secret (modo "
                        + (produccion ? "producción" : "pruebas") + "). Código " + r.statusCode() + ". Respuesta: " + recortar(r.body()));
                throw new AddiException(MSG_NO_DISPONIBLE);
            }
            JsonNode n = json.readTree(r.body());
            String nuevo = n.path("access_token").asText("");
            if (nuevo.isEmpty()) throw new AddiException(MSG_NO_DISPONIBLE);
            token = nuevo;
            tokenVence = Instant.now().plusSeconds(Math.max(60, n.path("expires_in").asLong(3600) - 300));
            return token;
        } catch (IOException e) {
            throw new AddiException(MSG_NO_DISPONIBLE);
        }
    }

    private HttpResponse<String> enviar(HttpRequest peticion) {
        try {
            return http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[Addi] Sin respuesta de Addi: " + e.getMessage());
            throw new AddiException(MSG_NO_DISPONIBLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AddiException(MSG_NO_DISPONIBLE);
        }
    }

    /** "María Fernanda Gómez Restrepo" → ["María Fernanda", "Gómez Restrepo"]. Addi pide nombre y apellido aparte. */
    static String[] nombreYApellido(String completo) {
        String[] p = texto(completo).split("\\s+");
        if (p.length == 1) return new String[]{p[0], p[0]};
        int nombres = p.length >= 4 ? 2 : 1;
        return new String[]{String.join(" ", List.of(p).subList(0, nombres)), String.join(" ", List.of(p).subList(nombres, p.length))};
    }

    /** Celular con solo los 10 números: "+57 312 304 3450" → "3123043450". */
    static String celular(String telefono) {
        String n = texto(telefono).replaceAll("\\D", "");
        return n.length() > 10 && n.startsWith("57") ? n.substring(n.length() - 10) : n;
    }

    /** 348200 → "348200.0" (así escribe los valores el conector oficial de Addi). */
    static String monto(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String texto(String s) { return s == null ? "" : s.trim(); }

    private static String recortar(String s) {
        if (s == null) return "";
        String limpio = s.replaceAll("\\s+", " ").trim();
        return limpio.length() > 400 ? limpio.substring(0, 400) + "…" : limpio;
    }
}