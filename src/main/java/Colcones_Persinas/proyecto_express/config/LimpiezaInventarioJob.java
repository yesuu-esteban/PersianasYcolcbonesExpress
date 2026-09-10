package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.PiezaInsumo;
import Colcones_Persinas.proyecto_express.modelo.RetazoTela;
import Colcones_Persinas.proyecto_express.modelo.RolloTela;
import Colcones_Persinas.proyecto_express.repository.PiezaInsumoRepository;
import Colcones_Persinas.proyecto_express.repository.RetazoTelaRepository;
import Colcones_Persinas.proyecto_express.repository.RolloTelaRepository;
import Colcones_Persinas.proyecto_express.servicio.MovimientoInventarioServicio;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Limpia automáticamente del inventario cualquier retazo, rollo o pieza de
 * insumo (tubería, cuerda, pesa, riel, etc.) que haya quedado tan pequeño
 * que ya no sirve para ningún pedido práctico. Corre todos los días a las
 * 3:00 AM.
 *
 * Los umbrales son deliberadamente los mismos que ya usa InventarioServicio
 * al descontar material de un pedido (UMBRAL_DESCARTE_RETAZO / UMBRAL_DESCARTE_PIEZA),
 * para que la limpieza automática sea consistente con ese criterio y no
 * elimine algo que el sistema seguiría considerando "usable" en un pedido nuevo.
 */
@Component
public class LimpiezaInventarioJob {

    private static final double UMBRAL_RETAZO = 0.05; // metros de alto
    private static final double UMBRAL_PIEZA  = 0.40; // metros restantes (tubería, cuerda, pesa, riel, etc.)

    private final RetazoTelaRepository retazoTelaRepository;
    private final RolloTelaRepository rolloTelaRepository;
    private final PiezaInsumoRepository piezaInsumoRepository;
    private final MovimientoInventarioServicio movimientoServicio;

    public LimpiezaInventarioJob(RetazoTelaRepository retazoTelaRepository,
                                  RolloTelaRepository rolloTelaRepository,
                                  PiezaInsumoRepository piezaInsumoRepository,
                                  MovimientoInventarioServicio movimientoServicio) {
        this.retazoTelaRepository = retazoTelaRepository;
        this.rolloTelaRepository = rolloTelaRepository;
        this.piezaInsumoRepository = piezaInsumoRepository;
        this.movimientoServicio = movimientoServicio;
    }

    /** Cron: todos los días a las 3:00:00 AM. */
    @Scheduled(cron = "0 0 3 * * *")
    public void limpiarInventario() {
        limpiarRetazos();
        limpiarRollosAgotados();
        limpiarPiezasPequenas();
    }

    private void limpiarRetazos() {
        List<RetazoTela> todos = retazoTelaRepository.findAll();
        for (RetazoTela r : todos) {
            if (r.getAlto() <= UMBRAL_RETAZO) {
                String desc = "Retazo " + r.getColor() + " " + r.getAncho() + "m × "
                        + r.getAlto() + "m (#" + r.getId() + ") — eliminado, quedaba demasiado pequeño.";
                retazoTelaRepository.delete(r);
                movimientoServicio.registrarLimpiezaAutomatica("RETAZO", desc);
            }
        }
    }

    private void limpiarRollosAgotados() {
        List<RolloTela> todos = rolloTelaRepository.findAll();
        for (RolloTela r : todos) {
            if (r.getLargoRestante() <= UMBRAL_PIEZA) {
                String desc = "Rollo " + r.getColor() + " " + r.getAncho() + "m (#" + r.getId()
                        + ") — eliminado, quedaban solo " + r.getLargoRestante() + " m.";
                rolloTelaRepository.delete(r);
                movimientoServicio.registrarLimpiezaAutomatica("ROLLO", desc);
            }
        }
    }

    private void limpiarPiezasPequenas() {
        List<PiezaInsumo> todas = piezaInsumoRepository.findAll();
        for (PiezaInsumo p : todas) {
            if (p.getLargoRestante() <= UMBRAL_PIEZA) {
                String nombreInsumo = p.getInsumo() != null ? p.getInsumo().getNombre() : "Insumo";
                String desc = nombreInsumo + " (#" + p.getId()
                        + ") — eliminado, quedaban solo " + p.getLargoRestante() + " m.";
                piezaInsumoRepository.delete(p);
                movimientoServicio.registrarLimpiezaAutomatica("INSUMO_PIEZA", desc);
            }
        }
    }
}