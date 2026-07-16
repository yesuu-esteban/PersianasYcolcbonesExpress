package Colcones_Persinas.proyecto_express.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import Colcones_Persinas.proyecto_express.modelo.PedidoTienda;

public interface PedidoTiendaRepository extends JpaRepository<PedidoTienda, Integer> {
}