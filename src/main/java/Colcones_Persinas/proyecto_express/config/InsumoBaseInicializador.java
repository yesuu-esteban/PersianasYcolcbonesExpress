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
        crearSiNoExiste("Control R24",           false, "Control especial para pedidos con corte de tela >= 2.00m ancho y >= 2.50m largo. [YA NO SE DESCUENTA DIRECTAMENTE: se dejó en catálogo por compatibilidad histórica; ahora se usa \"Paquete de Control R24\", que trae todo empacado junto].");
        crearSiNoExiste("Paquete de Control R24", false, "Combo completo para pedidos con Control R24: incluye el control, terminal, acoples y soportes (más grandes) en un solo paquete físico. Se descuenta 1 unidad por pedido, en vez de descontar cada pieza por separado.");
        crearSiNoExiste("Acople",                false, "Acople necesario cuando el ancho de corte es >= 2.00m (2 por pedido). Solo aplica a Control R16, R8 A y R8 B — Control R24 usa su propio Paquete. Se maneja por unidad.");
        crearSiNoExiste("Terminal",              false, "Terminal acompañante del control. Obligatorio en pedidos de fabricación que NO usan Control R24 (ese ya trae su propio terminal dentro del Paquete). Se maneja por unidad.");
        crearSiNoExiste("Control R8 A", false, "Control para pedidos con Tubo R8. Se maneja por unidad.");
        crearSiNoExiste("Control R8 B",          false, "Control para pedidos con ancho <= 1.50 m. Se maneja por unidad.");
        crearSiNoExiste("Soporte",               false, "Soporte de instalación. Se usan 2 en todo pedido de fabricación que NO use Control R24 (ese ya trae sus propios soportes, más grandes, dentro del Paquete).");
        crearSiNoExiste("Tapa Cabezal",          false, "Tapa de cabezal. Se usan 2 únicamente en pedidos CON cabezal.");
        crearSiNoExiste("Tapa Perfil",           false, "Tapas de perfil de la pesa. Se usan 2 en TODO pedido de fabricación, con o sin cabezal. (Fusiona lo que antes era 'Tope Pesa': mismo accesorio físico.)");
        crearSiNoExiste("Tornillo",              false, "Tornillo normal. Sin cabezal: 2 (soportes). Con cabezal: 8 (soportes + tapas).");
        crearSiNoExiste("Tornillo Perforante",   false, "Tornillo perforante. Solo en pedidos CON cabezal: 4 unidades.");

        // ── Riel de Onda Serena ──────────────────────────────────────────
        crearSiNoExiste("Polea",                 false, "Polea del riel de onda serena. Obligatoria (2 por pedido) cuando el riel lleva polea.");
        crearSiNoExiste("Crusador",              false, "Terminal/control de la polea del riel de onda serena (antes 'Terminal Control Polea'). Obligatorio cuando el riel lleva polea: 1 normalmente, 2 si abre \"Hacia los extremos\".");
        crearSiNoExiste("Tapa Riel",             false, "Tapa de riel de onda serena. Obligatoria (2 por pedido) cuando el riel NO lleva polea.");
        crearSiNoExiste("Roachina",              true,  "Riel de pines del riel de onda serena (antes 'Riel de Pines'). Por medida, obligatoria siempre.");
        crearSiNoExiste("Riata",                 true,  "Riata del riel de onda serena. Por medida, se corta al mismo ancho que el riel. Obligatoria SIEMPRE, con o sin polea.");

        crearSiNoExiste("Soporte Riel", false, "Soporte de instalación del riel de onda serena. Cantidad variable según el ancho del pedido.");

        // ── Bastones de riel: piezas FIJAS por unidad (NO se cortan ni se
        // miden). Cada uno tiene una longitud fija de fábrica, y esa medida ES
        // el nombre del insumo. El jefe elige manualmente cuál usar; el
        // sistema descuenta 1 unidad completa del tipo elegido normalmente,
        // o 2 si el riel abre "Hacia los extremos". Se usan cuando el riel
        // NO lleva polea. ──
        crearSiNoExiste("Bastón 0.80", false, "Bastón fijo de 0.80 m del riel de onda serena. Se descuenta como unidad completa, no se corta.");
        crearSiNoExiste("Bastón 1.20", false, "Bastón fijo de 1.20 m del riel de onda serena. Se descuenta como unidad completa, no se corta.");
        crearSiNoExiste("Bastón 1.50", false, "Bastón fijo de 1.50 m del riel de onda serena. Se descuenta como unidad completa, no se corta.");

        // ── FIX de migración: renombra los insumos viejos "Bastón Tipo A/B/C" a
        // los nombres nuevos por medida ("Bastón 0.80" etc.) si ya existían en
        // la base de datos, conservando su stock actual. También corrige a
        // tieneMedida = false si por error habían quedado como por medida. ──
        migrarNombreInsumo("Bastón Tipo A", "Bastón 0.80");
        migrarNombreInsumo("Bastón Tipo B", "Bastón 1.20");
        migrarNombreInsumo("Bastón Tipo C", "Bastón 1.50");
        corregirBastonAPorUnidad("Bastón 0.80");
        corregirBastonAPorUnidad("Bastón 1.20");
        corregirBastonAPorUnidad("Bastón 1.50");

        // ── FIX de migración: renombra el insumo viejo "Tapa" a "Tapa Cabezal"
        // si ya existía en la base de datos, conservando su stock actual. ──
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