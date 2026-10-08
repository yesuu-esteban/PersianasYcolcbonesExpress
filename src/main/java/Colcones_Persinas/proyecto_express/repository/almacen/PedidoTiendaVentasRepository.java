package Colcones_Persinas.proyecto_express.repository.almacen;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import org.springframework.data.repository.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Pedidos de Almacén vistos desde la contabilidad (pantalla "Ventas").
 * Va aparte de PedidoTiendaRepository para no tocar ese archivo; usa la misma tabla.
 */
public interface PedidoTiendaVentasRepository extends Repository<PedidoTienda, Integer> {

    /** Pedidos hechos entre dos momentos (por fecha del pedido), del más nuevo al más viejo. */
    List<PedidoTienda> findByFechaPedidoBetweenOrderByFechaPedidoDescIdDesc(LocalDateTime desde, LocalDateTime hasta);

    Optional<PedidoTienda> findById(Integer id);
}