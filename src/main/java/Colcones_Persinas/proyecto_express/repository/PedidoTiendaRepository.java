package Colcones_Persinas.proyecto_express.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import Colcones_Persinas.proyecto_express.modelo.PedidoTienda;

import java.util.List;

public interface PedidoTiendaRepository extends JpaRepository<PedidoTienda, Integer> {

    /**
     * Devuelve todos los pedidos ordenados por ID ascendente, para que la posición
     * de cada pedido en el listado sea siempre estable (no depende del orden de
     * inserción/actualización interno de la base de datos).
     */
    List<PedidoTienda> findAllByOrderByIdAsc();
}