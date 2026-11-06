package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.fabrica.Insumo;
import Colcones_Persinas.proyecto_express.repository.fabrica.InsumoRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Crea en el inventario los controles nuevos y lo que va con ellos (si no existen):
 *
 *  - "Control R16 Gris"  → se descuenta con "Terminal Gris" y la "Cuerda" normal.
 *  - "Control R16 Negro" → se descuenta con "Terminal Negro" y "Cadenilla Negra" (en vez de la cuerda).
 *
 * Los controles y terminales son por unidad; la cadenilla negra es por medida (piezas en metros).
 * Se crean con stock 0: hay que cargarles stock en Inventario. Si ya existen no se toca nada.
 */
@Component
public class InsumoControlesInicializador implements CommandLineRunner {

    private final InsumoRepository insumoRepository;

    public InsumoControlesInicializador(InsumoRepository insumoRepository) {
        this.insumoRepository = insumoRepository;
    }

    @Override
    public void run(String... args) {
        crearSiNoExiste("Control R16 Gris", false, "Control R16 gris. Va con Terminal Gris y la cuerda normal.");
        crearSiNoExiste("Control R16 Negro", false, "Control R16 negro. Va con Terminal Negro y Cadenilla Negra.");
        crearSiNoExiste("Terminal Gris", false, "Terminal gris: se descuenta cuando el control es Control R16 Gris.");
        crearSiNoExiste("Terminal Negro", false, "Terminal negro: se descuenta cuando el control es Control R16 Negro.");
        crearSiNoExiste("Cadenilla Negra", true, "Cadenilla negra (blackout): se usa en lugar de la cuerda cuando el control es Control R16 Negro. Por medida.");
    }

    private void crearSiNoExiste(String nombre, boolean tieneMedida, String descripcion) {
        if (insumoRepository.findByNombreIgnoreCase(nombre).isPresent()) return;
        Insumo insumo = new Insumo();
        insumo.setNombre(nombre);
        insumo.setTieneMedida(tieneMedida);
        insumo.setDescripcion(descripcion);
        insumo.setStockUnidades(0);
        insumoRepository.save(insumo);
        System.out.println("[InsumoControlesInicializador] Creado el insumo \"" + nombre + "\". Cárgale stock en Inventario.");
    }
}