package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.DatosVenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Factura, IVA, retención, costo, gasto… de cada venta de Almacén (una fila por pedido). */
public interface DatosVentaRepository extends JpaRepository<DatosVenta, Integer> {

    Optional<DatosVenta> findByPedidoTiendaId(Integer pedidoTiendaId);

    List<DatosVenta> findByPedidoTiendaIdIn(Collection<Integer> pedidoTiendaIds);
}