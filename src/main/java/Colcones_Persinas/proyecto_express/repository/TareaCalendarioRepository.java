package Colcones_Persinas.proyecto_express.repository;

import Colcones_Persinas.proyecto_express.modelo.TareaCalendario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TareaCalendarioRepository extends JpaRepository<TareaCalendario, Integer> {

    List<TareaCalendario> findByFechaProgramadaBetweenOrderByFechaProgramadaAsc(
            LocalDateTime desde, LocalDateTime hasta);

    /** Tareas donde participa un instalador (como principal o como segundo). */
    List<TareaCalendario> findDistinctByInstaladoresIdAndFechaProgramadaBetweenOrderByFechaProgramadaAsc(
            int instaladorId, LocalDateTime desde, LocalDateTime hasta);

    /** Para detectar cruces de horario en la agenda de un instalador. */
    List<TareaCalendario> findDistinctByInstaladoresIdAndEstadoInAndFechaProgramadaBetween(
            int instaladorId, Collection<String> estados, LocalDateTime desde, LocalDateTime hasta);

    /** Instalaciones activas con pedido ligado (para saber qué pedidos en bodega ya están asignados). */
    List<TareaCalendario> findByTipoAndEstadoInAndPedidoTiendaIsNotNull(String tipo, Collection<String> estados);

    List<TareaCalendario> findByPedidoTiendaIdAndTipoAndEstadoIn(
            int pedidoTiendaId, String tipo, Collection<String> estados);

    boolean existsByInstaladoresId(int instaladorId);

    /**
     * Al eliminar un pedido de almacén, sus tareas NO se borran (son historial):
     * solo se desligan. Cliente, dirección y teléfono ya quedaron copiados en la tarea.
     */
    @Modifying
    @Transactional
    @Query("update TareaCalendario t set t.pedidoTienda = null where t.pedidoTienda.id = :pedidoId")
    int desvincularPedido(@Param("pedidoId") int pedidoId);
}