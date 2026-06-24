package Colcones_Persinas.proyecto_express.repository;

import Colcones_Persinas.proyecto_express.modelo.MaterialUsado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MaterialUsadoRepository extends JpaRepository<MaterialUsado, Integer> {

    List<MaterialUsado> findByPedidoIdOrderByFechaAsc(int pedidoId);

    List<MaterialUsado> findByFechaBetweenOrderByFechaAsc(LocalDateTime desde, LocalDateTime hasta);
}