package Colcones_Persinas.proyecto_express.repository.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrdenTiendaRepository extends JpaRepository<OrdenTienda, Integer> {

    Optional<OrdenTienda> findByReferencia(String referencia);

    /**
     * Bloquea la fila mientras se procesa el pago. Wompi puede avisar del mismo pago
     * dos veces casi al mismo tiempo (aviso automático + regreso del cliente);
     * el bloqueo evita que se creen dos pedidos en Almacén.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdenTienda o where o.referencia = :referencia")
    Optional<OrdenTienda> bloquearPorReferencia(@Param("referencia") String referencia);

    List<OrdenTienda> findAllByOrderByFechaCreacionDesc();

    long countByEstado(String estado);

    // ── Listado de compras por páginas (pantalla "Compras en línea") ──

    /** Compras que están en alguno de esos estados, las más nuevas primero. */
    Page<OrdenTienda> findByEstadoInOrderByFechaCreacionDesc(Collection<String> estados, Pageable pagina);

    /** Todas las compras, las más nuevas primero. */
    Page<OrdenTienda> findAllByOrderByFechaCreacionDesc(Pageable pagina);

    long countByEstadoIn(Collection<String> estados);
}