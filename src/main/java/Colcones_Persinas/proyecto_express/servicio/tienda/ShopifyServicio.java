package Colcones_Persinas.proyecto_express.servicio.tienda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conexión con la API oficial de Shopify (GraphQL Admin API).
 *
 * Se usa una app creada en el Dev Dashboard de Shopify e instalada en la tienda. Con su
 * Client ID y Client secret se pide un token que dura 24 horas (client credentials grant);
 * el token se guarda en memoria y se renueva solo antes de vencerse.
 *
 * Permisos (scopes) de la app: read_products, write_products, read_inventory, read_orders, write_orders,
 * read_publications, write_publications. Y acceso a los datos protegidos del cliente (nombre, correo,
 * teléfono y dirección).
 *
 * Variables (solo en Railway, nunca en el código ni en el chat):
 *   SHOPIFY_TIENDA          el nombre de la tienda: "mitienda" o "mitienda.myshopify.com"
 *   SHOPIFY_CLIENT_ID       el Client ID de la app
 *   SHOPIFY_CLIENT_SECRET   el Client secret de la app
 *   SHOPIFY_API_VERSION     opcional (por defecto 2026-07)
 */
@Service
public class ShopifyServicio {

    /** Error al hablar con Shopify, con un mensaje que se puede mostrar en pantalla. */
    public static class ShopifyError extends RuntimeException {
        public ShopifyError(String mensaje) { super(mensaje); }
    }

    private final String tienda;
    private final String clientId;
    private final String clientSecret;
    private final String version;
    private final String urlBase;
    private final ObjectMapper json;
    private final HttpClient http;

    private String token;
    private Instant tokenVence = Instant.EPOCH;

    public ShopifyServicio(@Value("${SHOPIFY_TIENDA:}") String tienda,
                           @Value("${SHOPIFY_CLIENT_ID:}") String clientId,
                           @Value("${SHOPIFY_CLIENT_SECRET:}") String clientSecret,
                           @Value("${SHOPIFY_API_VERSION:2026-07}") String version,
                           @Value("${SHOPIFY_URL_BASE:}") String urlBase,
                           ObjectMapper json) {
        this.tienda = normalizarTienda(tienda);
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.version = version == null || version.isBlank() ? "2026-07" : version.trim();
        // SHOPIFY_URL_BASE solo se usa en pruebas (para apuntar a un servidor falso)
        this.urlBase = urlBase == null || urlBase.isBlank()
                ? (this.tienda.isEmpty() ? "" : "https://" + this.tienda)
                : urlBase.trim().replaceAll("/+$", "");
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** "Mi Tienda" → "", "mitienda" → "mitienda.myshopify.com", "https://mitienda.myshopify.com/" → "mitienda.myshopify.com". */
    static String normalizarTienda(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceFirst("^https?://", "").replaceAll("/.*$", "");
        if (t.isEmpty()) return "";
        if (!t.contains(".")) t = t + ".myshopify.com";
        return t.matches("[a-z0-9][a-z0-9-]*\\.myshopify\\.com") ? t : "";
    }

    public boolean isConfigurado() {
        return !tienda.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    /** Qué falta configurar (para mostrarlo en pantalla). Vacío si está todo. */
    public String getFaltante() {
        StringBuilder sb = new StringBuilder();
        if (tienda.isEmpty()) sb.append("SHOPIFY_TIENDA ");
        if (clientId.isEmpty()) sb.append("SHOPIFY_CLIENT_ID ");
        if (clientSecret.isEmpty()) sb.append("SHOPIFY_CLIENT_SECRET ");
        return sb.toString().trim();
    }

    public String getTienda() { return tienda; }

    /** Ejecuta una consulta GraphQL y devuelve "data". Lanza ShopifyError si algo falla. */
    public JsonNode graphql(String consulta, Map<String, Object> variables) {
        if (!isConfigurado()) throw new ShopifyError("Falta configurar en Railway: " + getFaltante() + ".");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("query", consulta);
        cuerpo.put("variables", variables == null ? Map.of() : variables);
        HttpResponse<String> r;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(urlBase + "/admin/api/" + version + "/graphql.json"))
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Shopify-Access-Token", token())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            r = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (ShopifyError e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ShopifyError("Shopify no respondió a tiempo. Inténtalo de nuevo.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShopifyError("Se interrumpió la conexión con Shopify.");
        } catch (Exception e) {
            throw new ShopifyError("No se pudo conectar con Shopify (" + e.getClass().getSimpleName() + ").");
        }
        if (r.statusCode() == 401 || r.statusCode() == 403) {
            synchronized (this) { token = null; tokenVence = Instant.EPOCH; }
            throw new ShopifyError("Shopify no dio permiso (código " + r.statusCode() + "). Revisa que la app esté instalada "
                    + "en la tienda y que tenga los permisos (scopes) de productos, pedidos y publicaciones.");
        }
        if (r.statusCode() == 429) throw new ShopifyError("Shopify pidió esperar un momento (demasiadas consultas). Inténtalo en un minuto.");
        JsonNode raiz;
        try {
            raiz = json.readTree(r.body() == null || r.body().isBlank() ? "{}" : r.body());
        } catch (Exception e) {
            throw new ShopifyError("Shopify respondió algo que no se pudo leer (código " + r.statusCode() + ").");
        }
        if (r.statusCode() < 200 || r.statusCode() >= 300) {
            throw new ShopifyError("Shopify respondió con error (código " + r.statusCode() + "): " + recortar(primerError(raiz), 300));
        }
        if (raiz.path("errors").isArray() && raiz.path("errors").size() > 0) {
            throw new ShopifyError("Shopify no aceptó la consulta: " + recortar(primerError(raiz), 300));
        }
        return raiz.path("data");
    }

