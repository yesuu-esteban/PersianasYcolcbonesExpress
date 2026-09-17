package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.PrecioItemFabricacion;
import Colcones_Persinas.proyecto_express.modelo.TipoCalculoPrecio;
import Colcones_Persinas.proyecto_express.repository.PrecioItemFabricacionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

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
        crearSiNoExiste("Tapas de Perfil (Pesa)", TipoCalculoPrecio.POR_CANTIDAD, "179",
                "Obligatorias en todo pedido: 2 tapas del perfil de la pesa, con o sin cabezal. (Fusiona lo que antes era 'Tope Pesa'.)");
        crearSiNoExiste("Cuerda / Cadenilla (Blackout)", TipoCalculoPrecio.POR_LARGO, "424",
                "Se calcula sobre el largo de cuerda que ya usa el sistema (3 o 4 metros según altura).");
        crearSiNoExiste("Mecanismo (Control y accesorios)", TipoCalculoPrecio.FIJO, "5868",
                "Precio fijo por pedido: incluye el control completo, terminal, conectores y topes de la cadenilla en un solo cobro. Aplica a Control R16, R8 A y R8 B.");
        crearSiNoExiste("Mecanismo (Control y accesorios) R24", TipoCalculoPrecio.FIJO, "19200",
                "Precio fijo por pedido, específico para Control R24 (mecanismo especial con soportes más grandes).");
        crearSiNoExiste("Tapa Cabezal", TipoCalculoPrecio.POR_CANTIDAD, "179",
                "Tapas del cabezal. Solo aplica cuando el pedido lleva cabezal; se multiplica por la cantidad de tapas de cabezal.");
        crearSiNoExiste("Pitillo", TipoCalculoPrecio.POR_ANCHO, "952",
                "Se calcula sobre el ancho del corte.");

        migrarNombrePrecio("Tapas", "Tapa Cabezal");

        // ── FIX: "Tope Pesa" se fusiona/elimina como ítem de precio,
        // ya que es el mismo accesorio que "Tapas de Perfil (Pesa)". ──
        eliminarPrecio("Tope Pesa");
    }

    private void eliminarPrecio(String nombre) {
        Optional<PrecioItemFabricacion> item = repositorio.findByNombreIgnoreCase(nombre);
        if (item.isPresent()) {
            repositorio.delete(item.get());
            System.out.println("[PrecioItemFabricacionInicializador] Eliminado el ítem de precio \"" + nombre
                    + "\" (fusionado con \"Tapas de Perfil (Pesa)\").");
        }
    }

    private void migrarNombrePrecio(String nombreViejo, String nombreNuevo) {
        Optional<PrecioItemFabricacion> viejo = repositorio.findByNombreIgnoreCase(nombreViejo);
        if (viejo.isPresent() && repositorio.findByNombreIgnoreCase(nombreNuevo).isEmpty()) {
            PrecioItemFabricacion item = viejo.get();
            item.setNombre(nombreNuevo);
            repositorio.save(item);
            System.out.println("[PrecioItemFabricacionInicializador] Renombrado \"" + nombreViejo + "\" a \"" + nombreNuevo + "\".");
        }
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