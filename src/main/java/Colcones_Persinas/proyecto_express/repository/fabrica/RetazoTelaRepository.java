package Colcones_Persinas.proyecto_express.repository.fabrica;

import org.springframework.data.jpa.repository.JpaRepository;

import Colcones_Persinas.proyecto_express.modelo.fabrica.RetazoTela;

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