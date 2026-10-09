package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CuentaContable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CuentaContableRepository extends JpaRepository<CuentaContable, Integer> {

    List<CuentaContable> findAllByOrderByOrdenAscNombreAsc();

    List<CuentaContable> findByActivaTrueOrderByOrdenAscNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, int id);

    /** La cuenta a la que entra la plata de la tienda virtual (si se eligió una). */
    Optional<CuentaContable> findFirstByRecibeTiendaTrue();

    /** Todas las que están marcadas como de la tienda (para dejar marcada solo una). */
    List<CuentaContable> findByRecibeTiendaTrue();
}