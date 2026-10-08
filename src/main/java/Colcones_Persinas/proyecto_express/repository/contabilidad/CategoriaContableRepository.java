package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CategoriaContable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaContableRepository extends JpaRepository<CategoriaContable, Integer> {

    List<CategoriaContable> findAllByOrderByOrdenAscNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, int id);
}