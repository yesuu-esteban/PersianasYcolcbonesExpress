package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.Usuario;
import Colcones_Persinas.proyecto_express.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garantiza que ciertos usuarios existan en la base de datos.
 *
 * A diferencia de la versión anterior (que solo corría si la tabla estaba
 * vacía con count() == 0), este runner revisa CADA usuario individualmente
 * con findByUsernameIgnoreCase(...). Así:
 *
 *  - Si el usuario YA existe, no se toca (ni contraseña, ni rol, ni nada).
 *  - Si el usuario NO existe, se crea con los datos indicados aquí.
 *
 * Para agregar un usuario nuevo sin afectar los que ya tienes: agrega una
 * línea "crearSiNoExiste(...)" más abajo con sus datos, y en el próximo
 * arranque de la app se creará solo si no existía ya.
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
        crearSiNoExiste("vendedor1", "123456", "TIENDA", "Vendedor 1");
        crearSiNoExiste("Tienda", "express", "TIENDA_ADMIN", "Administrador de Tienda");
        crearSiNoExiste("admin", "123456", "ADMIN", "Administrador General");

        // ── Usuarios nuevos ──
        crearSiNoExiste("vendedor2", "123456", "TIENDA", "Vendedor 2");
        crearSiNoExiste("jefe2", "123456", "FABRICA", "Jefe de Fábrica 2");
        crearSiNoExiste("tiendaadmin2", "123456", "TIENDA_ADMIN", "Administrador de Tienda 2");
        crearSiNoExiste("admin2", "123456", "ADMIN", "Administrador General 2");
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
}