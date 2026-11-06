package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.fabrica.Insumo;
import Colcones_Persinas.proyecto_express.repository.fabrica.InsumoRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Crea en el inventario los insumos nuevos del Riel de Onda Serena (si no existen):
 *
 *  - "Pesa Riel": por unidad. Solo la usa el riel de CUERDA (con polea): 1 por riel.
 *  - "Tope Riel": por unidad. La usa todo riel (cuerda o bastón):
 *                 2 si abre "Hacia los extremos", 1 si abre a un solo lado.
 *
 * Se crean con stock 0: hay que cargarles stock en Inventario antes de hacer rieles.
 * Si ya existen no se toca nada.
 */
@Component
public class InsumoRielInicializador implements CommandLineRunner {

    private final InsumoRepository insumoRepository;

    public InsumoRielInicializador(InsumoRepository insumoRepository) {
        this.insumoRepository = insumoRepository;
    }

    @Override
    public void run(String... args) {
        crearSiNoExiste("Pesa Riel", "Pesa del riel de onda serena. Solo cuando el riel es de cuerda (con polea): 1 por riel.");
        crearSiNoExiste("Tope Riel", "Tope del riel de onda serena. En todo riel (cuerda o bastón): 2 si abre \"Hacia los extremos\", si no 1.");
    }

    private void crearSiNoExiste(String nombre, String descripcion) {
        if (insumoRepository.findByNombreIgnoreCase(nombre).isPresent()) return;
        Insumo insumo = new Insumo();
        insumo.setNombre(nombre);
        insumo.setTieneMedida(false);
        insumo.setDescripcion(descripcion);
        insumo.setStockUnidades(0);
        insumoRepository.save(insumo);
        System.out.println("[InsumoRielInicializador] Creado el insumo \"" + nombre + "\" con stock 0. Cárgale stock en Inventario.");
    }
}