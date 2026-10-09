package Colcones_Persinas.proyecto_express.servicio.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.CuentaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.MovimientoContable;
import Colcones_Persinas.proyecto_express.repository.almacen.PedidoTiendaVentasRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.CuentaContableRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.MovimientoAbonosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/**
 * Anota sola en contabilidad la plata de las compras pagadas en la tienda virtual (Wompi o Addi).
 *
 *  - Entra a la cuenta marcada en Contabilidad → Cuentas como "aquí entra la plata de la tienda".
 *    Si no hay ninguna marcada, no se anota: el pedido sale en Ventas con "Faltan" y el botón Anotar.
 *  - Se anota DESPUÉS de que el pago quedó guardado, y en su propia transacción: si algo falla
 *    aquí, el pago del cliente y su pedido en Almacén no se afectan (solo queda el error en el log).
 */
@Service
public class ContabilidadTiendaVirtual {

    private static final Logger log = LoggerFactory.getLogger(ContabilidadTiendaVirtual.class);

    private final CuentaContableRepository cuentaRepository;
    private final PedidoTiendaVentasRepository pedidoRepository;
    private final MovimientoAbonosRepository abonosRepository;
    private final ContabilidadServicio contabilidadServicio;
    private final TransactionTemplate transaccionAparte;

    public ContabilidadTiendaVirtual(CuentaContableRepository cuentaRepository,
                                     PedidoTiendaVentasRepository pedidoRepository,
                                     MovimientoAbonosRepository abonosRepository,
                                     ContabilidadServicio contabilidadServicio,
                                     PlatformTransactionManager transactionManager) {
        this.cuentaRepository = cuentaRepository;
        this.pedidoRepository = pedidoRepository;
        this.abonosRepository = abonosRepository;
        this.contabilidadServicio = contabilidadServicio;
        this.transaccionAparte = new TransactionTemplate(transactionManager);
        this.transaccionAparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Llamar justo después de crear en Almacén el pedido de una compra pagada.
     * El ingreso se anota cuando termine de guardarse el pago (no antes).
     *
     * @param medio "Wompi" o "Addi", para la descripción del ingreso
     */
    public void anotarCuandoSeGuarde(int pedidoId, String medio) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    anotarAhora(pedidoId, medio);
                }
            });
        } else {
            anotarAhora(pedidoId, medio);
        }
    }

    private void anotarAhora(int pedidoId, String medio) {
        try {
            transaccionAparte.executeWithoutResult(estado -> {
                CuentaContable cuenta = cuentaRepository.findFirstByRecibeTiendaTrue().orElse(null);
                if (cuenta == null || !cuenta.isActiva()) {
                    log.info("[Contabilidad] Pedido #{} de la tienda virtual: no hay cuenta elegida para la tienda; queda para anotar a mano en Ventas.", pedidoId);
                    return;
                }
                PedidoTienda pedido = pedidoRepository.findById(pedidoId).orElse(null);
                if (pedido == null) return;

                BigDecimal yaAnotado = abonosRepository.anotadoDelPedido(pedidoId);
                BigDecimal falta = nz(pedido.getAbono()).subtract(nz(yaAnotado));
                if (falta.signum() <= 0) return;   // ya estaba anotado (por ejemplo, si el aviso de pago llegó dos veces)

                MovimientoContable m = contabilidadServicio.registrarAbonoAlmacen(pedido, falta, cuenta.getId(), "tienda virtual");
                m.setDescripcion("Venta tienda virtual (" + (medio == null || medio.isBlank() ? "pago en línea" : medio)
                        + ") · pedido de Almacén #" + pedidoId);
            });
        } catch (Exception e) {
            log.error("[Contabilidad] No se pudo anotar la venta de la tienda virtual del pedido #{}. "
                    + "Se puede anotar a mano en Contabilidad → Ventas.", pedidoId, e);
        }
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}