package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.MovimientoContable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Los ingresos de la contabilidad que salieron de abonos de pedidos de Almacén
 * (los que tienen pedidoTiendaId). Sirve para saber qué abonos ya están anotados.
 */
public interface MovimientoAbonosRepository extends Repository<MovimientoContable, Integer> {

    /** Los ingresos (abonos) anotados de varios pedidos, del más viejo al más nuevo. */
    List<MovimientoContable> findByTipoAndPedidoTiendaIdInOrderByFechaAscIdAsc(String tipo, Collection<Integer> pedidoIds);

    /** Lo que ya está anotado en contabilidad para un pedido (null si nada). */
    @Query("select sum(m.valor) from MovimientoContable m where m.tipo = 'INGRESO' and m.pedidoTiendaId = :id")
    BigDecimal anotadoDelPedido(@Param("id") Integer id);

    /** Abonos de Almacén que entraron a las cuentas entre dos fechas (null si nada). */
    @Query("select sum(m.valor) from MovimientoContable m where m.tipo = 'INGRESO' "
            + "and m.pedidoTiendaId is not null and m.fecha between :desde and :hasta")
    BigDecimal abonosEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}