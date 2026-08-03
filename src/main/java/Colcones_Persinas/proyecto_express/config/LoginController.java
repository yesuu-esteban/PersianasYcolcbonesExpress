package Colcones_Persinas.proyecto_express.config;

import org.springframework.http.MediaType;
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

@Controller
public class LoginController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public LoginController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @GetMapping("/login")
    public String mostrarLogin(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("error", error != null ? "Usuario o contraseña incorrectos." : null);
        return "login";
    }

    @PostMapping(value = "/login-jwt", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> login(@RequestParam String username, @RequestParam String password) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            String token = jwtService.generarToken(userDetails);

            boolean esAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean esFabrica = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FABRICA"));

            String destino = (esAdmin || esFabrica) ? "/taller/pedidos" : "/tienda/listado";

            String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body>
                <script>
                    sessionStorage.setItem('authToken', '%s');
                    window.location.href = '%s?token=%s';
                </script>
                </body>
                </html>
                """.formatted(token, destino, token);

            return ResponseEntity.ok(html);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(302)
                .header("Location", "/login?error=1")
                .build();
        }
    }
}