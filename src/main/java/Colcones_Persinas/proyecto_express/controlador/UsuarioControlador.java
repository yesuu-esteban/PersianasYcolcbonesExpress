package Colcones_Persinas.proyecto_express.controlador;

import Colcones_Persinas.proyecto_express.modelo.Usuario;
import Colcones_Persinas.proyecto_express.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Controller
public class UsuarioControlador {

    private static final List<String> ROLES_DISPONIBLES =
            Arrays.asList("TIENDA", "TIENDA_ADMIN", "FABRICA", "ADMIN");

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ═══════════════════════════════════════════════════════════════
    // ADMINISTRACIÓN DE USUARIOS (solo ADMIN)
    // ═══════════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioRepository.findAllByOrderByUsernameAsc());
        model.addAttribute("usuarioActual", nombreUsuarioActual());
        return "usuarios/listado";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/usuarios/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        model.addAttribute("roles", ROLES_DISPONIBLES);
        return "usuarios/nuevo";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/usuarios/nuevo")
    public String crearUsuario(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmarPassword,
            @RequestParam String rol,
            @RequestParam(required = false) String nombreCompleto,
            RedirectAttributes redirectAttributes) {

        String usernameLimpio = username == null ? "" : username.trim();

        if (usernameLimpio.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El usuario debe tener un nombre.");
            return "redirect:/usuarios/nuevo";
        }
        if (usuarioRepository.findByUsernameIgnoreCase(usernameLimpio).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Ya existe un usuario con ese nombre.");
            return "redirect:/usuarios/nuevo";
        }
        if (password == null || password.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "La contraseña debe tener al menos 6 caracteres.");
            return "redirect:/usuarios/nuevo";
        }
        if (!password.equals(confirmarPassword)) {
            redirectAttributes.addFlashAttribute("error", "Las contraseñas no coinciden.");
            return "redirect:/usuarios/nuevo";
        }
        if (!ROLES_DISPONIBLES.contains(rol)) {
            redirectAttributes.addFlashAttribute("error", "Rol inválido.");
            return "redirect:/usuarios/nuevo";
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(usernameLimpio);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setNombreCompleto(nombreCompleto != null ? nombreCompleto.trim() : "");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);

