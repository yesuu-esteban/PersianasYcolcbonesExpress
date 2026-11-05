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
 * Contraseña:
 *  - Si en Railway → Variables está CLAVE_GIOVANNY, se usa esa.
 *  - Si no está, se crea con la clave temporal 123456 (igual que los demás usuarios iniciales).
 *    Giovanny debe cambiarla al entrar: Mi cuenta → Cambiar contraseña.
 *
 * Igual que UsuarioInicializador:
 *  - Si "giovanny" YA existe, no se toca nada (ni contraseña, ni rol).
 */
@Component
public class UsuarioSabanaInicializador implements CommandLineRunner {

    static final String USUARIO = "giovanny";
    static final String ROL = "SABANA";
    static final String NOMBRE = "Giovanny Andrés Romero Fagua";
    static final String VARIABLE_CLAVE = "CLAVE_GIOVANNY";
    /** Clave temporal (la misma de los demás usuarios iniciales) si no se puso CLAVE_GIOVANNY. */
    static final String CLAVE_TEMPORAL = "123456";

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
            // Sin variable en Railway: se crea con la clave temporal de siempre.
            // Giovanny debe cambiarla al entrar por primera vez (Mi cuenta → Cambiar contraseña).
            clave = CLAVE_TEMPORAL;
            System.out.println("[UsuarioSabanaInicializador] \"" + USUARIO
                    + "\" se crea con la clave temporal. Cámbiala en Mi cuenta al entrar.");
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