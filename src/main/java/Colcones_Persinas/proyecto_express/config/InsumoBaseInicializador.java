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
        crearSiNoExiste("Tubo R8",    true,  "Tubo pequeño. Se carga por barras con medida.");
        crearSiNoExiste("Pesa",                  true,  "Pesa inferior, se corta a la misma medida que el tubo.");
        crearSiNoExiste("Control R16",           false, "Control para pedidos con ancho > 1.50 m. Se maneja por unidad.");
        crearSiNoExiste("Control R24",           false, "Control especial para pedidos con corte de tela >= 2.00m ancho y >= 2.50m largo. Soportes más grandes. Se maneja por unidad.");
        crearSiNoExiste("Acople",                false, "Acople necesario cuando el ancho de corte es >= 2.00m (2 por pedido), sin importar qué control termine asignado. Se maneja por unidad.");
        crearSiNoExiste("Terminal",              false, "Terminal acompañante del control. Obligatorio en TODO pedido de fabricación, sin importar ancho, alto o tipo de control/tubo. Se maneja por unidad.");
        crearSiNoExiste("Control R8 A", false, "Control para pedidos con Tubo R8. Se maneja por unidad.");
        crearSiNoExiste("Control R8 B",          false, "Control para pedidos con ancho <= 1.50 m. Se maneja por unidad.");
        crearSiNoExiste("Soporte",               false, "Soporte de instalación. Se usan 2 en todo pedido, con o sin cabezal.");
        crearSiNoExiste("Tapa",                  false, "Tapa de cabezal. Se usan 2 únicamente en pedidos CON cabezal.");
        crearSiNoExiste("Tope Pesa",             false, "Tope de pesa. Se usan 2 en todo pedido, con o sin cabezal.");
        crearSiNoExiste("Tapa Perfil",           false, "Tapas de perfil de la pesa. Se usan 2 en TODO pedido de fabricación, con o sin cabezal.");
        crearSiNoExiste("Tornillo",              false, "Tornillo normal. Sin cabezal: 2 (soportes). Con cabezal: 8 (soportes + tapas).");
        crearSiNoExiste("Tornillo Perforante",   false, "Tornillo perforante. Solo en pedidos CON cabezal: 4 unidades.");

        // ── Riel de Onda Serena ──────────────────────────────────────────
        crearSiNoExiste("Polea",                 false, "Polea del riel de onda serena. Obligatoria (2 por pedido) cuando el riel lleva polea.");
        crearSiNoExiste("Crusador",              false, "Terminal/control de la polea del riel de onda serena (antes 'Terminal Control Polea'). Obligatorio (1 por pedido) cuando el riel lleva polea.");
        crearSiNoExiste("Tapa Riel",             false, "Tapa de riel de onda serena. Obligatoria (2 por pedido) cuando el riel NO lleva polea.");
        crearSiNoExiste("Roachina",              true,  "Riel de pines del riel de onda serena (antes 'Riel de Pines'). Por medida, obligatoria siempre.");
        crearSiNoExiste("Riata",                 true,  "Riata del riel de onda serena. Por medida, se corta al mismo ancho que el riel. Obligatoria SIEMPRE, con o sin polea.");

        // ── Soporte de riel: por unidad, cantidad variable según ancho (ver Pedido.getCantidadSoportesRiel()) ──
        crearSiNoExiste("Soporte Riel", false, "Soporte de instalación del riel de onda serena. Cantidad variable según el ancho del pedido.");

        // ── Bastones de riel: piezas FIJAS por unidad (no se cortan ni se miden).
        //    Cada tipo tiene una longitud fija de fábrica, documentada aquí solo
        //    como referencia; el sistema únicamente descuenta 1 unidad completa
        //    del tipo que el jefe elija manualmente. Se usan cuando el riel NO
        //    lleva polea. ──
        crearSiNoExiste("Bastón Tipo A", false, "Bastón fijo de 0.80 m del riel de onda serena. Se descuenta como unidad completa, no se corta.");
        crearSiNoExiste("Bastón Tipo B", false, "Bastón fijo de 1.20 m del riel de onda serena. Se descuenta como unidad completa, no se corta.");
        crearSiNoExiste("Bastón Tipo C", false, "Bastón fijo de 1.50 m del riel de onda serena. Se descuenta como unidad completa, no se corta.");
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