package Colcones_Persinas.proyecto_express.servicio.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Conexión con Wompi (Bancolombia).
 *
 * Las llaves salen del panel de Wompi → Desarrolladores, y se configuran como
 * variables de entorno (nunca escritas en el código):
 *   WOMPI_PUBLIC_KEY        → llave pública (pub_test_... en pruebas, pub_prod_... en producción)
 *   WOMPI_INTEGRITY_SECRET  → secreto de integridad
 *   WOMPI_EVENTS_SECRET     → secreto de eventos
 *
 * Flujo:
 *  1. El cliente paga en el checkout de Wompi (checkout.wompi.co). El dinero lo
 *     recibe Wompi y lo deposita en la cuenta bancaria registrada en el comercio.
 *  2. Wompi avisa el resultado de dos formas (se usan las dos, por seguridad):
 *     - Aviso automático (webhook) a /tienda/wompi/eventos.
 *     - Al regresar el cliente a /tienda/pedido/{referencia}?id=..., se consulta la
 *       transacción directamente a la API de Wompi.
 */
@Service
public class WompiServicio {

    private static final String URL_CHECKOUT = "https://checkout.wompi.co/p/";

    @Value("${wompi.public-key:}")
    private String publicKey;

    @Value("${wompi.integrity-secret:}")
    private String integritySecret;

    @Value("${wompi.events-secret:}")
    private String eventsSecret;

    private final RestClient http = RestClient.create();

    /** true si ya están las llaves para cobrar. */
    public boolean isConfigurado() {
        return !vacio(publicKey) && !vacio(integritySecret);
    }

    /** true si las llaves son de pruebas (sin dinero real). */
    public boolean isModoPruebas() {
        return publicKey != null && publicKey.startsWith("pub_test_");
    }

    public boolean isEventosConfigurados() {
        return !vacio(eventsSecret);
    }

    private String urlApi() {
        return isModoPruebas() ? "https://sandbox.wompi.co/v1" : "https://production.wompi.co/v1";
    }

    /** Firma de integridad: SHA-256 de (referencia + monto en centavos + moneda + secreto). */
    public String firmaIntegridad(String referencia, long centavos, String moneda) {
        return sha256(referencia + centavos + moneda + integritySecret);
    }

    /** URL del checkout de Wompi para pagar una orden. */
    public String urlCheckout(OrdenTienda orden, String urlRegreso) {
        long centavos = orden.getTotalEnCentavos();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("public-key", publicKey);
        p.put("currency", "COP");
        p.put("amount-in-cents", String.valueOf(centavos));
        p.put("reference", orden.getReferencia());
        p.put("signature:integrity", firmaIntegridad(orden.getReferencia(), centavos, "COP"));
        p.put("redirect-url", urlRegreso);
        if (!vacio(orden.getEmail()))         p.put("customer-data:email", orden.getEmail());
        if (!vacio(orden.getNombreCliente())) p.put("customer-data:full-name", orden.getNombreCliente());
        String tel = orden.getTelefono() != null ? orden.getTelefono().replaceAll("\\D", "") : "";
        if (tel.length() == 10) {
            p.put("customer-data:phone-number", tel);
            p.put("customer-data:phone-number-prefix", "+57");
        }
        if (!vacio(orden.getCedula())) {
            p.put("customer-data:legal-id", orden.getCedula().replaceAll("\\D", ""));
            p.put("customer-data:legal-id-type", "CC");
        }

        StringBuilder url = new StringBuilder(URL_CHECKOUT).append('?');
        p.forEach((k, v) -> url.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)).append('&'));
        url.setLength(url.length() - 1);
        return url.toString();
    }

    /** Consulta una transacción en la API de Wompi. Devuelve el objeto "data" o vacío si falla. */
    public Optional<JsonNode> consultarTransaccion(String transaccionId) {
        if (vacio(transaccionId) || !isConfigurado()) return Optional.empty();
        try {
            JsonNode respuesta = http.get()
                    .uri(urlApi() + "/transactions/{id}", transaccionId)
                    .retrieve()
                    .body(JsonNode.class);
            if (respuesta == null || respuesta.path("data").isMissingNode()) return Optional.empty();
            return Optional.of(respuesta.path("data"));
        } catch (Exception e) {
            System.err.println("[Wompi] No se pudo consultar la transacción " + transaccionId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Verifica que un aviso automático (evento) venga de verdad de Wompi:
     * SHA-256 de (valores de signature.properties + timestamp + secreto de eventos)
     * debe ser igual a signature.checksum.
     */
    public boolean eventoValido(JsonNode evento) {
        if (vacio(eventsSecret) || evento == null) return false;
        JsonNode firma = evento.path("signature");
        StringBuilder cadena = new StringBuilder();
        for (JsonNode prop : firma.path("properties")) {
            JsonNode nodo = evento.path("data");
            for (String parte : prop.asText().split("\\.")) nodo = nodo.path(parte);
            cadena.append(nodo.asText());
        }
        cadena.append(evento.path("timestamp").asText());
        cadena.append(eventsSecret);
        return sha256(cadena.toString()).equalsIgnoreCase(firma.path("checksum").asText());
    }

    private static String sha256(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular SHA-256", e);
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}