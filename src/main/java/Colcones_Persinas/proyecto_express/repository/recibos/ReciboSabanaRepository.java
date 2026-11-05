package Colcones_Persinas.proyecto_express.repository.recibos;

import Colcones_Persinas.proyecto_express.modelo.recibos.ReciboSabana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Recibos de caja de la sede Sabana (tabla aparte de los de Almacén y Fábrica). */
public interface ReciboSabanaRepository extends JpaRepository<ReciboSabana, Integer> {

    List<ReciboSabana> findAllByOrderByIdDesc();

    /** El número más alto usado hasta ahora (0 si todavía no hay recibos). */
    @Query("select coalesce(max(r.numero), 0) from ReciboSabana r")
    int maxNumero();
}