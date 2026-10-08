package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CategoriaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.SubcategoriaContable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubcategoriaContableRepository extends JpaRepository<SubcategoriaContable, Integer> {

    /** Para el abono de Almacén: "VENTA DE PRODUCTOS" dentro de una categoría de ingresos. */
    List<SubcategoriaContable> findByNombreIgnoreCaseAndCategoria_Clase(String nombre, String clase);

    boolean existsByCategoriaAndNombreIgnoreCase(CategoriaContable categoria, String nombre);

    boolean existsByCategoriaAndNombreIgnoreCaseAndIdNot(CategoriaContable categoria, String nombre, int id);

    long countByCategoria(CategoriaContable categoria);

    /** Cuántos movimientos usan esta subcategoría (si hay, no se puede eliminar). */
    @Query("select count(m) from MovimientoContable m where m.subcategoria = :sub")
    long contarMovimientos(@Param("sub") SubcategoriaContable sub);

    /** Cuántos movimientos usan alguna subcategoría de esta categoría (si hay, no se puede eliminar). */
    @Query("select count(m) from MovimientoContable m where m.subcategoria.categoria = :cat")
    long contarMovimientosDeCategoria(@Param("cat") CategoriaContable cat);
}