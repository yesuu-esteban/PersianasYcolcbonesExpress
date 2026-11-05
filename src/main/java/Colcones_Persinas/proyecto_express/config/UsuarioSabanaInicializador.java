package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.usuarios.Usuario;
import Colcones_Persinas.proyecto_express.repository.usuarios.UsuarioRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el usuario de Giovanny (sede La Calera) para los Recibos Sabana.
 *
 *  - Usuario para iniciar sesión: giovanny
 *  - Rol: SABANA → en el portal SOLO ve la tarjeta "Recibos Sabana".
 *  - Nombre: Giovanny Andrés Romero Fagua
 *
 * La contraseña NO está escrita aquí (así no queda guardada en el código ni en GitHub).
 * Se toma de la variable CLAVE_GIOVANNY que se pone en Railway → Variables.
 *
 * Igual que UsuarioInicializador:
 *  - Si "giovanny" YA existe, no se toca nada (ni contraseña, ni rol).
 *  - Si no existe y la variable CLAVE_GIOVANNY no está puesta, no se crea y se avisa en el log.
 */
@Component
public class UsuarioSabanaInicializador implements CommandLineRunner {

    static final String USUARIO = "giovanny";
    static final String ROL = "SABANA";
    static final String NOMBRE = "Giovanny Andrés Romero Fagua";
    static final String VARIABLE_CLAVE = "CLAVE_GIOVANNY";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioSabanaInicializador(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.findByUsernameIgnoreCase(USUARIO).isPresent()) {
            return; // ya existe: no se toca
        }
        String clave = System.getenv(VARIABLE_CLAVE);
        if (clave == null || clave.isBlank()) {
            System.out.println("[UsuarioSabanaInicializador] No se creó \"" + USUARIO
                    + "\": falta la variable " + VARIABLE_CLAVE + " (ponla en Railway → Variables).");
            return;
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(USUARIO);
        usuario.setPassword(passwordEncoder.encode(clave.trim()));
        usuario.setRol(ROL);
        usuario.setNombreCompleto(NOMBRE);
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        System.out.println("[UsuarioSabanaInicializador] Usuario \"" + USUARIO + "\" creado con rol " + ROL + ".");
    }
}