package Colcones_Persinas.proyecto_express.repository.almacen;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import org.springframework.data.repository.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pedidos de Almacén que todavía deben plata (cuentas por cobrar de la contabilidad).
 * Va aparte de PedidoTiendaRepository para no tocar ese archivo; usa la misma tabla.
 */
public interface PedidoTiendaPorCobrarRepository extends Repository<PedidoTienda, Integer> {

    List<PedidoTienda> findBySaldoGreaterThanOrderByFechaPedidoAsc(BigDecimal cero);
}