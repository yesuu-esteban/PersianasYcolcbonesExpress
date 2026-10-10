package Colcones_Persinas.proyecto_express.repository.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.MovimientoTienda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface MovimientoTiendaRepository extends JpaRepository<MovimientoTienda, Integer> {

    /** Movimientos entre dos fechas (incluidas), los más nuevos primero. */
    List<MovimientoTienda> findByFechaBetweenOrderByFechaDescIdDesc(LocalDate desde, LocalDate hasta);

    boolean existsByOrdenId(Integer ordenId);

    boolean existsByCuentaId(int cuentaId);

    /** Total por cuenta y tipo, de todos los tiempos: filas [cuentaId, tipo, suma]. Sirve para los saldos. */
    @Query("select m.cuenta.id, m.tipo, sum(m.valor) from MovimientoTienda m group by m.cuenta.id, m.tipo")
    List<Object[]> totalesPorCuenta();
}