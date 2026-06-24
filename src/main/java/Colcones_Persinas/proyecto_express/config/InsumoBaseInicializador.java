package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.Insumo;
import Colcones_Persinas.proyecto_express.repository.InsumoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Crea los insumos básicos que el sistema necesita para poder verificar
 * material en cada pedido, únicamente si todavía no existen.
 */
@Component
public class InsumoBaseInicializador implements CommandLineRunner {

    private final InsumoRepository insumoRepository;

    public InsumoBaseInicializador(InsumoRepository insumoRepository) {
        this.insumoRepository = insumoRepository;
    }

    @Override
    public void run(String... args) {
        crearSiNoExiste("Tubo R16",              true,  "Tubo recomendado para pedidos livianos. Se carga por barras con medida.");
        crearSiNoExiste("Tubo R24",              true,  "Tubo recomendado para pedidos pesados o con cabezal. Se carga por barras con medida.");
        crearSiNoExiste("Cuerda 3 metros",       true,  "Cuerda para pedidos con altura <= 1.50 m.");
        crearSiNoExiste("Cuerda 4 metros",       true,  "Cuerda para pedidos con altura > 1.50 m.");
        crearSiNoExiste("Pesa",                  true,  "Pesa inferior, se corta a la misma medida que el tubo.");
        crearSiNoExiste("Control R16",           false, "Control para pedidos con ancho > 1.50 m. Se maneja por unidad.");
        crearSiNoExiste("Control R8 B",          false, "Control para pedidos con ancho <= 1.50 m. Se maneja por unidad.");
        crearSiNoExiste("Soporte",               false, "Soporte de instalación. Se usan 2 en todo pedido, con o sin cabezal.");
        crearSiNoExiste("Tapa",                  false, "Tapa de cabezal. Se usan 2 únicamente en pedidos CON cabezal.");
        crearSiNoExiste("Tope Pesa",             false, "Tope de pesa. Se usan 2 en todo pedido, con o sin cabezal.");
        crearSiNoExiste("Tornillo",              false, "Tornillo normal. Sin cabezal: 2 (soportes). Con cabezal: 8 (soportes + tapas).");
        crearSiNoExiste("Tornillo Perforante",   false, "Tornillo perforante. Solo en pedidos CON cabezal: 4 unidades.");
    }

    private void crearSiNoExiste(String nombre, boolean tieneMedida, String descripcion) {
        if (insumoRepository.findByNombreIgnoreCase(nombre).isEmpty()) {
            Insumo insumo = new Insumo();
            insumo.setNombre(nombre);
            insumo.setTieneMedida(tieneMedida);
            insumo.setDescripcion(descripcion);
            insumo.setStockUnidades(0);
            insumoRepository.save(insumo);
        }
    }
}