package Colcones_Persinas.proyecto_express.config.inicializadores;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.OrdenTienda;
import Colcones_Persinas.proyecto_express.repository.almacen.PedidoTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.MovimientoAbonosRepository;
import Colcones_Persinas.proyecto_express.repository.tienda.OrdenTiendaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Pasa al módulo de la tienda las compras en línea que se hicieron cuando la tienda todavía
 * creaba sus pedidos en Almacén.
 *
 * A cada compra pagada que aún no tiene estado de pedido le pone el estado equivalente al que
 * tenía su pedido en Almacén:
 *   Pendiente → Nuevo · Pedido → En fabricación · En Bodega → Listo · Instalado / Terminado → Entregado
 * Si el pedido de Almacén ya no existe, queda en Nuevo.
 *
 * Además, si la plata de esa compra ya estaba anotada en la contabilidad de Almacén, la marca
 * (ventaEnContaAlmacen) para que la contabilidad de la tienda no la pida anotar otra vez.
 * Las que no estaban anotadas en ningún lado salen en la contabilidad de la tienda como
 * "Ventas sin anotar".
 *
 * Solo toca compras SIN estado de pedido, así que corre una sola vez de verdad: en los
 * siguientes arranques no encuentra nada que hacer. No borra nada de Almacén: esos pedidos
 * viejos solo dejan de mostrarse allá.
 */
@Component
public class TiendaSeparadaInicializador implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TiendaSeparadaInicializador.class);

    private final OrdenTiendaRepository ordenRepository;
    private final PedidoTiendaRepository pedidoAlmacenRepository;
    private final MovimientoAbonosRepository abonosAlmacenRepository;

    public TiendaSeparadaInicializador(OrdenTiendaRepository ordenRepository,
                                       PedidoTiendaRepository pedidoAlmacenRepository,
                                       MovimientoAbonosRepository abonosAlmacenRepository) {
        this.ordenRepository = ordenRepository;
        this.pedidoAlmacenRepository = pedidoAlmacenRepository;
        this.abonosAlmacenRepository = abonosAlmacenRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<OrdenTienda> sinEstado = ordenRepository.findByEstadoAndEstadoPedidoIsNull(OrdenTienda.APROBADA);
        if (sinEstado.isEmpty()) return;

        LocalDateTime ahora = LocalDateTime.now(OrdenTienda.ZONA_COLOMBIA);
        for (OrdenTienda o : sinEstado) {
            String estadoAlmacen = null;
            if (o.getPedidoTiendaId() != null) {
                estadoAlmacen = pedidoAlmacenRepository.findById(o.getPedidoTiendaId())
                        .map(PedidoTienda::getEstado).orElse(null);
                BigDecimal anotado = abonosAlmacenRepository.anotadoDelPedido(o.getPedidoTiendaId());
                if (anotado != null && anotado.signum() > 0) o.setVentaEnContaAlmacen(Boolean.TRUE);
            }
            o.setEstadoPedido(estadoSegunAlmacen(estadoAlmacen));
            o.setFechaEstadoPedido(ahora);
            ordenRepository.save(o);
        }
        log.info("[Tienda] {} compra(s) en línea pasaron al módulo de la tienda con su estado de pedido.", sinEstado.size());
    }

    /** El estado de Almacén convertido al estado del pedido de la tienda. */
    static String estadoSegunAlmacen(String estadoAlmacen) {
        if (estadoAlmacen == null) return OrdenTienda.NUEVO;
        switch (estadoAlmacen.trim().toLowerCase(Locale.ROOT)) {
            case "pedido":
                return OrdenTienda.EN_FABRICACION;
            case "en bodega":
                return OrdenTienda.LISTO;
            case "instalado":
            case "terminado":
                return OrdenTienda.ENTREGADO;
            default:
                return OrdenTienda.NUEVO;
        }
    }
}