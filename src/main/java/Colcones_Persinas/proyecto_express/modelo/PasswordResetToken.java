package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Token de un solo uso enviado por correo cuando alguien pide recuperar su
 * contraseña desde el login. Expira a los 30 minutos y queda marcado como
 * "usado" apenas se define la nueva contraseña, para que el mismo enlace
 * no se pueda reutilizar.
 */
@Entity
@Table(name = "password_reset_token")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "usuario_id", nullable = false)
    private int usuarioId;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(nullable = false)
    private boolean usado = false;

    @Transient
    public boolean isValido() {
        return !usado && LocalDateTime.now().isBefore(fechaExpiracion);
    }
}