package Colcones_Persinas.proyecto_express.config.inicializadores;

import Colcones_Persinas.proyecto_express.modelo.usuarios.Usuario;
import Colcones_Persinas.proyecto_express.repository.usuarios.UsuarioRepository;

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
 * asegurarRol(...)           → cambia SOLO el rol de un usuario que ya existe.
 * renombrarUsuario(...)      → cambia el usuario (para iniciar sesión) y el nombre visible
 *                              de un usuario que ya existe. Conserva su contraseña y su rol.
 * marcarTambienInstalador(...) → el usuario conserva su rol y además aparece como instalador.
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
        crearSiNoExiste("Mono", "123456", "ADMIN", "Mono");
        crearSiNoExiste("jefe2", "123456", "FABRICA", "Jefe de Fábrica 2");
        crearSiNoExiste("tiendaadmin2", "123456", "TIENDA_ADMIN", "Administrador de Tienda 2");
        crearSiNoExiste("admin2", "123456", "ADMIN", "Administrador General 2");

        // ── Fabian y Mono son jefes superiores → acceso a TODOS los módulos ──
        asegurarRol("Fabian", "ADMIN");
        asegurarRol("Mono", "ADMIN");

        // ── Mono además instala: sale en la lista de instaladores y tiene "Mi agenda" ──
        marcarTambienInstalador("Mono");

        // ── Instaladores ──
        // 1) Los que ya estaban creados como instalador1 / instalador2 se renombran.
        renombrarUsuario("instalador1", "arbey", "Arbey");
        renombrarUsuario("instalador2", "fabianlopez", "Fabián López");

        // 2) Se crean si no existen (por ejemplo, en una base de datos nueva).
        crearSiNoExiste("arbey", "123456", "INSTALADOR", "Arbey");
        crearSiNoExiste("fabianlopez", "123456", "INSTALADOR", "Fabián López");
        crearSiNoExiste("juanpablo", "123456", "INSTALADOR", "Juan Pablo");
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
                usuario.setRol(rol);
                usuarioRepository.save(usuario);
            }
        });
    }

    /**
     * El usuario conserva su rol principal (ej: ADMIN) y además queda como instalador:
     * aparece en la lista de instaladores y puede entrar a "Mi agenda".
     */
    private void marcarTambienInstalador(String username) {
        usuarioRepository.findByUsernameIgnoreCase(username).ifPresent(usuario -> {
            if (!usuario.isTambienInstalador()) {
                usuario.setTambienInstalador(true);
                usuarioRepository.save(usuario);
                System.out.println("[UsuarioInicializador] \"" + usuario.getUsername() + "\" ahora también es instalador.");
            }
        });
    }

    /**
     * Cambia el usuario (para iniciar sesión) y el nombre visible de un usuario que ya existe.
     * Conserva su contraseña y su rol.
     *  - Si el usuario viejo no existe, no hace nada (ya se renombró antes o nunca se creó).
     *  - Si el usuario nuevo ya existe, no hace nada (para no duplicar nombres).
     */
    private void renombrarUsuario(String usuarioViejo, String usuarioNuevo, String nombreCompleto) {
        if (usuarioRepository.findByUsernameIgnoreCase(usuarioNuevo).isPresent()) {
            return;
        }
        usuarioRepository.findByUsernameIgnoreCase(usuarioViejo).ifPresent(usuario -> {
            usuario.setUsername(usuarioNuevo);
            usuario.setNombreCompleto(nombreCompleto);
            usuarioRepository.save(usuario);
            System.out.println("[UsuarioInicializador] \"" + usuarioViejo + "\" ahora es \"" + usuarioNuevo + "\".");
        });
    }
}