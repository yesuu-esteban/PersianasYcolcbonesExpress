package Colcones_Persinas.proyecto_express.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = obtenerPrimerTokenValido(request);

        if (token != null) {
            Claims claims = jwtService.validarYObtenerClaims(token);
            String username = claims.getSubject();

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            List<GrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Antes este método se quedaba con el PRIMER candidato que existiera
     * (query param → header → cookie), sin fijarse si era válido. Si el
     * ?token= de la URL (el que agrega token-nav.js desde sessionStorage)
     * estaba vencido o corrupto — por ejemplo, porque se cerró y volvió a
     * abrir la pestaña y sessionStorage quedó desincronizado de la cookie,
     * que dura 8h — el filtro se rendía ahí mismo y nunca llegaba a probar
     * la cookie, aunque esta sí fuera válida. Resultado: te mandaba al
     * login sin motivo real.
     *
     * Ahora se prueban los tres orígenes EN ORDEN y se usa el primero que
     * de verdad valide, cayendo al siguiente si el anterior no sirve.
     */
    private String obtenerPrimerTokenValido(HttpServletRequest request) {
        for (String candidato : obtenerCandidatos(request)) {
            if (candidato != null && !candidato.isBlank() && jwtService.esTokenValido(candidato)) {
                return candidato;
            }
        }
        return null;
    }

    private List<String> obtenerCandidatos(HttpServletRequest request) {
        List<String> candidatos = new ArrayList<>();

        // 1. Query param (?token=...) — lo que agrega token-nav.js en clicks/formularios
        String tokenParam = request.getParameter("token");
        if (tokenParam != null && !tokenParam.isBlank()) {
            candidatos.add(tokenParam);
        }

        // 2. Header Authorization: Bearer ... — para llamadas API si las hubiera
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            candidatos.add(header.substring(7));
        }

        // 3. Cookie "authToken" — sobrevive automáticamente a cualquier redirect
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("authToken".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    candidatos.add(c.getValue());
                }
            }
        }

        return candidatos;
    }
}