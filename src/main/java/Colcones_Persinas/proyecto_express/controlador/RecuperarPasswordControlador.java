package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.PasswordResetToken;
import Colcones_Persinas.proyecto_express.modelo.Usuario;
import Colcones_Persinas.proyecto_express.repository.PasswordResetTokenRepository;
import Colcones_Persinas.proyecto_express.repository.UsuarioRepository;
import Colcones_Persinas.proyecto_express.servicio.EmailServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/recuperar-password")
public class RecuperarPasswordControlador {

    private static final int MINUTOS_EXPIRACION = 30;

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private EmailServicio emailServicio;
    @Autowired private PasswordEncoder passwordEncoder;

    // Dominio público de la app para armar el enlace del correo. En Railway
    // (o donde despliegues) define la propiedad app.base-url con tu dominio
    // real, por ejemplo: app.base-url=https://tu-app.up.railway.app
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ─── Paso 1: pedir usuario o correo ─────────────────────────────
    @GetMapping
    public String mostrarFormularioSolicitud() {
        return "recuperar_password/solicitar";
    }

    @PostMapping
    public String solicitarRecuperacion(
            @RequestParam String usuarioOCorreo,
            RedirectAttributes redirectAttributes) {

        String valor = usuarioOCorreo == null ? "" : usuarioOCorreo.trim();

        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsernameIgnoreCase(valor);
        if (usuarioOpt.isEmpty()) {
            usuarioOpt = usuarioRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(valor))
                    .findFirst();
        }

        // La respuesta es SIEMPRE la misma, exista o no el usuario/correo:
        // así nadie puede usar este formulario para averiguar qué nombres
        // de usuario existen en el sistema probando uno por uno.
        String mensajeGenerico = "Si el usuario o correo existe y tiene un correo registrado, "
                + "te acabamos de enviar un enlace para restablecer tu contraseña. "
                + "Revisa tu bandeja de entrada (y la carpeta de spam).";

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            if (usuario.isActivo() && usuario.getEmail() != null && !usuario.getEmail().isBlank()) {
                String token = UUID.randomUUID().toString();

                PasswordResetToken registro = new PasswordResetToken();
                registro.setToken(token);
                registro.setUsuarioId(usuario.getId());
                registro.setFechaExpiracion(LocalDateTime.now().plusMinutes(MINUTOS_EXPIRACION));
                registro.setUsado(false);
                tokenRepository.save(registro);

                String enlace = baseUrl + "/recuperar-password/" + token;
                try {
                    emailServicio.enviarCorreoRecuperacion(usuario.getEmail(), enlace);
                } catch (Exception e) {
                    // No mostramos el error real al usuario (podría filtrar info del
                    // servidor), pero lo dejamos en el log para poder depurarlo.
                    System.err.println("No se pudo enviar el correo de recuperación: " + e.getMessage());
                }
            }
        }

        redirectAttributes.addFlashAttribute("mensaje", mensajeGenerico);
        return "redirect:/recuperar-password";
    }

    // ─── Paso 2: definir la nueva contraseña desde el enlace del correo ─
    @GetMapping("/{token}")
    public String mostrarFormularioNuevaPassword(@PathVariable String token, Model model) {
        Optional<PasswordResetToken> registro = tokenRepository.findByToken(token);

        if (registro.isEmpty() || !registro.get().isValido()) {
            model.addAttribute("tokenInvalido", true);
            return "recuperar_password/nueva_password";
        }

        model.addAttribute("token", token);
        return "recuperar_password/nueva_password";
    }

    @PostMapping("/{token}")
    public String guardarNuevaPassword(
            @PathVariable String token,
            @RequestParam String nuevaPassword,
            @RequestParam String confirmarPassword,
            Model model) {

        Optional<PasswordResetToken> registroOpt = tokenRepository.findByToken(token);

        if (registroOpt.isEmpty() || !registroOpt.get().isValido()) {
            model.addAttribute("tokenInvalido", true);
            return "recuperar_password/nueva_password";
        }

        if (nuevaPassword == null || nuevaPassword.length() < 6) {
            model.addAttribute("error", "La contraseña debe tener al menos 6 caracteres.");
            model.addAttribute("token", token);
            return "recuperar_password/nueva_password";
        }
        if (!nuevaPassword.equals(confirmarPassword)) {
            model.addAttribute("error", "Las contraseñas no coinciden.");
            model.addAttribute("token", token);
            return "recuperar_password/nueva_password";
        }

        PasswordResetToken registro = registroOpt.get();
        Usuario usuario = usuarioRepository.findById(registro.getUsuarioId()).orElseThrow();
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        registro.setUsado(true);
        tokenRepository.save(registro);

        return "redirect:/login?recuperada=1";
    }
}