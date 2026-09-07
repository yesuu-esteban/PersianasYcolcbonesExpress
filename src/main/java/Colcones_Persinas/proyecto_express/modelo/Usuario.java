package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

/**
 * Usuario del sistema, guardado en base de datos (reemplaza a los usuarios
 * que antes vivían hardcodeados en SecurityConfig con InMemoryUserDetailsManager).
 *
 * Esto permite: crear usuarios nuevos desde /usuarios (solo ADMIN), que cada
 * usuario cambie su propia contraseña e información desde /mi-cuenta, y que
 * el admin resetee contraseñas o desactive cuentas sin tocar código ni redeployar.
 *
 * Los roles NO se auto-gestionan: solo se agregan/quitan a mano en
 * ROLES_DISPONIBLES (UsuarioControlador) y en SecurityConfig si hiciera falta.
 */
@Entity
@Table(name = "usuario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true)
    private String username;

    /** Hash BCrypt de la contraseña. Nunca se guarda en texto plano. */
    @Column(nullable = false)
    private String password;

    @Column(name = "nombre_completo")
    private String nombreCompleto = "";

    /**
     * Un solo rol por usuario: "TIENDA", "TIENDA_ADMIN", "FABRICA" o "ADMIN"
     * (sin el prefijo ROLE_, Spring Security se lo agrega automáticamente).
     */
    @Column(nullable = false)
    private String rol;

    /** Si es false, el usuario no puede iniciar sesión aunque la contraseña sea correcta. */
    @Column(nullable = false)
    private boolean activo = true;
}