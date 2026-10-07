package Colcones_Persinas.proyecto_express.repository.fabrica;

import org.springframework.data.jpa.repository.JpaRepository;

import Colcones_Persinas.proyecto_express.modelo.fabrica.MovimientoInventario;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Integer> {
    List<MovimientoInventario> findAllByOrderByFechaDesc(Pageable pageable);

    /** Todos los movimientos dentro de un rango de fechas, ordenados cronológicamente. Usado por el reporte Excel. */
    List<MovimientoInventario> findByFechaBetweenOrderByFechaAsc(LocalDateTime desde, LocalDateTime hasta);
}