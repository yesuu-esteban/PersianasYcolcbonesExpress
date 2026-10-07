package Colcones_Persinas.proyecto_express.repository.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoTiendaRepository extends JpaRepository<ProductoTienda, Integer> {

    /** Productos visibles: primero los destacados, luego por orden y nombre. */
    List<ProductoTienda> findByActivoTrueOrderByDestacadoDescOrdenAscNombreAsc();

    /** Productos visibles de una categoría (Persianas, Cortinas...). */
    List<ProductoTienda> findByActivoTrueAndCategoriaOrderByDestacadoDescOrdenAscNombreAsc(String categoria);

    /** Producto visible por su dirección: /tienda/producto/{slug}. */
    Optional<ProductoTienda> findBySlugAndActivoTrue(String slug);

    /** Todos (visibles y ocultos), para la administración. */
    List<ProductoTienda> findAllByOrderByOrdenAscNombreAsc();

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, int id);
}