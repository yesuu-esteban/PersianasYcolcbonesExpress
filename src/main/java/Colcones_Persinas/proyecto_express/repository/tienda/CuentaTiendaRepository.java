package Colcones_Persinas.proyecto_express.repository.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.CuentaTienda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CuentaTiendaRepository extends JpaRepository<CuentaTienda, Integer> {

    List<CuentaTienda> findAllByOrderByOrdenAscNombreAsc();

    List<CuentaTienda> findByActivaTrueOrderByOrdenAscNombreAsc();

    Optional<CuentaTienda> findFirstByRecibeWompiTrue();

    Optional<CuentaTienda> findFirstByRecibeAddiTrue();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, int id);
}