    /** Pide (o reutiliza) el token de 24 horas. */
    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenVence)) return token;
        String formulario = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(urlBase + "/admin/oauth/access_token"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(formulario))
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode n = json.readTree(r.body() == null || r.body().isBlank() ? "{}" : r.body());
            String t = n.path("access_token").asText("");
            if (r.statusCode() < 200 || r.statusCode() >= 300 || t.isEmpty()) {
                String detalle = n.path("error_description").asText(n.path("error").asText(""));
                throw new ShopifyError("Shopify no entregó el token (código " + r.statusCode() + ")"
                        + (detalle.isEmpty() ? "" : ": " + recortar(detalle, 200))
                        + ". Revisa SHOPIFY_TIENDA, SHOPIFY_CLIENT_ID y SHOPIFY_CLIENT_SECRET, y que la app esté instalada en la tienda.");
            }
            long segundos = n.path("expires_in").asLong(86399);
            token = t;
            tokenVence = Instant.now().plusSeconds(Math.max(60, segundos - 120));
            return token;
        } catch (ShopifyError e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShopifyError("Se interrumpió la conexión con Shopify.");
        } catch (Exception e) {
            throw new ShopifyError("No se pudo pedir el token a Shopify (" + e.getClass().getSimpleName() + ").");
        }
    }

    /** Una foto descargada de Shopify. */
    public record Foto(byte[] datos, String tipo) {}

    /**
     * Descarga una foto de producto desde Shopify (solo de cdn.shopify.com, máximo 5 MB).
     * Devuelve null si no se pudo.
     */
    public Foto descargarFoto(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
            boolean permitido = "https".equals(uri.getScheme()) && (host.equals("cdn.shopify.com") || host.endsWith(".shopify.com")
                    || host.endsWith(".shopifycdn.com") || host.endsWith(".shopifycdn.net"));
            if (!permitido && (urlBase.isEmpty() || !url.startsWith(urlBase))) return null;   // urlBase: solo en pruebas
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            String tipo = r.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT);
            if (r.statusCode() != 200 || !tipo.startsWith("image/") || r.body().length == 0 || r.body().length > 5L * 1024 * 1024) return null;
            return new Foto(r.body(), tipo.split(";")[0].trim());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String primerError(JsonNode raiz) {
        JsonNode e = raiz.path("errors");
        if (e.isArray() && e.size() > 0) return e.get(0).path("message").asText(e.get(0).toString());
        if (e.isTextual()) return e.asText();
        if (e.isObject()) return e.toString();
        return raiz.toString();
    }

    static String recortar(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}