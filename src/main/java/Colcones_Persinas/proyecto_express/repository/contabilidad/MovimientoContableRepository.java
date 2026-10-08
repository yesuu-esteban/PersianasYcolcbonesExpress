package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.MovimientoContable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    List<MovimientoContable> findByPedidoTiendaIdOrderByFechaAscIdAsc(Integer pedidoTiendaId);
}