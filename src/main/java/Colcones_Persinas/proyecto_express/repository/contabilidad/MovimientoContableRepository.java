package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CuentaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.MovimientoContable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MovimientoContableRepository extends JpaRepository<MovimientoContable, Integer> {

    List<MovimientoContable> findByFechaBetweenOrderByFechaDescIdDesc(LocalDate desde, LocalDate hasta);

    /** [cuentaId, tipo, suma] de todos los movimientos, para calcular saldos. */
    @Query("select m.cuenta.id, m.tipo, sum(m.valor) from MovimientoContable m group by m.cuenta.id, m.tipo")
    List<Object[]> sumasPorCuentaYTipo();

    /** [cuentaDestinoId, suma] de los traslados que entran a cada cuenta. */
    @Query("select m.cuentaDestino.id, sum(m.valor) from MovimientoContable m "
            + "where m.tipo = 'TRASLADO' and m.cuentaDestino is not null group by m.cuentaDestino.id")
    List<Object[]> trasladosEntrantes();

    /** [cuentaId, tipo, suma] de los movimientos desde una fecha (la fecha de inicio de la contabilidad). */
    @Query("select m.cuenta.id, m.tipo, sum(m.valor) from MovimientoContable m "
            + "where m.fecha >= :desde group by m.cuenta.id, m.tipo")
    List<Object[]> sumasPorCuentaYTipoDesde(@Param("desde") LocalDate desde);

    /** [cuentaDestinoId, suma] de los traslados que entran a cada cuenta, desde una fecha. */
    @Query("select m.cuentaDestino.id, sum(m.valor) from MovimientoContable m "
            + "where m.tipo = 'TRASLADO' and m.cuentaDestino is not null and m.fecha >= :desde group by m.cuentaDestino.id")
    List<Object[]> trasladosEntrantesDesde(@Param("desde") LocalDate desde);

    List<MovimientoContable> findByPedidoTiendaIdOrderByFechaAscIdAsc(Integer pedidoTiendaId);

    /** Los pagos (abonos) de una deuda de "Por pagar", del primero al último. */
    List<MovimientoContable> findByPorPagarIdOrderByFechaAscIdAsc(Integer porPagarId);

    /** Los pagos de varias deudas (para mostrarlos en la pantalla Por pagar). */
    List<MovimientoContable> findByPorPagarIdInOrderByFechaAscIdAsc(List<Integer> porPagarIds);

    /** ¿Cuántos movimientos tiene la cuenta (como origen o como destino)? Si tiene, no se puede eliminar. */
    @Query("select count(m) from MovimientoContable m where m.cuenta = :c or m.cuentaDestino = :c")
    long contarDeCuenta(@Param("c") CuentaContable cuenta);
}