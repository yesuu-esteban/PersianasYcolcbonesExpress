package Colcones_Persinas.proyecto_express.repository;

import Colcones_Persinas.proyecto_express.modelo.RolloTela;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolloTelaRepository extends JpaRepository<RolloTela, Integer> {

    /**
     * Todos los rollos de un color y ancho específico que aún tengan
     * material disponible (largoRestante > 0), ordenados del que tiene
     * MENOS material al que tiene MÁS. Así el algoritmo puede recorrer
     * la lista y tomar el primero que alcance: el de menor sobrante posible.
     */
    List<RolloTela> findByColorAndAnchoAndLargoRestanteGreaterThanOrderByLargoRestanteAsc(
            String color, double ancho, double minimo);

    List<RolloTela> findByColorAndAnchoOrderByLargoRestanteAsc(String color, double ancho);

    List<RolloTela> findAllByOrderByColorAscAnchoAscLargoRestanteAsc();
}