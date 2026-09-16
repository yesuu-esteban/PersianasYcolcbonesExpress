package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.PrecioItemFabricacion;
import Colcones_Persinas.proyecto_express.modelo.TipoCalculoPrecio;
import Colcones_Persinas.proyecto_express.repository.PrecioItemFabricacionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Crea, solo la primera vez (si no existen todavía), los ítems de precio
 * de fabricación conocidos hasta ahora. Después de creados, sus precios
 * se editan libremente desde la pantalla — este inicializador nunca
 * sobrescribe un valor ya existente, solo agrega los que falten.
 */
@Component
public class PrecioItemFabricacionInicializador implements CommandLineRunner {

    private final PrecioItemFabricacionRepository repositorio;

    public PrecioItemFabricacionInicializador(PrecioItemFabricacionRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void run(String... args) {
        crearSiNoExiste("Tela", TipoCalculoPrecio.POR_AREA, "11900",
                "Se calcula ancho × alto del corte de tela.");
        crearSiNoExiste("Tubo R16", TipoCalculoPrecio.POR_ANCHO, "7854",
                "Se calcula sobre el ancho del corte de tubo.");
        crearSiNoExiste("Pesa", TipoCalculoPrecio.POR_ANCHO, "5381",
                "Acompañante del tubo; se calcula sobre el mismo ancho.");
        crearSiNoExiste("Cuerda / Cadenilla (Blackout)", TipoCalculoPrecio.POR_LARGO, "424",
                "Se calcula sobre el largo de cuerda que ya usa el sistema (3 o 4 metros según altura).");
        crearSiNoExiste("Mecanismo (Control y accesorios)", TipoCalculoPrecio.FIJO, "5868",
                "Precio fijo por pedido, no se multiplica por ninguna medida.");
        crearSiNoExiste("Tapas", TipoCalculoPrecio.POR_CANTIDAD, "179",
                "Se multiplica por la cantidad de tapas que use el pedido.");
        crearSiNoExiste("Pitillo", TipoCalculoPrecio.POR_ANCHO, "952",
                "Se calcula sobre el ancho del corte.");
    }

    private void crearSiNoExiste(String nombre, TipoCalculoPrecio tipo, String precio, String descripcion) {
        if (repositorio.findByNombreIgnoreCase(nombre).isPresent()) return;

        PrecioItemFabricacion item = new PrecioItemFabricacion();
        item.setNombre(nombre);
        item.setTipoCalculo(tipo);
        item.setPrecioUnitario(new BigDecimal(precio));
        item.setDescripcion(descripcion);
        item.setActualizadoPor("sistema (inicial)");
        repositorio.save(item);
    }
}