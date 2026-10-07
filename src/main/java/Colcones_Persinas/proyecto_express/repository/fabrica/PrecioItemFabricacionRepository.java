package Colcones_Persinas.proyecto_express.repository.fabrica;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Colcones_Persinas.proyecto_express.modelo.fabrica.PrecioItemFabricacion;

@Repository
public interface PrecioItemFabricacionRepository extends JpaRepository<PrecioItemFabricacion, Integer> {

    List<PrecioItemFabricacion> findAllByOrderByNombreAsc();
    
    Optional<PrecioItemFabricacion> findByNombreIgnoreCase(String nombre);

}