package Colcones_Persinas.proyecto_express.repository;

import Colcones_Persinas.proyecto_express.modelo.RetazoTela;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RetazoTelaRepository extends JpaRepository<RetazoTela, Integer> {

    /**
     * Retazos del color indicado cuyo ancho y alto son suficientes para el corte,
     * ordenados por alto ascendente (primero el que tenga menos sobrante al cortar,
     * para minimizar desperdicio y cerrar retazos pequeños antes).
     */
    List<RetazoTela> findByColorAndAnchoGreaterThanEqualAndAltoGreaterThanEqualOrderByAltoAsc(
            String color, double anchoMinimo, double altoMinimo);

    List<RetazoTela> findAllByOrderByColorAscAnchoAscAltoAsc();
}