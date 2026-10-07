package Colcones_Persinas.proyecto_express.repository.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;

import Colcones_Persinas.proyecto_express.modelo.usuarios.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByUsernameIgnoreCase(String username);

    List<Usuario> findAllByOrderByUsernameAsc();
}