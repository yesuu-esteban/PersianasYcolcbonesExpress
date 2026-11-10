package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.fabrica.Insumo;
import Colcones_Persinas.proyecto_express.repository.fabrica.InsumoRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Insumos del Riel de Onda Serena (por unidad):
 *
 *  - "Tensor": solo lo usa el riel de CUERDA (con polea): 1 por riel.
 *  - "Freno Lateral": lo usa todo riel (cuerda o bastón):
 *                     2 si abre "Hacia los extremos", 1 si abre a un solo lado.
 *
 * Antes se llamaban "Pesa Riel" y "Tope Riel". Si existen con el nombre viejo se les
 * cambia el nombre (conservan su stock y su historial). Si no existen, se crean con stock 0.
 */
@Component
public class InsumoRielInicializador implements CommandLineRunner {

    private final InsumoRepository insumoRepository;

    public InsumoRielInicializador(InsumoRepository insumoRepository) {
        this.insumoRepository = insumoRepository;
    }

    @Override
    public void run(String... args) {
        renombrarOCrear("Pesa Riel", "Tensor",
                "Tensor del riel de onda serena. Solo cuando el riel es de cuerda (con polea): 1 por riel.");
        renombrarOCrear("Tope Riel", "Freno Lateral",
                "Freno lateral del riel de onda serena. En todo riel (cuerda o bastón): 2 si abre \"Hacia los extremos\", si no 1.");
    }

    private void renombrarOCrear(String nombreViejo, String nombreNuevo, String descripcion) {
        if (insumoRepository.findByNombreIgnoreCase(nombreNuevo).isPresent()) {
            Optional<Insumo> viejo = insumoRepository.findByNombreIgnoreCase(nombreViejo);
            if (viejo.isPresent()) {
                System.out.println("[InsumoRielInicializador] Ya existe \"" + nombreNuevo + "\" y también \""
                        + nombreViejo + "\". El riel usa \"" + nombreNuevo + "\"; si \"" + nombreViejo
                        + "\" tiene stock, páselo y borre \"" + nombreViejo + "\" en Inventario.");
            }
            return;
        }
        Optional<Insumo> viejo = insumoRepository.findByNombreIgnoreCase(nombreViejo);
        if (viejo.isPresent()) {
            Insumo insumo = viejo.get();
            insumo.setNombre(nombreNuevo);
            insumo.setDescripcion(descripcion);
            insumoRepository.save(insumo);
            System.out.println("[InsumoRielInicializador] \"" + nombreViejo + "\" ahora se llama \"" + nombreNuevo + "\" (conserva su stock).");
            return;
        }
        Insumo insumo = new Insumo();
        insumo.setNombre(nombreNuevo);
        insumo.setTieneMedida(false);
        insumo.setDescripcion(descripcion);
        insumo.setStockUnidades(0);
        insumoRepository.save(insumo);
        System.out.println("[InsumoRielInicializador] Creado el insumo \"" + nombreNuevo + "\" con stock 0. Cárgale stock en Inventario.");
    }
}