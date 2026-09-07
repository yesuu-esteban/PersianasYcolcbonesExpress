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

            // Todos los roles caen primero en el portal; ahí cada quien ve
            // activos solo los módulos a los que tiene acceso (sec:authorize).
            String destino = "/portal";

            // ── FIX: la cookie "secure" solo se marca así si la conexión
            // realmente es HTTPS (request.isSecure()). Antes estaba fijo en
            // "true", así que en local por http:// el navegador la descartaba
            // en silencio: nunca se guardaba, nunca se enviaba, y cualquier
            // request que dependiera solo de la cookie (sin ?token= en la URL)
            // terminaba sin autenticar y te mandaba a /login. En Railway
            // (https) el comportamiento sigue siendo exactamente igual que antes.
            boolean esHttps = request.isSecure();

            ResponseCookie cookie = ResponseCookie.from("authToken", token)
                .httpOnly(true)
                .secure(esHttps)
                .path("/")
                .maxAge(8 * 60 * 60) // 8 horas, igual que la expiración del JWT
                .sameSite("Lax")
                .build();

            // Seguimos guardando en sessionStorage para que token-nav.js siga
            // funcionando igual en clicks/forms (no hace daño tenerlo duplicado).
            //
            // ── FIX extra: además de sessionStorage, mandamos el token también
            // como ?token= en la primera navegación al portal. Así la PRIMERA
            // carga de /portal ya llega autenticada sin depender de que la
            // cookie se haya guardado a tiempo (evita el "parpadeo" de ver el
            // portal como si no hubieras iniciado sesión justo después de loguearte).
            String destinoConToken = destino + "?token=" + token;

            String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body>
                <script>
                    sessionStorage.setItem('authToken', '%s');
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
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        boolean esHttps = request.isSecure();

        ResponseCookie cookieBorrada = ResponseCookie.from("authToken", "")
            .httpOnly(true)
            .secure(esHttps)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build();

        return ResponseEntity.status(302)
            .header(HttpHeaders.SET_COOKIE, cookieBorrada.toString())
            .header("Location", "/login")
            .build();
    }
}