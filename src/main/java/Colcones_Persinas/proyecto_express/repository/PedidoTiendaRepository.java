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

    /**
     * Devuelve todos los pedidos ordenados por fecha de pedido descendente
     * (el más nuevo primero); en caso de empate de fecha, se desempata por
     * ID descendente.
     */
    List<PedidoTienda> findAllByOrderByFechaPedidoDescIdDesc();
}