        redirectAttributes.addFlashAttribute("mensaje", "Usuario \"" + usernameLimpio + "\" creado correctamente.");
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/usuarios/{id}/editar")
    public String mostrarFormularioEditar(@PathVariable("id") int id, Model model) {
        Usuario usuario = usuarioRepository.findById(id).orElseThrow();
        model.addAttribute("usuario", usuario);
        model.addAttribute("roles", ROLES_DISPONIBLES);
        return "usuarios/editar";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/usuarios/{id}/editar")
    public String guardarEdicion(
            @PathVariable("id") int id,
            @RequestParam String rol,
            @RequestParam(required = false) String nombreCompleto,
            @RequestParam(required = false, defaultValue = "false") boolean activo,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = usuarioRepository.findById(id).orElseThrow();

        if (!ROLES_DISPONIBLES.contains(rol)) {
            redirectAttributes.addFlashAttribute("error", "Rol inválido.");
            return "redirect:/usuarios/" + id + "/editar";
        }

        boolean esUsuarioActual = usuario.getUsername().equalsIgnoreCase(nombreUsuarioActual());

        // Evita que un admin se quite a sí mismo el rol de ADMIN o se desactive,
        // lo que dejaría el sistema sin nadie con permisos para revertirlo.
        if (esUsuarioActual && "ADMIN".equals(usuario.getRol()) && !"ADMIN".equals(rol)) {
            redirectAttributes.addFlashAttribute("error", "No puedes quitarte a ti mismo el rol de administrador.");
            return "redirect:/usuarios/" + id + "/editar";
        }
        if (esUsuarioActual && !activo) {
            redirectAttributes.addFlashAttribute("error", "No puedes desactivar tu propia cuenta.");
            return "redirect:/usuarios/" + id + "/editar";
        }

        usuario.setRol(rol);
        usuario.setNombreCompleto(nombreCompleto != null ? nombreCompleto.trim() : "");
        usuario.setActivo(activo);
        usuarioRepository.save(usuario);

        redirectAttributes.addFlashAttribute("mensaje", "Usuario \"" + usuario.getUsername() + "\" actualizado.");
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/usuarios/{id}/resetear-password")
    public String resetearPassword(
            @PathVariable("id") int id,
            @RequestParam String nuevaPassword,
            @RequestParam String confirmarPassword,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = usuarioRepository.findById(id).orElseThrow();

        if (nuevaPassword == null || nuevaPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "La nueva contraseña debe tener al menos 6 caracteres.");
            return "redirect:/usuarios/" + id + "/editar";
        }
        if (!nuevaPassword.equals(confirmarPassword)) {
            redirectAttributes.addFlashAttribute("error", "Las contraseñas no coinciden.");
            return "redirect:/usuarios/" + id + "/editar";
        }

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        redirectAttributes.addFlashAttribute("mensaje",
                "Contraseña de \"" + usuario.getUsername() + "\" restablecida correctamente.");
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/usuarios/{id}/eliminar")
    public String eliminarUsuario(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        Usuario usuario = usuarioRepository.findById(id).orElseThrow();

        if (usuario.getUsername().equalsIgnoreCase(nombreUsuarioActual())) {
            redirectAttributes.addFlashAttribute("error", "No puedes eliminar tu propia cuenta.");
            return "redirect:/usuarios";
        }

        usuarioRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("mensaje", "Usuario \"" + usuario.getUsername() + "\" eliminado.");
        return "redirect:/usuarios";
    }

    // ═══════════════════════════════════════════════════════════════
    // CADA USUARIO GESTIONA SU PROPIA CUENTA (cualquier rol logueado)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/mi-cuenta")
    public String verMiCuenta(Model model) {
        model.addAttribute("usuario", obtenerUsuarioActual());
        return "usuarios/mi_cuenta";
    }

    @PostMapping("/mi-cuenta/actualizar")
    public String actualizarMiInformacion(
            @RequestParam(required = false) String nombreCompleto,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = obtenerUsuarioActual();
        usuario.setNombreCompleto(nombreCompleto != null ? nombreCompleto.trim() : "");
        usuarioRepository.save(usuario);

        redirectAttributes.addFlashAttribute("mensaje", "Tu información fue actualizada.");
        return "redirect:/mi-cuenta";
    }

    @PostMapping("/mi-cuenta/cambiar-password")
    public String cambiarMiPassword(
            @RequestParam String passwordActual,
            @RequestParam String nuevaPassword,
            @RequestParam String confirmarPassword,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = obtenerUsuarioActual();

        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "La contraseña actual no es correcta.");
            return "redirect:/mi-cuenta";
        }
        if (nuevaPassword == null || nuevaPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "La nueva contraseña debe tener al menos 6 caracteres.");
            return "redirect:/mi-cuenta";
        }
        if (!nuevaPassword.equals(confirmarPassword)) {
            redirectAttributes.addFlashAttribute("error", "Las contraseñas nuevas no coinciden.");
            return "redirect:/mi-cuenta";
        }
        if (passwordEncoder.matches(nuevaPassword, usuario.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "La nueva contraseña debe ser diferente a la actual.");
            return "redirect:/mi-cuenta";
        }

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        redirectAttributes.addFlashAttribute("mensaje",
                "Contraseña actualizada correctamente. La sesión actual sigue activa; la próxima vez que "
                + "inicies sesión deberás usar la nueva contraseña.");
        return "redirect:/mi-cuenta";
    }

    // ─── Helpers ────────────────────────────────────────────────────────
    private String nombreUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private Usuario obtenerUsuarioActual() {
        String username = nombreUsuarioActual();
        Optional<Usuario> usuario = usuarioRepository.findByUsernameIgnoreCase(username);
        return usuario.orElseThrow(() ->
                new IllegalStateException("No se encontró el usuario autenticado: " + username));
    }
}