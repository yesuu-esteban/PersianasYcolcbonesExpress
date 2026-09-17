package Colcones_Persinas.proyecto_express.config;

import Colcones_Persinas.proyecto_express.modelo.Insumo;
import Colcones_Persinas.proyecto_express.repository.InsumoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
        crearSiNoExiste("Tubo R8",    true,  "Tubo pequeño. Se carga por barras con medida.");
        crearSiNoExiste("Pesa",                  true,  "Pesa inferior, se corta a la misma medida que el tubo.");
        crearSiNoExiste("Control R16",           false, "Control para pedidos con ancho > 1.50 m. Se maneja por unidad.");
        crearSiNoExiste("Control R24",           false, "Control especial para pedidos con corte de tela >= 2.00m ancho y >= 2.50m largo. Soportes más grandes. Se maneja por unidad.");
        crearSiNoExiste("Acople",                false, "Acople necesario cuando el ancho de corte es >= 2.00m (2 por pedido), sin importar qué control termine asignado. Se maneja por unidad.");
        crearSiNoExiste("Terminal",              false, "Terminal acompañante del control. Obligatorio en TODO pedido de fabricación, sin importar ancho, alto o tipo de control/tubo. Se maneja por unidad.");
        crearSiNoExiste("Control R8 A", false, "Control para pedidos con Tubo R8. Se maneja por unidad.");
        crearSiNoExiste("Control R8 B",          false, "Control para pedidos con ancho <= 1.50 m. Se maneja por unidad.");
        crearSiNoExiste("Soporte",               false, "Soporte de instalación. Se usan 2 en todo pedido, con o sin cabezal.");
        crearSiNoExiste("Tapa Cabezal",          false, "Tapa de cabezal. Se usan 2 únicamente en pedidos CON cabezal.");
        crearSiNoExiste("Tapa Perfil",           false, "Tapas de perfil de la pesa. Se usan 2 en TODO pedido de fabricación, con o sin cabezal. (Fusiona lo que antes era 'Tope Pesa': mismo accesorio físico.)");
        crearSiNoExiste("Tornillo",              false, "Tornillo normal. Sin cabezal: 2 (soportes). Con cabezal: 8 (soportes + tapas).");
        crearSiNoExiste("Tornillo Perforante",   false, "Tornillo perforante. Solo en pedidos CON cabezal: 4 unidades.");

        crearSiNoExiste("Polea",                 false, "Polea del riel de onda serena. Obligatoria (2 por pedido) cuando el riel lleva polea.");
        crearSiNoExiste("Crusador",              false, "Terminal/control de la polea del riel de onda serena (antes 'Terminal Control Polea'). Obligatorio (1 por pedido) cuando el riel lleva polea.");
        crearSiNoExiste("Tapa Riel",             false, "Tapa de riel de onda serena. Obligatoria (2 por pedido) cuando el riel NO lleva polea.");
        crearSiNoExiste("Roachina",              true,  "Riel de pines del riel de onda serena (antes 'Riel de Pines'). Por medida, obligatoria siempre.");
        crearSiNoExiste("Riata",                 true,  "Riata del riel de onda serena. Por medida, se corta al mismo ancho que el riel. Obligatoria SIEMPRE, con o sin polea.");
        crearSiNoExiste("Soporte Riel", false, "Soporte de instalación del riel de onda serena. Cantidad variable según el ancho del pedido.");

        crearSiNoExiste("Bastón 0.80", false, "Bastón fijo de 0.80 m del riel de onda serena. Se descuenta como unidad completa, no se corta.");
        crearSiNoExiste("Bastón 1.20", false, "Bastón fijo de 1.20 m del riel de onda serena. Se descuenta como unidad completa, no se corta.");
        crearSiNoExiste("Bastón 1.50", false, "Bastón fijo de 1.50 m del riel de onda serena. Se descuenta como unidad completa, no se corta.");

        // ── Migraciones de nombres antiguos ──
        migrarNombreInsumo("Bastón Tipo A", "Bastón 0.80");
        migrarNombreInsumo("Bastón Tipo B", "Bastón 1.20");
        migrarNombreInsumo("Bastón Tipo C", "Bastón 1.50");
        corregirBastonAPorUnidad("Bastón 0.80");
        corregirBastonAPorUnidad("Bastón 1.20");
        corregirBastonAPorUnidad("Bastón 1.50");
        migrarNombreInsumo("Tapa", "Tapa Cabezal");

        // ── FIX: "Tope Pesa" se fusiona con "Tapa Perfil" (eran el mismo
        // accesorio físico). Si "Tope Pesa" existía con stock, ese stock se
        // suma al de "Tapa Perfil" para no perder inventario físico real, y
        // luego se elimina el insumo "Tope Pesa". ──
        fusionarTopePesaConTapaPerfil();
    }

    private void fusionarTopePesaConTapaPerfil() {
        Optional<Insumo> topePesaOpt = insumoRepository.findByNombreIgnoreCase("Tope Pesa");
        if (topePesaOpt.isEmpty()) return;

        Insumo topePesa = topePesaOpt.get();
        Optional<Insumo> tapaPerfilOpt = insumoRepository.findByNombreIgnoreCase("Tapa Perfil");

        if (tapaPerfilOpt.isPresent()) {
            Insumo tapaPerfil = tapaPerfilOpt.get();
            int stockTope = topePesa.getStockUnidades() != null ? topePesa.getStockUnidades() : 0;
            int stockTapa = tapaPerfil.getStockUnidades() != null ? tapaPerfil.getStockUnidades() : 0;
            tapaPerfil.setStockUnidades(stockTope + stockTapa);
            insumoRepository.save(tapaPerfil);
            System.out.println("[InsumoBaseInicializador] Fusionado \"Tope Pesa\" (stock " + stockTope
                    + ") dentro de \"Tapa Perfil\" (stock ahora: " + (stockTope + stockTapa) + "). Eliminando \"Tope Pesa\".");
        }
        insumoRepository.delete(topePesa);
    }

    private void migrarNombreInsumo(String nombreViejo, String nombreNuevo) {
        Optional<Insumo> viejo = insumoRepository.findByNombreIgnoreCase(nombreViejo);
        if (viejo.isPresent() && insumoRepository.findByNombreIgnoreCase(nombreNuevo).isEmpty()) {
            Insumo insumo = viejo.get();
            insumo.setNombre(nombreNuevo);
            insumoRepository.save(insumo);
            System.out.println("[InsumoBaseInicializador] Renombrado \"" + nombreViejo + "\" a \"" + nombreNuevo + "\".");
        }
    }

    private void corregirBastonAPorUnidad(String nombre) {
        Optional<Insumo> existente = insumoRepository.findByNombreIgnoreCase(nombre);
        if (existente.isPresent()) {
            Insumo insumo = existente.get();
            if (Boolean.TRUE.equals(insumo.getTieneMedida())) {
                insumo.setTieneMedida(false);
                if (insumo.getStockUnidades() == null) {
                    insumo.setStockUnidades(0);
                }
                insumoRepository.save(insumo);
                System.out.println("[InsumoBaseInicializador] Corregido \"" + nombre
                        + "\": ahora es por UNIDAD. Revisa /inventario/insumo/" + insumo.getId()
                        + "/cargar para cargarle stock si hace falta.");
            }
        }
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