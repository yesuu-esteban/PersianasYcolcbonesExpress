package Colcones_Persinas.proyecto_express.config.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Separa la TIENDA VIRTUAL (clientes) del SOFTWARE INTERNO (personal) aunque
 * vivan en el mismo proyecto y el mismo servicio de Railway.
 *
 *  - En el dominio de la tienda (app.dominio-tienda) solo existe la tienda:
 *    "/" lleva a /tienda, y cualquier ruta interna (/portal, /login, /almacen,
 *    /taller, /instalaciones...) también vuelve a /tienda. El cliente nunca ve el sistema.
 *  - En cualquier otro dominio (el del sistema, localhost, *.up.railway.app),
 *    todo funciona como siempre.
 *
 * Si app.dominio-tienda está vacío, el filtro no hace nada (útil en desarrollo).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SeparacionDominiosFilter extends OncePerRequestFilter {

    /** Rutas que SÍ se permiten en el dominio de la tienda ("/tienda-admin" NO, es del sistema interno). */
    private static final String[] PREFIJOS_TIENDA = { "/tienda/", "/css/", "/js/", "/images/", "/favicon", "/error" };

    private final Set<String> dominiosTienda;

    public SeparacionDominiosFilter(@Value("${app.dominio-tienda:}") String dominiosTienda) {
        // Admite varios separados por coma: "pcexpress.co, www.pcexpress.co"
        this.dominiosTienda = Arrays.stream(dominiosTienda.split(","))
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!dominiosTienda.isEmpty() && esDominioTienda(request)) {
            String ruta = request.getRequestURI().substring(request.getContextPath().length());
            boolean permitida = ruta.equals("/tienda") || Arrays.stream(PREFIJOS_TIENDA).anyMatch(ruta::startsWith);
            if (!permitida) {
                response.sendRedirect(request.getContextPath() + "/tienda");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean esDominioTienda(HttpServletRequest request) {
        String host = request.getServerName();
        return host != null && dominiosTienda.contains(host.toLowerCase(Locale.ROOT));
    }
}