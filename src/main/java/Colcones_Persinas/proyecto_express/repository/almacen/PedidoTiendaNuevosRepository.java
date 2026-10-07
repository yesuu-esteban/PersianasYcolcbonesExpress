package Colcones_Persinas.proyecto_express.repository.almacen;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Consultas para el aviso de "pedidos nuevos de la tienda virtual" en Almacén.
 * Va aparte de PedidoTiendaRepository para no tocar ese archivo; usa la misma tabla.
 */
public interface PedidoTiendaNuevosRepository extends Repository<PedidoTienda, Integer> {

    /** Los 20 más recientes de ese vendedor que siguen en ese estado. */
    List<PedidoTienda> findTop20ByVendedorAndEstadoOrderByFechaPedidoDesc(String vendedor, String estado);

    long countByVendedorAndEstado(String vendedor, String estado);
}