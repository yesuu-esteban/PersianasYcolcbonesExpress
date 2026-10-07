package Colcones_Persinas.proyecto_express.repository.fabrica;

import org.springframework.data.jpa.repository.JpaRepository;

import Colcones_Persinas.proyecto_express.modelo.fabrica.PiezaInsumo;

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