package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CuentaContable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuentaContableRepository extends JpaRepository<CuentaContable, Integer> {

    List<CuentaContable> findAllByOrderByOrdenAscNombreAsc();

    List<CuentaContable> findByActivaTrueOrderByOrdenAscNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, int id);
}