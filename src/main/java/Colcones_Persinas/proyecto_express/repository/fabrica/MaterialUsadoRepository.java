package Colcones_Persinas.proyecto_express.repository.fabrica;

import org.springframework.data.jpa.repository.JpaRepository;

import Colcones_Persinas.proyecto_express.modelo.fabrica.MaterialUsado;

import java.time.LocalDateTime;
import java.util.List;

public interface MaterialUsadoRepository extends JpaRepository<MaterialUsado, Integer> {

    List<MaterialUsado> findByPedidoIdOrderByFechaAsc(int pedidoId);

    List<MaterialUsado> findByFechaBetweenOrderByFechaAsc(LocalDateTime desde, LocalDateTime hasta);
}