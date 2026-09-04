package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.Usuario;
import Colcones_Persinas.proyecto_express.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea los mismos 4 usuarios que antes vivían hardcodeados en SecurityConfig
 * (InMemoryUserDetailsManager), pero solo la primera vez que arranca la app
 * con la tabla `usuario` vacía. Así:
 *
 *  - Nadie se queda sin poder entrar al desplegar este cambio.
 *  - Si ya cambiaste alguna contraseña desde /mi-cuenta o /usuarios, este
 *    runner NO la vuelve a pisar en el próximo arranque (solo actúa cuando
 *    count() == 0).
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
        if (usuarioRepository.count() > 0) return;

        crear("jefe", "123456", "FABRICA", "Jefe de Fábrica");
        crear("vendedor1", "123456", "TIENDA", "Vendedor 1");
        crear("Tienda", "express", "TIENDA_ADMIN", "Administrador de Tienda");
        crear("admin", "123456", "ADMIN", "Administrador General");
    }

    private void crear(String username, String password, String rol, String nombreCompleto) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setNombreCompleto(nombreCompleto);
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }
}