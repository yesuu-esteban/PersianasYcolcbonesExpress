package Colcones_Persinas.proyecto_express.config.inicializadores;

import Colcones_Persinas.proyecto_express.modelo.tienda.ProductoTienda;
import Colcones_Persinas.proyecto_express.modelo.tienda.TelaTienda;
import Colcones_Persinas.proyecto_express.repository.tienda.ProductoTiendaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Carga el catálogo inicial de la tienda SOLO si todavía no hay ningún producto.
 * Son los productos del almacén con precios DE EJEMPLO (los mismos por rollo que usa
 * fábrica: 1,83 = $51.200 · 2,50 = $53.800 · 3,00 = $74.250 por m²).
 * Ajusten precios, telas, colores y fotos desde /tienda-admin.
 */
@Component
public class TiendaInicializador implements CommandLineRunner {

    private final ProductoTiendaRepository repo;

    public TiendaInicializador(ProductoTiendaRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return;

        crear("Blackout enrollable", "PERSIANAS", 1, true,
                "Bloquea la luz por completo. Ideal para habitaciones.",
                "Persiana enrollable en tela blackout que no deja pasar la luz.\n"
                        + "Se fabrica a la medida exacta de tu ventana, con mando de cadena al lado que prefieras.\n"
                        + "Fácil de limpiar con un paño húmedo.",
                true,
                new TelaTienda("Blackout liso", "Blanco, Gris, Fawn, Vainilla", 51200, 53800, 74250));

        crear("Enrollable screen", "PERSIANAS", 2, true,
                "Filtra la luz y deja ver hacia afuera.",
                "Tela screen que controla el sol y el calor sin quitar la vista.\n"
                        + "Perfecta para salas, estudios y oficinas.",
                true,
                new TelaTienda("Screen 5%", "Blanco, Gris, Arena", 51200, 53800, 74250));

        crear("Sheer elegance", "PERSIANAS", 3, true,
                "Franjas que alternan luz y privacidad.",
                "Dos capas de franjas que se cruzan: ábrelas para dejar pasar la luz o ciérralas para tener privacidad.",
                true,
                new TelaTienda("Sheer", "Blanco, Gris, Vainilla", 51200, 53800, 74250));

        crear("Persiana vertical", "PERSIANAS", 4, false,
                "Lamas verticales para ventanales y puertas.",
                "Lamas que giran y se recogen hacia un lado. Muy prácticas para ventanales grandes y puertas de vidrio.",
                true,
                new TelaTienda("Lama en tela", "Blanco, Beige, Gris", 51200, 53800, 74250));

        crear("Mini persiana", "PERSIANAS", 5, false,
                "Láminas horizontales en aluminio.",
                "Láminas delgadas de aluminio que se inclinan para regular la luz. Resistentes y fáciles de limpiar.",
                true,
                new TelaTienda("Aluminio", "Blanco, Plata, Negro", 51200, 53800, 74250));

        crear("Macro madera", "PERSIANAS", 6, false,
                "Láminas anchas con acabado en madera.",
                "Láminas anchas con aspecto de madera que le dan calidez a cualquier espacio.",
                true,
                new TelaTienda("Madera", "Madera natural, Café, Blanco", 51200, 53800, 74250));

        crear("Panel japonés", "CORTINAS", 7, false,
                "Paneles de tela que se deslizan sobre un riel.",
                "Paneles de tela que se desplazan uno sobre otro. Ideales para ventanales amplios y para dividir espacios.",
                true,
                new TelaTienda("Tela panel", "Blanco, Gris, Beige", 51200, 53800, 74250));

        crear("Cortina de onda serena", "CORTINAS", 8, true,
                "Cortina con caída en ondas uniformes.",
                "Cortina en riel con ondas suaves y parejas, de aspecto elegante. Se abre hacia un lado o hacia los extremos.",
                false,
                new TelaTienda("Tela onda serena", "Blanco, Gris, Beige, Vainilla", 51200, 53800, 74250));

        crear("Toldo vertical", "TOLDOS", 9, false,
                "Protección del sol para terrazas y balcones.",
                "Toldo en tela resistente al exterior para terrazas, balcones y patios.",
                true,
                new TelaTienda("Lona exterior", "Gris, Beige, Arena", 51200, 53800, 74250));

        System.out.println("[TiendaInicializador] Catálogo inicial de la tienda creado con precios de ejemplo. "
                + "Ajústalos en /tienda-admin.");
    }

    private void crear(String nombre, String categoria, int orden, boolean destacado, String corta,
                       String descripcion, boolean conMando, TelaTienda... telas) {
        ProductoTienda p = new ProductoTienda();
        p.setNombre(nombre);
        p.setSlug(nombre.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""));
        p.setCategoria(categoria);
        p.setOrden(orden);
        p.setDestacado(destacado);
        p.setDescripcionCorta(corta);
        p.setDescripcion(descripcion);
        p.setTipoPrecio(ProductoTienda.PRECIO_M2);
        p.setM2Minimo(1.0);
        p.setConMando(conMando);
        p.setActivo(true);
        for (TelaTienda t : telas) p.agregarTela(t);
        repo.save(p);
    }
}