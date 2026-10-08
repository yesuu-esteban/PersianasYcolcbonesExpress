package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CategoriaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.SubcategoriaContable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubcategoriaContableRepository extends JpaRepository<SubcategoriaContable, Integer> {

    /** Para el abono de Almacén: "VENTA DE PRODUCTOS" dentro de una categoría de ingresos. */
    List<SubcategoriaContable> findByNombreIgnoreCaseAndCategoria_Clase(String nombre, String clase);

    boolean existsByCategoriaAndNombreIgnoreCase(CategoriaContable categoria, String nombre);

    boolean existsByCategoriaAndNombreIgnoreCaseAndIdNot(CategoriaContable categoria, String nombre, int id);

    long countByCategoria(CategoriaContable categoria);
}