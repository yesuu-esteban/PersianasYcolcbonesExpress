package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.Usuario;
import Colcones_Persinas.proyecto_express.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garantiza que ciertos usuarios existan en la base de datos.
 *
 * Revisa CADA usuario individualmente con findByUsernameIgnoreCase(...):
 *  - Si el usuario YA existe, no se toca (ni contraseña, ni rol, ni nada).
 *  - Si el usuario NO existe, se crea con los datos indicados aquí.
 *
 * Para agregar un usuario nuevo sin afectar los que ya tienes: agrega una
 * línea "crearSiNoExiste(...)" más abajo con sus datos, y en el próximo
 * arranque de la app se creará solo si no existía ya.
 *
 * asegurarRol(...) es distinto: SÍ modifica a un usuario que ya existe, pero
 * solo su rol (la contraseña y lo demás se respetan).
 */
@Component
public class UsuarioInicializador implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioInicializador(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // ── Usuarios originales (se respetan tal cual, no se tocan) ──
        crearSiNoExiste("jefe", "123456", "FABRICA", "Jefe de Fábrica");
        crearSiNoExiste("Fabian", "123456", "ADMIN", "Fabian - Jefe General");
        crearSiNoExiste("Tienda", "express", "TIENDA_ADMIN", "Administrador de Tienda");
        crearSiNoExiste("admin", "123456", "ADMIN", "Administrador General");

        // ── Usuarios nuevos ──
        crearSiNoExiste("Mono", "123456", "ADMIN", "Mono - Jefe General");
        crearSiNoExiste("jefe2", "123456", "FABRICA", "Jefe de Fábrica 2");
        crearSiNoExiste("tiendaadmin2", "123456", "TIENDA_ADMIN", "Administrador de Tienda 2");
        crearSiNoExiste("admin2", "123456", "ADMIN", "Administrador General 2");

        // ── Instaladores de prueba (cada uno tiene su agenda en /mi-calendario) ──
        // Cambia la contraseña desde "Mi cuenta" la primera vez que entres.
        crearSiNoExiste("instalador1", "123456", "INSTALADOR", "Instalador 1");
        crearSiNoExiste("instalador2", "123456", "INSTALADOR", "Instalador 2");

        // ── NUEVO: Fabian y Mono son jefes superiores → acceso a TODOS los módulos ──
        // Como ya existían con rol TIENDA, aquí se les sube a ADMIN (su contraseña no cambia).
        asegurarRol("Fabian", "ADMIN");
        asegurarRol("Mono", "ADMIN");
    }

    private void crearSiNoExiste(String username, String password, String rol, String nombreCompleto) {
        if (usuarioRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return; // ya existe: no se toca
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setNombreCompleto(nombreCompleto);
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    /**
     * Si el usuario existe y su rol es distinto al indicado, se lo cambia.
     * No toca la contraseña, el nombre ni nada más. Si el usuario no existe, no hace nada.
     */
    private void asegurarRol(String username, String rol) {
        usuarioRepository.findByUsernameIgnoreCase(username).ifPresent(usuario -> {
            if (!rol.equals(usuario.getRol())) {
                String rolAnterior = usuario.getRol();
                usuario.setRol(rol);
                usuarioRepository.save(usuario);
                System.out.println("[UsuarioInicializador] Rol de \"" + usuario.getUsername()
                        + "\" cambiado de " + rolAnterior + " a " + rol + ".");
            }
        });
    }
}