package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.Usuario;
import Colcones_Persinas.proyecto_express.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Reemplaza al InMemoryUserDetailsManager que antes tenía los usuarios
 * hardcodeados en SecurityConfig. Ahora el login busca el usuario en la
 * tabla `usuario`, así que crear cuentas, cambiar contraseñas o desactivar
 * usuarios se refleja de inmediato sin tocar código.
 *
 * Nota: como el login sigue siendo por JWT (stateless), un usuario que ya
 * tiene un token activo no pierde la sesión al instante si se le cambia el
 * rol, se le resetea la contraseña o se le desactiva la cuenta — eso solo
 * aplica la próxima vez que inicie sesión, mientras el token viejo no expire
 * (dura 8 horas, ver JwtService.EXPIRACION_MS).
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getPassword())
                .roles(usuario.getRol())
                .disabled(!usuario.isActivo())
                .build();
    }
}