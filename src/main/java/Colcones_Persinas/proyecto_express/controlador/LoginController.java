package Colcones_Persinas.proyecto_express.controlador;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import Colcones_Persinas.proyecto_express.config.JwtService;

@Controller
public class LoginController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public LoginController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @GetMapping("/login")
    public String mostrarLogin(
            @RequestParam(required = false) String error,
            Model model) {
        model.addAttribute("error", error != null ? "Usuario o contraseña incorrectos." : null);
        return "login";
    }

    @PostMapping(value = "/login-jwt", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> login(@RequestParam String username,
                                         @RequestParam String password,
                                         HttpServletRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            String token = jwtService.generarToken(userDetails);

            String destino = "/portal";

            // Con server.forward-headers-strategy=framework en application.properties,
            // request.isSecure() ahora refleja correctamente si el cliente original
            // usó HTTPS, aunque Railway termine el TLS antes de reenviar la petición
            // internamente por HTTP. Sin esa propiedad, isSecure() podía devolver
            // false de forma inconsistente detrás del proxy.
            boolean esHttps = request.isSecure();

            ResponseCookie cookie = ResponseCookie.from("authToken", token)
                .httpOnly(true)
                .secure(esHttps)
                .path("/")
                .maxAge(8 * 60 * 60) // 8 horas, igual que la expiración del JWT
                .sameSite("Lax")
                .build();

            // ── FIX: usamos localStorage en vez de sessionStorage.
            // sessionStorage se borra al cerrar la pestaña Y NO se comparte entre
            // pestañas nuevas (cada pestaña tiene su propio sessionStorage aislado).
            // Eso hacía que, al abrir una pestaña nueva o refrescar en ciertos
            // escenarios, el token "desapareciera" del lado del cliente y
            // token-nav.js ya no tuviera nada que adjuntar a los links, dejando
            // la autenticación dependiendo 100% de la cookie (que si fallaba,
            // mandaba directo a /login). localStorage persiste entre pestañas y
            // recargas hasta que se borre explícitamente (lo hacemos en /logout).
            String destinoConToken = destino + "?token=" + token;

            String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body>
                <script>
                    localStorage.setItem('authToken', '%s');
                    window.location.href = '%s';
                </script>
                </body>
                </html>
                """.formatted(token, destinoConToken);

            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(html);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(302)
                .header("Location", "/login?error=1")
                .build();
        }
    }

    @GetMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        boolean esHttps = request.isSecure();

        ResponseCookie cookieBorrada = ResponseCookie.from("authToken", "")
            .httpOnly(true)
            .secure(esHttps)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build();

        // Como el token también vive en localStorage (client-side), un simple
        // redirect 302 no lo borra ahí. Devolvemos una página intermedia que
        // limpia localStorage antes de mandar al login, para que "Cerrar sesión"
        // sí cierre sesión de verdad en ambos lados.
        String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body>
            <script>
                localStorage.removeItem('authToken');
                window.location.href = '/login';
            </script>
            </body>
            </html>
            """;

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookieBorrada.toString())
            .contentType(MediaType.TEXT_HTML)
            .body(html);
    }
}