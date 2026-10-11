package Colcones_Persinas.proyecto_express.servicio.tienda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Conexión con Dropi (PRUEBA, SOLO LECTURA).
 *
 * Usa la misma forma de conectarse que el complemento de Dropi para WooCommerce:
 *  - Dirección: https://api.dropi.co/integrations/ (Colombia)
 *  - El token va en el encabezado "dropi-integration-key".
 * No es una conexión documentada oficialmente por Dropi; por eso aquí solo se CONSULTAN
 * productos (nombre, stock y precios). No crea pedidos ni cambia nada en Dropi.
 *
 * El token se lee de la variable DROPI_TOKEN (en Railway o en la computadora).
 * Nunca se escribe en el código, en la base de datos ni en la pantalla.
 */
@Service
public class DropiServicio {

    /** Lo que se sabe de un producto de Dropi. ok=false trae el mensaje de por qué no se pudo. */
    public record ProductoDropi(boolean ok, String mensaje, String id, String nombre, String sku, String tipo,
                                Integer stock, BigDecimal precioParaTi, BigDecimal precioSugerido,
                                int variaciones, String respuesta) {
        static ProductoDropi error(String mensaje, String respuesta) {
            return new ProductoDropi(false, mensaje, null, null, null, null, null, null, null, 0, respuesta);
        }
    }

    private static final int MAX_RESPUESTA = 6000;

    private final String token;
    private final String urlApi;
    private final ObjectMapper json;
    private final HttpClient http;

    public DropiServicio(@Value("${DROPI_TOKEN:}") String token,
                         @Value("${DROPI_URL:https://api.dropi.co/integrations/}") String urlApi,
                         ObjectMapper json) {
        this.token = token == null ? "" : token.trim();
        this.urlApi = urlApi.endsWith("/") ? urlApi : urlApi + "/";
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** ¿Ya se puso la variable DROPI_TOKEN? */
    public boolean isConfigurado() {
        return !token.isEmpty();
    }

    /**
     * Consulta un producto de Dropi por su ID (el número que sale en Dropi, ej: 45872).
     * Solo lee: no cambia nada en Dropi.
     */
    public ProductoDropi consultarProducto(String idProducto) {
        if (!isConfigurado()) {
            return ProductoDropi.error("Falta la variable DROPI_TOKEN. Ponla en Railway (o en tu computadora) y reinicia.", null);
        }
        String id = idProducto == null ? "" : idProducto.trim();
        if (!id.matches("\\d{1,12}")) {
            return ProductoDropi.error("Escribe el ID del producto en Dropi: solo números (ej: 45872). "
                    + "Lo ves en Dropi en el detalle del producto o en la dirección de la página.", null);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(urlApi + "products/v2/" + id))
                    .timeout(Duration.ofSeconds(20))
                    .header("dropi-integration-key", token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            return leerProducto(r.statusCode(), r.body(), json);
        } catch (java.net.http.HttpTimeoutException e) {
            return ProductoDropi.error("Dropi no respondió a tiempo. Inténtalo de nuevo en un momento.", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProductoDropi.error("Se interrumpió la consulta a Dropi.", null);
        } catch (Exception e) {
            return ProductoDropi.error("No se pudo conectar con Dropi (" + e.getClass().getSimpleName() + ").", null);
        }
    }

    /** Lee la respuesta de Dropi. Es estática para poder probarla sin conectarse. */
    static ProductoDropi leerProducto(int status, String cuerpo, ObjectMapper json) {
        String crudo = cuerpo == null ? "" : cuerpo;
        String recorte = crudo.length() > MAX_RESPUESTA ? crudo.substring(0, MAX_RESPUESTA) + " …(recortado)" : crudo;

        if (status == 401 || status == 403) {
            return ProductoDropi.error("Dropi no aceptó el token (código " + status + "). Puede que esa llave no sirva "
                    + "para este tipo de conexión, o que esté mal copiada en la variable DROPI_TOKEN.", recorte);
        }
        if (status == 404) {
            return ProductoDropi.error("Dropi dice que ese producto no existe (código 404). Revisa el ID.", recorte);
        }

        JsonNode raiz;
        try {
            raiz = json.readTree(crudo.isBlank() ? "{}" : crudo);
        } catch (Exception e) {
            return ProductoDropi.error("Dropi respondió algo que no se pudo leer (código " + status + ").", recorte);
        }
        if (status < 200 || status >= 300 || (raiz.has("isSuccess") && !raiz.path("isSuccess").asBoolean(true))) {
            String msg = texto(raiz, "message");
            if (msg == null) msg = texto(raiz, "status");
            String codigo = status >= 200 && status < 300 ? "" : " (código " + status + ")";
            return ProductoDropi.error("Dropi respondió con un error" + codigo
                    + (msg != null ? ": " + msg : "."), recorte);
        }

        JsonNode p = raiz.path("objects");
        if (p.isArray()) p = p.size() > 0 ? p.get(0) : json.createObjectNode();
        if (p.isMissingNode() || p.isNull() || p.isEmpty()) {
            return ProductoDropi.error("La conexión funcionó, pero Dropi no devolvió datos de ese producto.", recorte);
        }

        String tipo = texto(p, "type");
        int variaciones = p.path("variations").isArray() ? p.path("variations").size() : 0;
        return new ProductoDropi(true, "¡Conexión con Dropi funcionando!", texto(p, "id"), texto(p, "name"),
                texto(p, "sku"), tipo, stock(p), plata(p, "sale_price"), plata(p, "suggested_price"),
                variaciones, recorte);
    }

    /**
     * Stock del producto: el campo "stock", o la suma de "warehouse_product[].stock".
     * Si es un producto con variaciones (tallas, colores), la suma de las variaciones.
     */
    static Integer stock(JsonNode p) {
        List<Integer> partes = new ArrayList<>();
        JsonNode bodegas = p.path("warehouse_product");
        if (bodegas.isArray() && bodegas.size() > 0) {
            for (JsonNode b : bodegas) if (b.path("stock").isNumber() || b.path("stock").isTextual()) partes.add(entero(b.path("stock")));
        } else if (p.has("stock") && !p.path("stock").isNull()) {
            partes.add(entero(p.path("stock")));
        }
        JsonNode vars = p.path("variations");
        if (partes.isEmpty() && vars.isArray() && vars.size() > 0) {
            for (JsonNode v : vars) {
                JsonNode bv = v.path("warehouse_product_variation");
                if (bv.isArray() && bv.size() > 0) { for (JsonNode b : bv) partes.add(entero(b.path("stock"))); }
                else if (v.has("stock")) partes.add(entero(v.path("stock")));
            }
        }
        if (partes.isEmpty()) return null;
        int total = 0;
        for (Integer n : partes) total += n == null ? 0 : n;
        return total;
    }

    private static Integer entero(JsonNode n) {
        try { return (int) Math.round(Double.parseDouble(n.asText("0").trim())); } catch (Exception e) { return 0; }
    }

    private static BigDecimal plata(JsonNode p, String campo) {
        JsonNode n = p.path(campo);
        if (n.isMissingNode() || n.isNull()) return null;
        try {
            BigDecimal v = new BigDecimal(n.asText().trim());
            return v.signum() > 0 ? v.setScale(0, java.math.RoundingMode.HALF_UP) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.path(campo);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }
}