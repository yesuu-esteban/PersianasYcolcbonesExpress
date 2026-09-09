package Colcones_Persinas.proyecto_express.repository;

import Colcones_Persinas.proyecto_express.modelo.ReciboCaja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReciboCajaRepository extends JpaRepository<ReciboCaja, Integer> {

    List<ReciboCaja> findAllByOrderByIdDesc();

    /** Cuenta cuántos recibos siguen existiendo con un id menor al indicado.
     *  Eso es exactamente la posición (0-based) que le corresponde a un
     *  recibo en la numeración compacta y sin huecos. */
    long countByIdLessThan(int id);
}