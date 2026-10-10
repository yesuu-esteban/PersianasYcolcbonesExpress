package Colcones_Persinas.proyecto_express.repository.tienda;

import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrdenTiendaRepository extends JpaRepository<OrdenTienda, Integer> {

    Optional<OrdenTienda> findByReferencia(String referencia);

    /**
     * Bloquea la fila mientras se procesa el pago. Wompi puede avisar del mismo pago
     * dos veces casi al mismo tiempo (aviso automático + regreso del cliente);
     * el bloqueo evita que el pago se procese dos veces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdenTienda o where o.referencia = :referencia")
    Optional<OrdenTienda> bloquearPorReferencia(@Param("referencia") String referencia);

    List<OrdenTienda> findAllByOrderByFechaCreacionDesc();

    long countByEstado(String estado);

    long countByEstadoIn(Collection<String> estados);

    // ── Pantalla "Pedidos" de la tienda ──

    /**
     * Pedidos por páginas, los más nuevos primero.
     *  estados        estados del PAGO que se quieren ver (APROBADA, PENDIENTE...)
     *  estadosPedido  estados del PEDIDO (NUEVO, LISTO...). Para no filtrar, se mandan todos.
     *  q              "%" para no buscar, o "%texto%" en minúsculas para buscar por nombre,
     *                 cédula, celular, ciudad o número de pedido
     * Una compra sin estado de pedido guardado cuenta como NUEVO.
     */
    @Query(value = "select o from OrdenTienda o where o.estado in :estados "
            + "and coalesce(o.estadoPedido, 'NUEVO') in :estadosPedido "
            + "and (lower(o.nombreCliente) like :q or lower(o.cedula) like :q or lower(o.telefono) like :q "
            + "     or lower(o.ciudad) like :q or lower(o.referencia) like :q) "
            + "order by coalesce(o.fechaPago, o.fechaCreacion) desc, o.id desc",
           countQuery = "select count(o) from OrdenTienda o where o.estado in :estados "
            + "and coalesce(o.estadoPedido, 'NUEVO') in :estadosPedido "
            + "and (lower(o.nombreCliente) like :q or lower(o.cedula) like :q or lower(o.telefono) like :q "
            + "     or lower(o.ciudad) like :q or lower(o.referencia) like :q)")
    Page<OrdenTienda> buscar(@Param("estados") Collection<String> estados,
                             @Param("estadosPedido") Collection<String> estadosPedido,
                             @Param("q") String q,
                             Pageable pagina);

    /** Cuántas compras pagadas hay en cada estado del pedido: filas [estado (puede venir vacío), cantidad]. */
    @Query("select o.estadoPedido, count(o) from OrdenTienda o where o.estado = 'APROBADA' group by o.estadoPedido")
    List<Object[]> contarPorEstadoPedido();

    /** Pedidos pagados que siguen en Nuevo (para el aviso del portal y del menú). */
    @Query("select o from OrdenTienda o where o.estado = 'APROBADA' and coalesce(o.estadoPedido, 'NUEVO') = 'NUEVO' "
            + "order by o.fechaPago desc, o.id desc")
    List<OrdenTienda> pedidosNuevos(Pageable limite);

    @Query("select count(o) from OrdenTienda o where o.estado = 'APROBADA' and coalesce(o.estadoPedido, 'NUEVO') = 'NUEVO'")
    long contarPedidosNuevos();

    /** Compras pagadas a las que todavía no se les guardó estado de pedido (para pasarlas al módulo de la tienda). */
    List<OrdenTienda> findByEstadoAndEstadoPedidoIsNull(String estado);

    // ── Contabilidad de la tienda ──

    /** Compras pagadas entre dos momentos (por fecha de pago). */
    List<OrdenTienda> findByEstadoAndFechaPagoBetweenOrderByFechaPagoDesc(String estado, LocalDateTime desde, LocalDateTime hasta);

    /**
     * Compras pagadas (y no canceladas) cuya plata todavía no está anotada en la contabilidad de la tienda.
     * No incluye las de antes de separar la tienda que ya quedaron anotadas en la contabilidad de Almacén.
     */
    @Query("select o from OrdenTienda o where o.estado = 'APROBADA' and coalesce(o.estadoPedido, 'NUEVO') <> 'CANCELADO' "
            + "and (o.ventaEnContaAlmacen is null or o.ventaEnContaAlmacen = false) "
            + "and not exists (select m.id from MovimientoTienda m where m.ordenId = o.id) "
            + "order by o.fechaPago desc, o.id desc")
    List<OrdenTienda> ventasSinAnotar(Pageable limite);

    @Query("select count(o) from OrdenTienda o where o.estado = 'APROBADA' and coalesce(o.estadoPedido, 'NUEVO') <> 'CANCELADO' "
            + "and (o.ventaEnContaAlmacen is null or o.ventaEnContaAlmacen = false) "
            + "and not exists (select m.id from MovimientoTienda m where m.ordenId = o.id)")
    long contarVentasSinAnotar();
}