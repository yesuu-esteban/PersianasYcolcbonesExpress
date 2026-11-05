package Colcones_Persinas.proyecto_express.repository.fabrica;

import Colcones_Persinas.proyecto_express.modelo.fabrica.PrecioVentaEstandar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrecioVentaEstandarRepository extends JpaRepository<PrecioVentaEstandar, Integer> {
    Optional<PrecioVentaEstandar> findByClave(String clave);
    List<PrecioVentaEstandar> findAllByOrderByClaveAsc();
}