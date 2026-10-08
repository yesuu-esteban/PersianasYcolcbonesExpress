package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CuentaPorPagar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuentaPorPagarRepository extends JpaRepository<CuentaPorPagar, Integer> {

    List<CuentaPorPagar> findByPagadaFalseOrderByFechaVencimientoAscIdAsc();

    List<CuentaPorPagar> findTop30ByPagadaTrueOrderByFechaPagoDescIdDesc();

    List<CuentaPorPagar> findByMovimientoId(Integer movimientoId);
}