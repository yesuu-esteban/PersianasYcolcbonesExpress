package Colcones_Persinas.proyecto_express.controlador.inicio;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import Colcones_Persinas.proyecto_express.config.seguridad.JwtService;

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
            @RequestParam(required = false) String salio,
            Model model) {
        if ("inactivo".equals(error)) {
            model.addAttribute("error", "Tu cuenta está desactivada. Habla con el administrador.");
        } else if (error != null) {
            model.addAttribute("error", "Usuario o contraseña incorrectos.");
        }
        // ── NUEVO: confirmación de que la sesión se cerró ──
        if (salio != null) {
            model.addAttribute("mensaje", "Cerraste sesión correctamente.");
        }
        // La plantilla ahora está en templates/inicio/login.html
        return "inicio/login";
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
            // request.isSecure() refleja correctamente si el cliente original usó HTTPS,
            // aunque Railway termine el TLS antes de reenviar la petición por HTTP.
            boolean esHttps = request.isSecure();

            ResponseCookie cookie = ResponseCookie.from("authToken", token)
                .httpOnly(true)
                .secure(esHttps)
                .path("/")
                .maxAge(8 * 60 * 60) // 8 horas, igual que la expiración del JWT
                .sameSite("Lax")
                .build();

            // El token también se guarda en localStorage para que token-nav.js lo
            // agregue a los enlaces. localStorage persiste entre pestañas y recargas
            // hasta que se borre explícitamente (lo hacemos en /logout).
            String destinoConToken = destino + "?token=" + token;

            String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body>
                <script>
                    localStorage.setItem('authToken', '%s');
                    window.location.replace('%s');
                </script>
                </body>
                </html>
                """.formatted(token, destinoConToken);

            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(html);

        } catch (DisabledException e) {
            return ResponseEntity.status(302)
                .header("Location", "/login?error=inactivo")
                .build();
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(302)
                .header("Location", "/login?error=1")
                .build();
        }
    }

    /**
     * Cierra la sesión DE VERDAD, en los dos lados:
     *  1. Servidor/navegador: borra la cookie "authToken" (maxAge 0).
     *  2. Navegador: borra el token de localStorage y sessionStorage, para que
     *     token-nav.js ya no lo agregue a los enlaces.
     *
     * Antes este método nunca se ejecutaba: el logout por defecto de Spring
     * Security atrapaba /logout primero. Ahora está desactivado en SecurityConfig.
     */
    @GetMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        boolean esHttps = request.isSecure();

        ResponseCookie cookieBorrada = ResponseCookie.from("authToken", "")
            .httpOnly(true)
            .secure(esHttps)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build();

        // window.location.replace: no deja la página de "cerrando sesión" en el
        // historial, así el botón "atrás" no vuelve a una página con la sesión vieja.
        String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body>
            <script>
                try { localStorage.removeItem('authToken'); } catch (e) {}
                try { sessionStorage.removeItem('authToken'); } catch (e) {}
                window.location.replace('/login?salio=1');
            </script>
            </body>
            </html>
            """;

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookieBorrada.toString())
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.TEXT_HTML)
            .body(html);
    }
}