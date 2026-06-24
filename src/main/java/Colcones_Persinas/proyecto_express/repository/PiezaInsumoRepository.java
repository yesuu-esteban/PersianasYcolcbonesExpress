package Colcones_Persinas.proyecto_express.repository;

import Colcones_Persinas.proyecto_express.modelo.PiezaInsumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PiezaInsumoRepository extends JpaRepository<PiezaInsumo, Integer> {

    /**
     * Piezas de un insumo específico con material disponible, ordenadas
     * de menor a mayor sobrante (igual lógica que los rollos de tela:
     * se prioriza cerrar piezas casi agotadas antes de abrir una nueva).
     */
    List<PiezaInsumo> findByInsumoIdAndLargoRestanteGreaterThanOrderByLargoRestanteAsc(
            int insumoId, double minimo);

    List<PiezaInsumo> findByInsumoIdOrderByLargoRestanteAsc(int insumoId);
}