package Colcones_Persinas.proyecto_express.servicio.fabrica;

import Colcones_Persinas.proyecto_express.modelo.fabrica.Insumo;
import Colcones_Persinas.proyecto_express.modelo.fabrica.MaterialUsado;
import Colcones_Persinas.proyecto_express.modelo.fabrica.Pedido;
import Colcones_Persinas.proyecto_express.modelo.fabrica.PiezaInsumo;
import Colcones_Persinas.proyecto_express.modelo.fabrica.RetazoTela;
import Colcones_Persinas.proyecto_express.modelo.fabrica.RolloTela;
import Colcones_Persinas.proyecto_express.repository.fabrica.InsumoRepository;
import Colcones_Persinas.proyecto_express.repository.fabrica.MaterialUsadoRepository;
import Colcones_Persinas.proyecto_express.repository.fabrica.PiezaInsumoRepository;
import Colcones_Persinas.proyecto_express.repository.fabrica.RetazoTelaRepository;
import Colcones_Persinas.proyecto_express.repository.fabrica.RolloTelaRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inventario de la fábrica: busca, reserva y descuenta el material de cada pedido.
 *
 * CÓMO SE DESCUENTA (desde esta versión):
 *  1. Para toda la orden se arma primero un PLAN DE CORTE en memoria. Cada producto
 *     (y cada unidad de una misma línea) corta de la pieza que quedó después del
 *     producto anterior; nunca dos productos "cuentan" con el mismo pedazo.
 *  2. Siempre se escoge la pieza MÁS PEQUEÑA que alcance (si empatan, la que ya
 *     está empezada): así se gastan primero los sobrantes y los rollos abiertos.
 *  3. Si algo no alcanza para algún producto, no se descuenta NADA y se dice cuál.
 *  4. Si todo alcanza, se descuenta de verdad siguiendo ese mismo plan, y cada
 *     producto queda con su propio registro: "tenía X m → quedan Y m".
 */
@Service
public class InventarioServicio {

    private final RolloTelaRepository rolloTelaRepository;
    private final InsumoRepository insumoRepository;
    private final PiezaInsumoRepository piezaInsumoRepository;
    private final MaterialUsadoRepository materialUsadoRepository;
    private final RetazoTelaRepository retazoTelaRepository;

    private static final double UMBRAL_DESCARTE_RETAZO = 0.05;
    private static final double UMBRAL_DESCARTE_PIEZA = 0.40;
    private static final List<Double> ANCHOS_COMERCIALES = Arrays.asList(1.83, 2.50, 3.00);

    public InventarioServicio(RolloTelaRepository rolloTelaRepository,
                               InsumoRepository insumoRepository,
                               PiezaInsumoRepository piezaInsumoRepository,
                               MaterialUsadoRepository materialUsadoRepository,
                               RetazoTelaRepository retazoTelaRepository) {
        this.rolloTelaRepository = rolloTelaRepository;
        this.insumoRepository = insumoRepository;
        this.piezaInsumoRepository = piezaInsumoRepository;
        this.materialUsadoRepository = materialUsadoRepository;
        this.retazoTelaRepository = retazoTelaRepository;
    }

    // ═══════════════════════════════════════════════════════════════
    // CLASES DE APOYO
    // ═══════════════════════════════════════════════════════════════

    public static class MaterialInsuficienteException extends RuntimeException {
        public MaterialInsuficienteException(String mensaje) { super(mensaje); }
    }

    public static class SeleccionManual {
        public Integer rolloTelaId;
        public Integer retazoTelaId;
        public Integer piezaTuboId;
        public Integer piezaCabezalId;
        public Integer piezaPesaId;
        public Integer piezaCuerdaId;
        public Integer piezaPitilloId;
    }

    public static class RolloEncontrado {
        public RolloTela rollo;
        public boolean esSustituto;
        public double anchoSolicitado;
    }

    public static class PrevisualizacionMaterial {
        public boolean disponible = true;
        public String rolloSugerido;
        public String retazoSugerido;
        public String tuboSugerido;
        public String cabezalSugerido;
        public String pesaSugerida;
        public String cuerdaSugerida;
        public String controlInfo;
        public String pitilloSugerido;
        public String conectorInfo;
        public String soporteInfo;
        public String tapaInfo;
        public String tapaPerfilInfo;
        public String tornilloInfo;
        public String tornilloPerforanteInfo;
        public String acopleInfo;
        public String terminalInfo;
        // ── Riel de Onda Serena ──
        public String rielInfo;
        public String roachinaInfo;
        public String riataInfo;
        public String cuerdaOndaInfo;
        public String poleaInfo;
        public String crusadorInfo;
        public String tapaRielInfo;
        public String bastonInfo;
        public String soporteRielInfo;
        public List<String> faltantes = new ArrayList<>();
    }

    public static class ResumenMaterial {
        public String tipoMaterial;
        public double totalUsado;
        public String unidad;
        public int vecesUsado;
        public List<MaterialUsado> detalle;
    }

    public enum NivelAlerta {
        ADVERTENCIA, CRITICO, AGOTADO
    }

    public static class AlertaInventario {
        public NivelAlerta nivel;
        public String titulo;
        public String mensaje;
        public String categoria;

        public AlertaInventario(NivelAlerta nivel, String titulo, String mensaje, String categoria) {
            this.nivel = nivel;
            this.titulo = titulo;
            this.mensaje = mensaje;
            this.categoria = categoria;
        }
    }

    public static class ExtraInsumo {
        public Integer insumoId;
        public String nombreLibre;
        public double cantidad;
    }

    public static class ItemTelaVenta {
        public Integer rolloId;
        public String color;
        public double ancho;
        public double metros;
    }

    // ─── Plan de corte (lo que se le va a cortar a cada producto) ───────────

    /** Un corte o un descuento concreto: de qué sale, cuánto se usa y cuánto queda. */
    public static class Paso {
        /** Nombre corto para mostrar: "🔧 Tubo", "🧵 Tela", "🔩 Tornillo"… */
        public String material;
        /** RETAZO, ROLLO, PIEZA, PITILLO o UNIDAD. */
        public String clase;
        /** id del retazo, rollo o pieza (null en UNIDAD). */
        public Integer fuenteId;
        /** Nombre del insumo en el catálogo (PIEZA, PITILLO y UNIDAD). */
        public String insumoNombre;
        /** Metros que se cortan (o unidades). */
        public double cantidad;
        /** Lo que tenía la pieza/rollo antes de este corte (o stock antes). */
        public double antes;
        /** Lo que queda después de este corte. */
        public double despues;
        /** true = quedó tan poquito que se bota (o se gastó completa). */
        public boolean descartado;
        /** true = la pieza la escogió el usuario a mano. */
        public boolean manual;
        public boolean sustituto;
        public double anchoSolicitado;
        public boolean combinado;
        /** Aviso para el usuario (p. ej. "la pieza que elegiste ya se gastó en otro producto"). */
        public String nota;
        /** Lo que se corta, en palabras: "1.475 m", "1.47 m × 1.70 m", "8 und." */
        public String medida;
        /** De dónde sale y cuánto queda, en palabras. */
        public String texto;
    }

    /** El plan completo de un producto (una unidad). */
    public static class PlanPedido {
        public Pedido pedido;
        public List<Paso> pasos = new ArrayList<>();
        public List<String> faltantes = new ArrayList<>();

        public boolean isDisponible() { return faltantes.isEmpty(); }
    }

    /** Vista previa de UNA unidad (para la pantalla de nuevo pedido). */
    public static class VistaCopia {
        public String etiqueta;
        public boolean disponible = true;
        public List<Paso> pasos = new ArrayList<>();
        public List<String> faltantes = new ArrayList<>();
    }

    /** Vista previa de una línea del formulario (puede tener varias unidades). */
    public static class VistaLinea {
        public boolean disponible = true;
        public List<VistaCopia> copias = new ArrayList<>();
    }

    /** Vista previa de TODA la orden, contando lo que gasta cada línea. */
    public static class VistaLote {
        public boolean disponible = true;
        public List<VistaLinea> lineas = new ArrayList<>();
        /** "Tubo R16 #5: 5.80 m → 1.20 m (3 cortes)" — una línea por pieza que se toca. */
        public List<String> resumen = new ArrayList<>();
    }

    private static final int UMBRAL_UNIDAD_ADVERTENCIA_DEFECTO = 50;
    private static final double UMBRAL_METROS_CRITICO_DEFECTO = 5.0;

    // ═══════════════════════════════════════════════════════════════
    // BÚSQUEDA DE RETAZO
    // ═══════════════════════════════════════════════════════════════

    public RetazoTela buscarMejorRetazo(String color, double anchoNecesario, double altoNecesario) {
        List<RetazoTela> directos = retazoTelaRepository
                .findByColorAndAnchoGreaterThanEqualAndAltoGreaterThanEqualOrderByAltoAsc(
                        color, anchoNecesario, altoNecesario);
        RetazoTela mejorDirecto = directos.isEmpty() ? null : directos.get(0);

        List<RetazoTela> rotados = retazoTelaRepository
                .findByColorAndAnchoGreaterThanEqualAndAltoGreaterThanEqualOrderByAltoAsc(
                        color, altoNecesario, anchoNecesario);
        RetazoTela mejorRotado = rotados.isEmpty() ? null : rotados.get(0);

        if (mejorDirecto == null) return mejorRotado;
        if (mejorRotado == null) return mejorDirecto;

        double sobranteDirecto = mejorDirecto.getAlto() - altoNecesario;
        double sobranteRotado = mejorRotado.getAlto() - anchoNecesario;
        return (sobranteDirecto <= sobranteRotado) ? mejorDirecto : mejorRotado;
    }

    public RetazoTela obtenerRetazoPorId(int id) {
        return retazoTelaRepository.findById(id)
                .orElseThrow(() -> new MaterialInsuficienteException("El retazo #" + id + " ya no existe."));
    }

    // ═══════════════════════════════════════════════════════════════
    // BÚSQUEDA DE ROLLO / PIEZA
    // ═══════════════════════════════════════════════════════════════

    public RolloTela buscarMejorRollo(String color, double ancho, double metrosNecesarios) {
        List<RolloTela> candidatos = rolloTelaRepository
                .findByColorAndAnchoAndLargoRestanteGreaterThanOrderByLargoRestanteAsc(color, ancho, 0.0);
        for (RolloTela r : candidatos) {
            if (r.getLargoRestante() >= metrosNecesarios - 0.001) return r;
        }
        throw new MaterialInsuficienteException(
                "No hay tela de color " + color + " (" + ancho + " m de ancho) con "
                + metrosNecesarios + " m disponibles.");
    }

    public RolloTela obtenerRolloPorId(int id) {
        return rolloTelaRepository.findById(id)
                .orElseThrow(() -> new MaterialInsuficienteException("El rollo #" + id + " ya no existe."));
    }

    public RolloEncontrado buscarMejorRolloConSustituto(String color, double anchoNecesario, double metrosNecesarios) {
        List<Double> candidatos = ANCHOS_COMERCIALES.stream()
                .filter(a -> a >= anchoNecesario - 0.001)
                .sorted()
                .collect(java.util.stream.Collectors.toList());

        for (double anchoProbado : candidatos) {
            try {
                RolloTela rollo = buscarMejorRollo(color, anchoProbado, metrosNecesarios);
                RolloEncontrado res = new RolloEncontrado();
                res.rollo = rollo;
                res.esSustituto = anchoProbado > anchoNecesario + 0.001;
                res.anchoSolicitado = anchoNecesario;
                return res;
            } catch (MaterialInsuficienteException ignorada) {
                // probar el siguiente ancho comercial
            }
        }

        throw new MaterialInsuficienteException(
                "No hay tela de color " + color + " disponible en ningún ancho comercial igual o mayor a "
                + anchoNecesario + " m, con " + metrosNecesarios + " m disponibles.");
    }

    public Insumo obtenerInsumoPorNombre(String nombre) {
        return insumoRepository.findByNombreIgnoreCase(nombre)
                .orElseThrow(() -> new MaterialInsuficienteException(
                        "No existe el insumo \"" + nombre + "\" en el catálogo."));
    }

    public PiezaInsumo buscarMejorPieza(Insumo insumo, double metrosNecesarios) {
        List<PiezaInsumo> candidatas = piezaInsumoRepository
                .findByInsumoIdAndLargoRestanteGreaterThanOrderByLargoRestanteAsc(insumo.getId(), 0.0);
        for (PiezaInsumo p : candidatas) {
            if (p.getLargoRestante() >= metrosNecesarios - 0.001) return p;
        }
        throw new MaterialInsuficienteException(
                "No hay suficiente \"" + insumo.getNombre() + "\". Se necesitan "
                + redondear(metrosNecesarios) + " m y no hay ninguna pieza disponible.");
    }

    public PiezaInsumo obtenerPiezaPorId(int id) {
        return piezaInsumoRepository.findById(id)
                .orElseThrow(() -> new MaterialInsuficienteException("La pieza #" + id + " ya no existe."));
    }

    // ═══════════════════════════════════════════════════════════════
    // PITILLO: combinación de retazos pequeños
    // ═══════════════════════════════════════════════════════════════

    public List<PiezaInsumo> resolverPitillo(Insumo pitillo, double necesario) {
        List<PiezaInsumo> candidatas = piezaInsumoRepository
                .findByInsumoIdAndLargoRestanteGreaterThanOrderByLargoRestanteAsc(pitillo.getId(), 0.0);

        for (PiezaInsumo p : candidatas) {
            if (p.getLargoRestante() >= necesario - 0.001) {
                return List.of(p);
            }
        }

        List<PiezaInsumo> combinacion = new ArrayList<>();
        double acumulado = 0.0;
        for (PiezaInsumo p : candidatas) {
            combinacion.add(p);
            acumulado += p.getLargoRestante();
            if (acumulado >= necesario - 0.001) {
                return combinacion;
            }
        }

        throw new MaterialInsuficienteException(
                "No hay suficiente \"" + pitillo.getNombre() + "\" ni combinando los retazos sueltos. Se necesitan "
                + redondear(necesario) + " m y solo hay " + redondear(acumulado) + " m disponibles en total.");
    }

    public MaterialUsado descontarPiezaPitillo(Pedido pedido, PiezaInsumo pieza, double metros,
                                                boolean manual, boolean combinado) {
        double antes = num(pieza.getLargoRestante());
        if (antes < metros - 0.001) {
            throw new MaterialInsuficienteException(
                    "Pieza #" + pieza.getId() + " (" + pieza.getInsumo().getNombre() + ") no tiene suficiente material ("
                    + antes + " m disponibles, " + metros + " m necesarios).");
        }

        double sobrante = redondear(antes - metros);
        boolean descartar = sobrante < UMBRAL_DESCARTE_PIEZA;

        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial(pieza.getInsumo().getNombre().toUpperCase().replace(" ", "_"));
        r.setPiezaInsumoId(pieza.getId());
        r.setFuenteDescripcion(pieza.getInsumo().getNombre() + " (#" + pieza.getId()
                + ", pieza original de " + redondear(num(pieza.getLargoInicial())) + " m)"
                + (combinado ? " [combinado con otra(s) pieza(s)]" : "")
                + cambio(antes, sobrante, descartar));
        r.setMetrosUsados(metros);
        r.setSeleccionManual(manual);

        if (descartar) {
            r.setMetrosSobrantes(0.0);
            piezaInsumoRepository.delete(pieza);
        } else {
            pieza.setLargoRestante(sobrante);
            piezaInsumoRepository.save(pieza);
            r.setMetrosSobrantes(sobrante);
        }

        return materialUsadoRepository.save(r);
    }

    // ═══════════════════════════════════════════════════════════════
    // DESCUENTOS UNITARIOS (cada uno deja su registro "tenía → quedan")
    // ═══════════════════════════════════════════════════════════════

    public MaterialUsado descontarRetazo(Pedido pedido, RetazoTela retazo, boolean manual) {
        double corteAncho = pedido.getCorteTelaAncho();
        double corteAlto = pedido.getCorteTelaAlto();

        double anchoOriginal = num(retazo.getAncho());
        double altoOriginal = num(retazo.getAlto());

        double altoUsado = altoQueGastaRetazo(anchoOriginal, altoOriginal, corteAncho, corteAlto);
        if (altoUsado < 0) {
            throw new MaterialInsuficienteException(
                    "Retazo #" + retazo.getId() + " no tiene medidas suficientes ("
                    + anchoOriginal + "m × " + altoOriginal + "m disponibles, se necesitan "
                    + corteAncho + "m × " + corteAlto + "m, en cualquier orientación).");
        }

        double sobrante = redondear(altoOriginal - altoUsado);
        boolean descartar = sobrante <= UMBRAL_DESCARTE_RETAZO;
        if (descartar) {
            retazoTelaRepository.delete(retazo);
        } else {
            retazo.setAlto(sobrante);
            retazoTelaRepository.save(retazo);
        }
        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial("RETAZO");
        r.setFuenteDescripcion("Retazo " + retazo.getColor() + " (#" + retazo.getId()
                + ") — medida original " + anchoOriginal + "m × " + altoOriginal + "m"
                + (descartar
                    ? " · se gastó completo"
                    : " · quedan " + num2(anchoOriginal) + " m × " + num2(sobrante) + " m"));
        r.setMetrosUsados(altoUsado);
        r.setMetrosSobrantes(sobrante);
        r.setMetrosCuadrados(redondear(pedido.getCorteTelaAncho() * pedido.getCorteTelaAlto()));
        r.setSeleccionManual(manual);
        return materialUsadoRepository.save(r);
    }

    /**
     * Cuánto alto se le quita a un retazo para sacar un corte de anchoCorte × altoCorte.
     * Prueba derecho y girado y escoge el que deja el sobrante más pequeño.
     * Devuelve -1 si no cabe de ninguna forma.
     */
    private double altoQueGastaRetazo(double anchoRetazo, double altoRetazo, double corteAncho, double corteAlto) {
        boolean entraDirecto = anchoRetazo >= corteAncho - 0.001 && altoRetazo >= corteAlto - 0.001;
        boolean entraRotado = anchoRetazo >= corteAlto - 0.001 && altoRetazo >= corteAncho - 0.001;
        if (entraDirecto && (!entraRotado || altoRetazo - corteAlto <= altoRetazo - corteAncho)) {
            return corteAlto;
        } else if (entraRotado) {
            return corteAncho;
        }
        return -1;
    }

    public MaterialUsado descontarTela(Pedido pedido, RolloTela rollo, double metros, boolean manual) {
        return descontarTela(pedido, rollo, metros, manual, false, 0.0);
    }

    public MaterialUsado descontarTela(Pedido pedido, RolloTela rollo, double metros, boolean manual,
                                        boolean esSustituto, double anchoOriginalNecesario) {
        double antes = num(rollo.getLargoRestante());
        if (antes < metros - 0.001) {
            throw new MaterialInsuficienteException(
                    "Rollo #" + rollo.getId() + " no tiene suficiente material ("
                    + antes + " m disponibles, " + metros + " m necesarios).");
        }
        double restante = redondear(antes - metros);
        boolean descartar = restante < UMBRAL_DESCARTE_PIEZA;

        String fuente = "Rollo " + rollo.getColor() + " " + rollo.getAncho() + "m (#" + rollo.getId()
                + ", rollo original de " + redondear(num(rollo.getLargoInicial())) + " m)"
                + cambio(antes, restante, descartar);
        if (esSustituto) {
            fuente += " ⚠ SUSTITUTO: se necesitaba ancho " + anchoOriginalNecesario
                    + "m pero no había stock; se usó " + rollo.getAncho() + "m en su lugar.";
        }

        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial("TELA");
        r.setRolloTelaId(rollo.getId());
        r.setFuenteDescripcion(fuente);
        r.setMetrosUsados(metros);
        r.setMetrosCuadrados(redondear(pedido.getCorteTelaAncho() * pedido.getCorteTelaAlto()));
        r.setSeleccionManual(manual);

        if (descartar) {
            r.setMetrosSobrantes(0.0);
            rolloTelaRepository.delete(rollo);
        } else {
            rollo.setLargoRestante(restante);
            rolloTelaRepository.save(rollo);
            r.setMetrosSobrantes(restante);
        }
        return materialUsadoRepository.save(r);
    }


    public MaterialUsado descontarTelaVentaDirecta(Pedido pedido, RolloTela rollo, double metros) {
        if (rollo.getLargoRestante() < metros - 0.001) {
            throw new MaterialInsuficienteException(
                    "Rollo #" + rollo.getId() + " no tiene suficiente material ("
                    + rollo.getLargoRestante() + " m disponibles, " + metros + " m necesarios).");
        }
        double restante = redondear(rollo.getLargoRestante() - metros);

        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial("TELA");
        r.setRolloTelaId(rollo.getId());
        r.setFuenteDescripcion("Rollo " + rollo.getColor() + " " + rollo.getAncho()
                + "m (#" + rollo.getId() + ", rollo original de " + redondear(rollo.getLargoInicial())
                + " m) — venta directa");
        r.setMetrosUsados(metros);
        r.setMetrosCuadrados(redondear(rollo.getAncho() * metros));
        r.setSeleccionManual(false);

        if (restante < UMBRAL_DESCARTE_PIEZA) {
            r.setMetrosSobrantes(0.0);
            rolloTelaRepository.delete(rollo);
        } else {
            rollo.setLargoRestante(restante);
            rolloTelaRepository.save(rollo);
            r.setMetrosSobrantes(restante);
        }
        return materialUsadoRepository.save(r);
    }

    public MaterialUsado descontarInsumoConMedida(Pedido pedido, PiezaInsumo pieza, double metros, boolean manual) {
        double antes = num(pieza.getLargoRestante());
        if (antes < metros - 0.001) {
            throw new MaterialInsuficienteException(
                    "Pieza #" + pieza.getId() + " (" + pieza.getInsumo().getNombre() + ") no tiene suficiente material ("
                    + antes + " m disponibles, " + metros + " m necesarios).");
        }
        double sobrante = redondear(antes - metros);
        boolean descartar = sobrante < UMBRAL_DESCARTE_PIEZA;

        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial(pieza.getInsumo().getNombre().toUpperCase().replace(" ", "_"));
        r.setPiezaInsumoId(pieza.getId());
        r.setFuenteDescripcion(pieza.getInsumo().getNombre() + " (#" + pieza.getId()
                + ", pieza original de " + redondear(num(pieza.getLargoInicial())) + " m)"
                + cambio(antes, sobrante, descartar));
        r.setMetrosUsados(metros);
        r.setSeleccionManual(manual);

        if (descartar) {
            r.setMetrosSobrantes(0.0);
            piezaInsumoRepository.delete(pieza);
        } else {
            pieza.setLargoRestante(sobrante);
            piezaInsumoRepository.save(pieza);
            r.setMetrosSobrantes(sobrante);
        }
        return materialUsadoRepository.save(r);
    }

    public MaterialUsado descontarInsumoPorUnidad(Pedido pedido, String nombreInsumo, int unidades) {
        Insumo insumo = obtenerInsumoPorNombre(nombreInsumo);
        int disponible = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;
        if (disponible < unidades) {
            throw new MaterialInsuficienteException(
                    "No hay suficiente \"" + insumo.getNombre() + "\". Disponible: "
                    + disponible + " unidad(es), necesario: " + unidades + ".");
        }
        insumo.setStockUnidades(disponible - unidades);
        insumoRepository.save(insumo);
        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial(insumo.getNombre().toUpperCase().replace(" ", "_"));
        r.setFuenteDescripcion(insumo.getNombre() + " (unidad) · había " + disponible
                + " → quedan " + (disponible - unidades));
        r.setMetrosUsados(unidades);
        r.setMetrosSobrantes(disponible - unidades);
        r.setSeleccionManual(false);
        return materialUsadoRepository.save(r);
    }

    // ═══════════════════════════════════════════════════════════════
    // PLAN DE CORTE — así se decide qué se corta de dónde
    //
    // El Simulador es una COPIA en memoria del inventario (no toca la base
    // de datos). Cada corte que se planea rebaja la pieza en esa copia, así
    // el siguiente producto ve lo que de verdad quedó. Al final, si todo
    // alcanza, aplicarPlanes() hace los descuentos reales en el mismo orden.
    // ═══════════════════════════════════════════════════════════════

    private static final class PiezaSim {
        final Integer id;
        final Object insumoId;
        final String insumo;
        final double inicial;
        final double original;
        double restante;
        boolean fuera;
        int cortes;

        PiezaSim(Integer id, Object insumoId, String insumo, double inicial, double restante) {
            this.id = id;
            this.insumoId = insumoId;
            this.insumo = insumo;
            this.inicial = inicial;
            this.restante = restante;
            this.original = restante;
        }

        boolean empezada() { return restante < inicial - 0.001; }
    }

    private static final class RolloSim {
        Integer id;
        String color;
        double ancho;
        double inicial;
        double original;
        double restante;
        boolean fuera;
        int cortes;

        boolean empezado() { return restante < inicial - 0.001; }
    }

    private static final class RetazoSim {
        Integer id;
        String color;
        double ancho;
        double altoOriginal;
        double alto;
        boolean fuera;
        int cortes;
    }

    private static final class UnidadSim {
        String nombre;
        int original;
        int stock;
    }

    /** Pieza más pequeña que alcance; si empatan, la ya empezada; luego la más antigua. */
    private static final Comparator<PiezaSim> MEJOR_PIEZA = Comparator
            .comparingDouble((PiezaSim p) -> p.restante)
            .thenComparingInt(p -> p.empezada() ? 0 : 1)
            .thenComparingInt(p -> p.id == null ? Integer.MAX_VALUE : p.id);

    private static final Comparator<RolloSim> MEJOR_ROLLO = Comparator
            .comparingDouble((RolloSim r) -> r.restante)
            .thenComparingInt(r -> r.empezado() ? 0 : 1)
            .thenComparingInt(r -> r.id == null ? Integer.MAX_VALUE : r.id);

    /** Copia en memoria del inventario para planear cortes sin tocar la base de datos. */
    private final class Simulador {
        private final Map<Object, List<PiezaSim>> piezasPorInsumo = new HashMap<>();
        private final Map<Integer, PiezaSim> piezasPorId = new LinkedHashMap<>();
        private List<RolloSim> rollos;
        private final Map<Integer, RolloSim> rollosPorId = new LinkedHashMap<>();
        private List<RetazoSim> retazos;
        private final Map<Integer, RetazoSim> retazosPorId = new LinkedHashMap<>();
        private final Map<String, UnidadSim> unidades = new LinkedHashMap<>();

        private PiezaSim registrar(PiezaInsumo p) {
            Integer id = p.getId();
            PiezaSim ya = piezasPorId.get(id);
            if (ya != null) return ya;
            Insumo ins = p.getInsumo();
            PiezaSim s = new PiezaSim(id,
                    ins != null ? (Object) ins.getId() : null,
                    ins != null ? ins.getNombre() : "Pieza",
                    num(p.getLargoInicial()),
                    num(p.getLargoRestante()));
            piezasPorId.put(id, s);
            return s;
        }

        List<PiezaSim> piezasDe(Insumo insumo) {
            Object clave = insumo.getId();
            List<PiezaSim> lista = piezasPorInsumo.get(clave);
            if (lista == null) {
                lista = new ArrayList<>();
                for (PiezaInsumo p : piezaInsumoRepository
                        .findByInsumoIdAndLargoRestanteGreaterThanOrderByLargoRestanteAsc(insumo.getId(), 0.0)) {
                    lista.add(registrar(p));
                }
                piezasPorInsumo.put(clave, lista);
            }
            return lista;
        }

        PiezaSim pieza(int id) {
            PiezaSim ya = piezasPorId.get(id);
            if (ya != null) return ya;
            return piezaInsumoRepository.findById(id).map(this::registrar).orElse(null);
        }

        List<RolloSim> rollos() {
            if (rollos == null) {
                rollos = new ArrayList<>();
                for (RolloTela r : rolloTelaRepository.findAllByOrderByColorAscAnchoAscLargoRestanteAsc()) {
                    RolloSim s = rollosPorId.get(r.getId());
                    if (s == null) {
                        s = new RolloSim();
                        s.id = r.getId();
                        s.color = r.getColor();
                        s.ancho = num(r.getAncho());
                        s.inicial = num(r.getLargoInicial());
                        s.restante = num(r.getLargoRestante());
                        s.original = s.restante;
                        rollosPorId.put(s.id, s);
                    }
                    rollos.add(s);
                }
            }
            return rollos;
        }

        RolloSim rollo(int id) {
            rollos();
            return rollosPorId.get(id);
        }

        List<RetazoSim> retazos() {
            if (retazos == null) {
                retazos = new ArrayList<>();
                for (RetazoTela r : retazoTelaRepository.findAllByOrderByColorAscAnchoAscAltoAsc()) {
                    RetazoSim s = new RetazoSim();
                    s.id = r.getId();
                    s.color = r.getColor();
                    s.ancho = num(r.getAncho());
                    s.alto = num(r.getAlto());
                    s.altoOriginal = s.alto;
                    retazosPorId.put(s.id, s);
                    retazos.add(s);
                }
            }
            return retazos;
        }

        RetazoSim retazo(int id) {
            retazos();
            return retazosPorId.get(id);
        }

        UnidadSim unidad(String nombre) {
            String clave = nombre.trim().toLowerCase(Locale.ROOT);
            UnidadSim u = unidades.get(clave);
            if (u == null) {
                Insumo insumo = obtenerInsumoPorNombre(nombre.trim());
                u = new UnidadSim();
                u.nombre = insumo.getNombre();
                u.original = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;
                u.stock = u.original;
                unidades.put(clave, u);
            }
            return u;
        }

        /** Una línea por cada pieza, rollo, retazo o insumo que la orden toca. */
        List<String> resumen() {
            List<String> lineas = new ArrayList<>();
            for (RetazoSim r : retazosPorId.values()) {
                if (r.cortes == 0) continue;
                lineas.add("✂ Retazo " + r.color + " #" + r.id + ": " + num2(r.ancho) + " × " + num2(r.altoOriginal)
                        + " m → " + (r.fuera ? "se gasta completo" : num2(r.ancho) + " × " + num2(r.alto) + " m")
                        + veces(r.cortes));
            }
            for (RolloSim r : rollosPorId.values()) {
                if (r.cortes == 0) continue;
                lineas.add("🧵 Rollo " + r.color + " " + num2(r.ancho) + " m #" + r.id + ": " + num2(r.original)
                        + " m → " + (r.fuera ? "se acaba" : num2(r.restante) + " m") + veces(r.cortes));
            }
            for (PiezaSim p : piezasPorId.values()) {
                if (p.cortes == 0) continue;
                lineas.add("📏 " + p.insumo + " #" + p.id + ": " + num2(p.original) + " m → "
                        + (p.fuera ? "se gasta" : num2(p.restante) + " m") + veces(p.cortes));
            }
            for (UnidadSim u : unidades.values()) {
                if (u.stock == u.original) continue;
                lineas.add("🔢 " + u.nombre + ": " + u.original + " → " + u.stock + " und.");
            }
            return lineas;
        }
    }

    private static String veces(int cortes) {
        return cortes > 1 ? " (" + cortes + " cortes)" : "";
    }

    /**
     * Arma el plan de corte de una orden completa. Cada unidad descuenta de lo que
     * dejó la anterior. Si algo no alcanza lanza MaterialInsuficienteException
     * diciendo en qué producto, y NO se toca nada del inventario.
     *
     * @param selecciones una por pedido (puede ser null o tener nulls = automático)
     */
    public List<PlanPedido> planificarLote(List<Pedido> pedidos, List<SeleccionManual> selecciones) {
        Simulador sim = new Simulador();
        List<PlanPedido> planes = new ArrayList<>();
        for (int i : ordenDePlan(pedidos)) {
            Pedido p = pedidos.get(i);
            PlanPedido plan = new PlanPedido();
            plan.pedido = p;
            try {
                planificar(p, seleccionDe(selecciones, i), sim, plan, true);
            } catch (MaterialInsuficienteException e) {
                if (pedidos.size() == 1) throw e;
                throw new MaterialInsuficienteException("Producto " + (i + 1) + " «" + nombreProducto(p) + "»: "
                        + e.getMessage());
            }
            planes.add(plan);
        }
        return planes;
    }

    /** Hace los descuentos reales siguiendo el plan (en el mismo orden en que se planeó). */
    public void aplicarPlanes(List<PlanPedido> planes) {
        for (PlanPedido plan : planes) {
            aplicarPlan(plan);
        }
    }

    public void aplicarPlan(PlanPedido plan) {
        Pedido pedido = plan.pedido;
        for (Paso paso : plan.pasos) {
            MaterialUsado registro;
            switch (paso.clase) {
                case "RETAZO":
                    registro = descontarRetazo(pedido, obtenerRetazoPorId(paso.fuenteId), paso.manual);
                    break;
                case "ROLLO":
                    registro = descontarTela(pedido, obtenerRolloPorId(paso.fuenteId), paso.cantidad, paso.manual,
                            paso.sustituto, paso.anchoSolicitado);
                    break;
                case "PIEZA":
                    registro = descontarInsumoConMedida(pedido, obtenerPiezaPorId(paso.fuenteId), paso.cantidad, paso.manual);
                    break;
                case "PITILLO":
                    registro = descontarPiezaPitillo(pedido, obtenerPiezaPorId(paso.fuenteId), paso.cantidad,
                            paso.manual, paso.combinado);
                    break;
                case "UNIDAD":
                    registro = descontarInsumoPorUnidad(pedido, paso.insumoNombre, (int) Math.round(paso.cantidad));
                    break;
                default:
                    continue;
            }
            if (paso.nota != null && registro != null) {
                registro.setFuenteDescripcion(registro.getFuenteDescripcion() + " — " + paso.nota);
                materialUsadoRepository.save(registro);
            }
        }
    }

    /**
     * Vista previa de toda la orden: igual que el plan real, pero si algo falta no
     * se detiene; lo anota y sigue, para que se vea todo lo que falta de una vez.
     *
     * @param lineas             cada línea del formulario con sus unidades ya separadas
     * @param seleccionPorLinea  la selección manual de cada línea (o null)
     */
    public VistaLote previsualizarLote(List<List<Pedido>> lineas, List<SeleccionManual> seleccionPorLinea) {
        List<Pedido> todos = new ArrayList<>();
        List<SeleccionManual> sels = new ArrayList<>();
        List<int[]> ubicacion = new ArrayList<>();
        for (int l = 0; l < lineas.size(); l++) {
            List<Pedido> copias = lineas.get(l) != null ? lineas.get(l) : Collections.<Pedido>emptyList();
            for (int c = 0; c < copias.size(); c++) {
                todos.add(copias.get(c));
                sels.add(seleccionDe(seleccionPorLinea, l));
                ubicacion.add(new int[]{l, c});
            }
        }

        Simulador sim = new Simulador();
        PlanPedido[] planes = new PlanPedido[todos.size()];
        for (int i : ordenDePlan(todos)) {
            PlanPedido plan = new PlanPedido();
            plan.pedido = todos.get(i);
            if (plan.pedido.getAncho() <= 0 || (!plan.pedido.isRielOndaSerena() && plan.pedido.getAltura() <= 0)) {
                plan.faltantes.add("Completa ancho y alto para calcular el material.");
            } else {
                planificar(plan.pedido, sels.get(i), sim, plan, false);
            }
            planes[i] = plan;
        }

        VistaLote vista = new VistaLote();
        for (int l = 0; l < lineas.size(); l++) {
            vista.lineas.add(new VistaLinea());
        }
        for (int i = 0; i < todos.size(); i++) {
            int l = ubicacion.get(i)[0];
            int c = ubicacion.get(i)[1];
            int total = lineas.get(l).size();
            VistaCopia copia = new VistaCopia();
            copia.etiqueta = total > 1 ? "Unidad " + (c + 1) + " de " + total : "";
            copia.pasos = planes[i].pasos;
            copia.faltantes = planes[i].faltantes;
            copia.disponible = planes[i].isDisponible();
            VistaLinea linea = vista.lineas.get(l);
            linea.copias.add(copia);
            if (!copia.disponible) {
                linea.disponible = false;
                vista.disponible = false;
            }
        }
        vista.resumen = sim.resumen();
        return vista;
    }

    /**
     * En qué orden se planean los cortes: primero los productos más anchos (y luego
     * los más altos). Así los cortes grandes toman las piezas grandes y los cortes
     * pequeños aprovechan los sobrantes; se desperdicia menos.
     */
    private List<Integer> ordenDePlan(List<Pedido> pedidos) {
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < pedidos.size(); i++) orden.add(i);
        orden.sort(Comparator
                .comparingDouble((Integer i) -> -pedidos.get(i).getAncho())
                .thenComparingDouble(i -> -pedidos.get(i).getAltura())
                .thenComparingInt(i -> i));
        return orden;
    }

    private SeleccionManual seleccionDe(List<SeleccionManual> selecciones, int i) {
        return (selecciones == null || i >= selecciones.size()) ? null : selecciones.get(i);
    }

    private String nombreProducto(Pedido p) {
        String d = p.getDescripcion();
        if (d != null && !d.isBlank()) return d.trim();
        return num2(p.getAncho()) + " × " + num2(p.getAltura()) + " m";
    }

    private void intentarPaso(PlanPedido plan, boolean estricto, Runnable accion) {
        try {
            accion.run();
        } catch (MaterialInsuficienteException e) {
            if (estricto) throw e;
            plan.faltantes.add(e.getMessage());
        }
    }

    private void planificar(Pedido p, SeleccionManual sel, Simulador sim, PlanPedido plan, boolean estricto) {
        if (p.isRielOndaSerena()) {
            planificarRiel(p, sim, plan, estricto);
        } else {
            planificarFabricacion(p, sel, sim, plan, estricto);
        }
    }

    private void planificarFabricacion(Pedido p, SeleccionManual sel, Simulador sim, PlanPedido plan, boolean estricto) {
        boolean cabezal = Boolean.TRUE.equals(p.getUsaCabezal());

        intentarPaso(plan, estricto, () -> planificarTela(p, sel, sim, plan));

        intentarPaso(plan, estricto, () -> cortarPieza(sim, plan, "🔧 Tubo",
                obtenerInsumoPorNombre("Tubo " + p.getTuboRecomendado()), p.getCorteTuberia(),
                sel != null ? sel.piezaTuboId : null));

        if (cabezal) {
            intentarPaso(plan, estricto, () -> cortarPieza(sim, plan, "🪟 Cabezal",
                    obtenerInsumoPorNombre("Cabezal"), p.getMedidaCabezal(),
                    sel != null ? sel.piezaCabezalId : null));
        }

        intentarPaso(plan, estricto, () -> cortarPieza(sim, plan, "⚖ Pesa",
                obtenerInsumoPorNombre("Pesa"), p.getCorteTuberia(),
                sel != null ? sel.piezaPesaId : null));

        intentarPaso(plan, estricto, () -> cortarPieza(sim, plan, "🧶 Cuerda",
                obtenerInsumoPorNombre("Cuerda"), p.getMetrosCuerda(),
                sel != null ? sel.piezaCuerdaId : null));

        // Control R24: viene empacado con terminal, acoples y soportes en un solo ítem.
        if (p.isUsaPaqueteControlR24()) {
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🎛 Control", "Paquete de Control R24", 1));
        } else {
            String control = p.getTipoControl() != null ? p.getTipoControl().trim() : "";
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🎛 Control", control, 1));
            if (p.getCantidadAcoples() > 0) {
                intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🔩 Acople", "Acople", p.getCantidadAcoples()));
            }
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🔌 Terminal", "Terminal", p.getCantidadTerminal()));
        }

        if (Boolean.TRUE.equals(p.getUsaPitilloPesa())) {
            intentarPaso(plan, estricto, () -> planificarPitillo(p, sel, sim, plan));
        }

        if (Boolean.TRUE.equals(p.getUsaConectorTope())) {
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🔗 Conector", "Conector", p.getCantidadConectores()));
            if (p.getCantidadTopes() > 0) {
                intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🔗 Tope", "Tope Control", p.getCantidadTopes()));
            }
        }

        // Soporte normal: solo si NO es Control R24 (ese ya trae los suyos).
        if (!p.isUsaPaqueteControlR24()) {
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🔩 Soporte", "Soporte", p.getCantidadSoportes()));
        }
        if (cabezal) {
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🧢 Tapa cabezal", "Tapa Cabezal", p.getCantidadTapas()));
        }
        intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🧢 Tapa perfil", "Tapa Perfil", p.getCantidadTapasPerfil()));
        intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🔩 Tornillo", "Tornillo", p.getCantidadTornillos()));
        if (cabezal) {
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🔩 Tornillo perforante", "Tornillo Perforante",
                    p.getCantidadTornillosPerforantes()));
        }
    }

    private void planificarRiel(Pedido p, Simulador sim, PlanPedido plan, boolean estricto) {
        intentarPaso(plan, estricto, () -> cortarPieza(sim, plan, "📏 Riel",
                obtenerInsumoPorNombre("Riel Onda Serena"), p.getCorteRiel(), null));
        intentarPaso(plan, estricto, () -> cortarPieza(sim, plan, "📌 Roachina",
                obtenerInsumoPorNombre("Roachina"), p.getCorteRielPines(), null));
        intentarPaso(plan, estricto, () -> cortarPieza(sim, plan, "🎗 Riata",
                obtenerInsumoPorNombre("Riata"), p.getMedidaRiata(), null));

        if (Boolean.TRUE.equals(p.getUsaPolea())) {
            intentarPaso(plan, estricto, () -> cortarPieza(sim, plan, "🧶 Cuerda",
                    obtenerInsumoPorNombre("Cuerda Onda Serena"), p.getCorteCuerdaOnda(), null));
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "⚙ Polea", "Polea", p.getCantidadPoleas()));
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "✖ Crusador", "Crusador", p.getCantidadTerminalPolea()));
        } else {
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🧢 Tapa riel", "Tapa Riel", p.getCantidadTapasRiel()));
            String nombreBaston = (p.getBastonElegido() != null && !p.getBastonElegido().isBlank())
                    ? p.getBastonElegido() : "Bastón 0.80";
            intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🦯 Bastón", nombreBaston, p.getCantidadBaston()));
        }
        intentarPaso(plan, estricto, () -> usarUnidades(sim, plan, "🔩 Soporte riel", "Soporte Riel", p.getCantidadSoportesRiel()));
    }

    // ─── Tela: primero retazo, si no, el rollo más gastado que alcance ──────

    private void planificarTela(Pedido p, SeleccionManual sel, Simulador sim, PlanPedido plan) {
        double corteAncho = p.getCorteTelaAncho();
        double corteAlto = p.getCorteTelaAlto();
        String color = p.getColorTelaDeseado() != null ? p.getColorTelaDeseado() : "";
        String nota = null;

        if (sel != null && sel.retazoTelaId != null) {
            RetazoSim r = sim.retazo(sel.retazoTelaId);
            if (r == null) {
                nota = "El retazo #" + sel.retazoTelaId + " que elegiste ya no existe; se tomó otra tela.";
            } else {
                if (!color.equalsIgnoreCase(r.color)) {
                    throw new MaterialInsuficienteException(
                            "El retazo #" + r.id + " es de color \"" + r.color
                            + "\", pero el pedido necesita color \"" + color + "\".");
                }
                if (!r.fuera && altoQueGastaRetazo(r.ancho, r.alto, corteAncho, corteAlto) >= 0) {
                    cortarRetazo(plan, r, corteAncho, corteAlto, true, null);
                    return;
                }
                if (r.cortes > 0) {
                    nota = "El retazo #" + r.id + " que elegiste ya no alcanzaba después de cortar los otros productos de esta orden; se tomó otra tela.";
                } else {
                    throw new MaterialInsuficienteException(
                            "El retazo #" + r.id + " elegido no tiene medidas suficientes ("
                            + r.ancho + "m × " + r.alto + "m disponibles, se necesitan "
                            + corteAncho + "m × " + corteAlto + "m, en cualquier orientación).");
                }
            }
        } else if (sel != null && sel.rolloTelaId != null) {
            RolloSim r = sim.rollo(sel.rolloTelaId);
            double necesario = metrosADescontarDeRollo(p);
            if (r == null) {
                nota = "El rollo #" + sel.rolloTelaId + " que elegiste ya no existe; se tomó otra tela.";
            } else {
                if (!color.equalsIgnoreCase(r.color)) {
                    throw new MaterialInsuficienteException(
                            "El rollo #" + r.id + " es de color \"" + r.color
                            + "\", pero el pedido necesita color \"" + color + "\".");
                }
                if (!r.fuera && r.restante >= necesario - 0.001) {
                    cortarRollo(plan, r, necesario, true, false, 0.0, null);
                    return;
                }
                if (r.cortes > 0) {
                    nota = "El rollo #" + r.id + " que elegiste ya no alcanzaba después de los otros productos de esta orden; se tomó otro.";
                } else {
                    throw new MaterialInsuficienteException(
                            "El rollo #" + r.id + " no tiene suficiente material ("
                            + r.restante + " m disponibles, " + necesario + " m necesarios).");
                }
            }
        }

        // Automático: 1) el retazo que deje menos sobrante
        RetazoSim mejorRetazo = mejorRetazo(sim, color, corteAncho, corteAlto);
        if (mejorRetazo != null) {
            cortarRetazo(plan, mejorRetazo, corteAncho, corteAlto, false, nota);
            return;
        }

        // 2) el rollo más gastado que alcance, en el ancho justo o el siguiente más ancho
        double anchoNecesario = anchoComercialDe(p);
        double metros = metrosADescontarDeRollo(p);
        List<Double> anchos = new ArrayList<>(ANCHOS_COMERCIALES);
        Collections.sort(anchos);
        for (double anchoProbado : anchos) {
            if (anchoProbado < anchoNecesario - 0.001) continue;
            RolloSim rollo = mejorRollo(sim, color, anchoProbado, metros);
            if (rollo != null) {
                cortarRollo(plan, rollo, metros, false, anchoProbado > anchoNecesario + 0.001, anchoNecesario, nota);
                return;
            }
        }

        throw new MaterialInsuficienteException(
                "No hay tela de color " + color + " disponible en ningún ancho comercial igual o mayor a "
                + anchoNecesario + " m, con " + metros + " m disponibles"
                + " (ni retazo de " + corteAncho + " × " + corteAlto + " m).");
    }

    private RetazoSim mejorRetazo(Simulador sim, String color, double corteAncho, double corteAlto) {
        RetazoSim mejorDirecto = null;
        RetazoSim mejorRotado = null;
        for (RetazoSim r : sim.retazos()) {
            if (r.fuera || !color.equalsIgnoreCase(r.color)) continue;
            if (r.ancho >= corteAncho - 0.001 && r.alto >= corteAlto - 0.001
                    && (mejorDirecto == null || r.alto < mejorDirecto.alto)) {
                mejorDirecto = r;
            }
            if (r.ancho >= corteAlto - 0.001 && r.alto >= corteAncho - 0.001
                    && (mejorRotado == null || r.alto < mejorRotado.alto)) {
                mejorRotado = r;
            }
        }
        if (mejorDirecto == null) return mejorRotado;
        if (mejorRotado == null) return mejorDirecto;
        double sobranteDirecto = mejorDirecto.alto - corteAlto;
        double sobranteRotado = mejorRotado.alto - corteAncho;
        return (sobranteDirecto <= sobranteRotado) ? mejorDirecto : mejorRotado;
    }

    private RolloSim mejorRollo(Simulador sim, String color, double ancho, double metros) {
        RolloSim mejor = null;
        for (RolloSim r : sim.rollos()) {
            if (r.fuera || !color.equalsIgnoreCase(r.color)) continue;
            if (Math.abs(r.ancho - ancho) > 0.001) continue;
            if (r.restante < metros - 0.001) continue;
            if (mejor == null || MEJOR_ROLLO.compare(r, mejor) < 0) mejor = r;
        }
        return mejor;
    }

    private void cortarRetazo(PlanPedido plan, RetazoSim r, double corteAncho, double corteAlto,
                              boolean manual, String nota) {
        double altoUsado = altoQueGastaRetazo(r.ancho, r.alto, corteAncho, corteAlto);
        double antes = r.alto;
        double despues = redondear(antes - altoUsado);
        boolean descartado = despues <= UMBRAL_DESCARTE_RETAZO;

        Paso paso = new Paso();
        paso.material = "✂ Retazo";
        paso.clase = "RETAZO";
        paso.fuenteId = r.id;
        paso.cantidad = altoUsado;
        paso.antes = antes;
        paso.despues = descartado ? 0.0 : despues;
        paso.descartado = descartado;
        paso.manual = manual;
        paso.nota = nota;
        paso.medida = num2(corteAncho) + " m × " + num2(corteAlto) + " m";
        paso.texto = "Retazo " + r.color + " #" + r.id + " · tiene " + num2(r.ancho) + " × " + num2(antes) + " m → "
                + (descartado ? "se gasta completo" : "quedará de " + num2(r.ancho) + " × " + num2(despues) + " m")
                + (manual ? " (elegido por ti)" : "");
        plan.pasos.add(paso);

        r.cortes++;
        if (descartado) {
            r.alto = 0;
            r.fuera = true;
        } else {
            r.alto = despues;
        }
    }

    private void cortarRollo(PlanPedido plan, RolloSim r, double metros, boolean manual,
                             boolean sustituto, double anchoSolicitado, String nota) {
        double antes = r.restante;
        double despues = redondear(antes - metros);
        boolean descartado = despues < UMBRAL_DESCARTE_PIEZA;

        Paso paso = new Paso();
        paso.material = "🧵 Tela";
        paso.clase = "ROLLO";
        paso.fuenteId = r.id;
        paso.cantidad = metros;
        paso.antes = antes;
        paso.despues = descartado ? 0.0 : despues;
        paso.descartado = descartado;
        paso.manual = manual;
        paso.sustituto = sustituto;
        paso.anchoSolicitado = anchoSolicitado;
        paso.nota = nota;
        paso.medida = num2(metros) + " m de largo";
        paso.texto = "Rollo " + r.color + " " + num2(r.ancho) + " m #" + r.id + textoCambio(antes, despues, descartado)
                + (manual ? " (elegido por ti)" : "")
                + (sustituto ? " ⚠ Se necesitaba " + num2(anchoSolicitado) + " m pero no hay; se usa uno más ancho." : "");
        plan.pasos.add(paso);

        r.cortes++;
        if (descartado) {
            r.restante = 0;
            r.fuera = true;
        } else {
            r.restante = despues;
        }
    }

    // ─── Piezas con medida (tubo, pesa, cuerda, cabezal, riel…) ─────────────

    private void cortarPieza(Simulador sim, PlanPedido plan, String etiqueta, Insumo insumo,
                             double metros, Integer manualId) {
        PiezaSim elegida = null;
        boolean manual = false;
        String nota = null;

        if (manualId != null) {
            PiezaSim m = sim.pieza(manualId);
            if (m == null) {
                nota = "La pieza #" + manualId + " que elegiste ya no existe; se tomó otra.";
            } else {
                if (!Objects.equals(m.insumoId, (Object) insumo.getId())) {
                    throw new MaterialInsuficienteException(
                            "La pieza #" + manualId + " es de \"" + m.insumo
                            + "\", pero se esperaba una pieza de \"" + insumo.getNombre() + "\".");
                }
                if (!m.fuera && m.restante >= metros - 0.001) {
                    elegida = m;
                    manual = true;
                } else if (m.cortes > 0) {
                    nota = "La pieza #" + manualId + " que elegiste ya no alcanzaba después de cortar los otros productos de esta orden; se tomó otra.";
                } else {
                    throw new MaterialInsuficienteException(
                            "La pieza #" + manualId + " (" + insumo.getNombre() + ") no tiene suficiente material ("
                            + m.restante + " m disponibles, " + metros + " m necesarios).");
                }
            }
        }

        if (elegida == null) {
            elegida = mejorPieza(sim, insumo, metros);
        }
        agregarCorte(plan, etiqueta, "PIEZA", elegida, metros, manual, false, nota);
    }

    private PiezaSim mejorPieza(Simulador sim, Insumo insumo, double metros) {
        PiezaSim mejor = null;
        double mayor = 0;
        for (PiezaSim p : sim.piezasDe(insumo)) {
            if (p.fuera) continue;
            mayor = Math.max(mayor, p.restante);
            if (p.restante < metros - 0.001) continue;
            if (mejor == null || MEJOR_PIEZA.compare(p, mejor) < 0) mejor = p;
        }
        if (mejor == null) {
            throw new MaterialInsuficienteException(
                    "No hay suficiente \"" + insumo.getNombre() + "\". Se necesitan " + num2(metros) + " m"
                    + (mayor > 0 ? " y la pieza más larga que queda tiene " + num2(mayor) + " m."
                                 : " y no hay ninguna pieza disponible."));
        }
        return mejor;
    }

    private void agregarCorte(PlanPedido plan, String etiqueta, String clase, PiezaSim pieza, double metros,
                              boolean manual, boolean combinado, String nota) {
        double antes = pieza.restante;
        double despues = redondear(antes - metros);
        boolean descartado = despues < UMBRAL_DESCARTE_PIEZA;

        Paso paso = new Paso();
        paso.material = etiqueta;
        paso.clase = clase;
        paso.fuenteId = pieza.id;
        paso.insumoNombre = pieza.insumo;
        paso.cantidad = metros;
        paso.antes = antes;
        paso.despues = descartado ? 0.0 : despues;
        paso.descartado = descartado;
        paso.manual = manual;
        paso.combinado = combinado;
        paso.nota = nota;
        paso.medida = num2(metros) + " m";
        paso.texto = pieza.insumo + " #" + pieza.id + textoCambio(antes, despues, descartado)
                + (combinado ? " (se une con otro pedazo)" : "")
                + (manual ? " (elegida por ti)" : "");
        plan.pasos.add(paso);

        pieza.cortes++;
        if (descartado) {
            pieza.restante = 0;
            pieza.fuera = true;
        } else {
            pieza.restante = despues;
        }
    }

    // ─── Pitillo: una pieza que alcance, o varios pedacitos unidos ──────────

    private void planificarPitillo(Pedido p, SeleccionManual sel, Simulador sim, PlanPedido plan) {
        Insumo pitillo = obtenerInsumoPorNombre("Pitillo");
        double necesario = p.getCortePitilloPesa();
        String nota = null;

        if (sel != null && sel.piezaPitilloId != null) {
            PiezaSim m = sim.pieza(sel.piezaPitilloId);
            if (m == null) {
                nota = "La pieza de pitillo #" + sel.piezaPitilloId + " que elegiste ya no existe; se tomó otra.";
            } else {
                if (!Objects.equals(m.insumoId, (Object) pitillo.getId())) {
                    throw new MaterialInsuficienteException(
                            "La pieza #" + m.id + " es de \"" + m.insumo
                            + "\", pero se esperaba una pieza de \"" + pitillo.getNombre() + "\".");
                }
                if (!m.fuera && m.restante >= necesario - 0.001) {
                    agregarCorte(plan, "🪡 Pitillo", "PITILLO", m, necesario, true, false, null);
                    return;
                }
                if (m.cortes > 0) {
                    nota = "La pieza de pitillo #" + m.id + " que elegiste ya no alcanzaba después de cortar los otros productos de esta orden; se tomó otra.";
                } else {
                    throw new MaterialInsuficienteException(
                            "La pieza #" + m.id + " (" + pitillo.getNombre() + ") no tiene suficiente material ("
                            + m.restante + " m disponibles, " + necesario + " m necesarios).");
                }
            }
        }

        List<PiezaSim> disponibles = new ArrayList<>();
        for (PiezaSim pz : sim.piezasDe(pitillo)) {
            if (!pz.fuera && pz.restante > 0.001) disponibles.add(pz);
        }
        disponibles.sort(MEJOR_PIEZA);

        for (PiezaSim pz : disponibles) {
            if (pz.restante >= necesario - 0.001) {
                agregarCorte(plan, "🪡 Pitillo", "PITILLO", pz, necesario, false, false, nota);
                return;
            }
        }

        // Ninguna alcanza sola: se unen los pedacitos más pequeños primero.
        List<PiezaSim> combinacion = new ArrayList<>();
        double acumulado = 0.0;
        for (PiezaSim pz : disponibles) {
            combinacion.add(pz);
            acumulado += pz.restante;
            if (acumulado >= necesario - 0.001) break;
        }
        if (acumulado < necesario - 0.001) {
            throw new MaterialInsuficienteException(
                    "No hay suficiente \"" + pitillo.getNombre() + "\" ni combinando los retazos sueltos. Se necesitan "
                    + redondear(necesario) + " m y solo hay " + redondear(acumulado) + " m disponibles en total.");
        }
        double falta = necesario;
        for (int i = 0; i < combinacion.size(); i++) {
            PiezaSim pz = combinacion.get(i);
            boolean ultima = (i == combinacion.size() - 1);
            double aUsar = ultima ? redondear(falta) : pz.restante;
            agregarCorte(plan, "🪡 Pitillo", "PITILLO", pz, aUsar, false, true, i == 0 ? nota : null);
            falta -= aUsar;
        }
    }

    // ─── Insumos por unidad ─────────────────────────────────────────────────

    private void usarUnidades(Simulador sim, PlanPedido plan, String etiqueta, String nombreInsumo, int cantidad) {
        if (cantidad <= 0) return;
        if (nombreInsumo == null || nombreInsumo.isBlank()) {
            throw new MaterialInsuficienteException("El pedido no tiene definido qué " + etiqueta + " lleva.");
        }
        UnidadSim u = sim.unidad(nombreInsumo);
        if (u.stock < cantidad) {
            throw new MaterialInsuficienteException(
                    "No hay suficiente \"" + u.nombre + "\". Disponible: " + u.stock
                    + " unidad(es), necesario: " + cantidad + ".");
        }
        Paso paso = new Paso();
        paso.material = etiqueta;
        paso.clase = "UNIDAD";
        paso.insumoNombre = u.nombre;
        paso.cantidad = cantidad;
        paso.antes = u.stock;
        paso.despues = u.stock - cantidad;
        paso.medida = cantidad + " und.";
        paso.texto = u.nombre + " · había " + u.stock + " → quedan " + (u.stock - cantidad);
        plan.pasos.add(paso);
        u.stock -= cantidad;
    }

    // ═══════════════════════════════════════════════════════════════
    // VERIFICACIÓN Y DESCUENTO DE UN SOLO PEDIDO (usan el mismo plan)
    // ═══════════════════════════════════════════════════════════════

    public void verificarDisponibilidad(Pedido pedido) {
        verificarDisponibilidad(pedido, null);
    }

    public void verificarDisponibilidad(Pedido pedido, SeleccionManual sel) {
        planificarLote(Collections.singletonList(pedido), Collections.singletonList(sel));
    }

    public void descontarMaterialDe(Pedido pedido) {
        descontarMaterialDe(pedido, null);
    }

    public void descontarMaterialDe(Pedido pedido, SeleccionManual sel) {
        aplicarPlanes(planificarLote(Collections.singletonList(pedido), Collections.singletonList(sel)));
    }

    // ═══════════════════════════════════════════════════════════════
    // VENTA DIRECTA — verificación y descuento de ítems de tela suelta
    // ═══════════════════════════════════════════════════════════════

    public void verificarItemsTelaVenta(List<ItemTelaVenta> items) {
        if (items == null) return;
        for (ItemTelaVenta it : items) {
            if (it.metros <= 0) continue;
            if (it.rolloId != null) {
                RolloTela rollo = obtenerRolloPorId(it.rolloId);
                if (!rollo.getColor().equalsIgnoreCase(it.color)) {
                    throw new MaterialInsuficienteException(
                            "El rollo #" + it.rolloId + " es de color \"" + rollo.getColor()
                            + "\", pero se pidió color \"" + it.color + "\".");
                }
                if (rollo.getLargoRestante() < it.metros - 0.001) {
                    throw new MaterialInsuficienteException(
                            "El rollo #" + it.rolloId + " no tiene suficiente tela ("
                            + rollo.getLargoRestante() + " m disponibles, " + it.metros + " m necesarios).");
                }
            } else {
                buscarMejorRollo(it.color, it.ancho, it.metros);
            }
        }
    }

    public void descontarItemsTelaVenta(Pedido pedido, List<ItemTelaVenta> items) {
        if (items == null) return;
        for (ItemTelaVenta it : items) {
            if (it.metros <= 0) continue;
            RolloTela rollo = (it.rolloId != null)
                    ? obtenerRolloPorId(it.rolloId)
                    : buscarMejorRollo(it.color, it.ancho, it.metros);
            descontarTelaVentaDirecta(pedido, rollo, it.metros);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RIEL DE ONDA SERENA
    // ═══════════════════════════════════════════════════════════════
    public void verificarRielOndaSerena(Pedido pedido) {
        planificarLote(Collections.singletonList(pedido), null);
    }

    public void descontarRielOndaSerena(Pedido pedido) {
        aplicarPlanes(planificarLote(Collections.singletonList(pedido), null));
    }

    // ═══════════════════════════════════════════════════════════════
    // REVERSIÓN DE MATERIAL (para edición de pedidos)
    //
    // Devuelve al inventario lo que el pedido gastó. Si la pieza o el rollo
    // ya no existe (porque quedó tan corto que se descartó), se vuelve a
    // crear con lo que se le había cortado, para que no se pierda material.
    // Los "extras" no se devuelven: al editar se conservan como texto.
    // ═══════════════════════════════════════════════════════════════

    public void revertirMaterialDe(int pedidoId) {
        List<MaterialUsado> registros = materialUsadoRepository.findByPedidoIdOrderByFechaAsc(pedidoId);
        if (registros.isEmpty()) return;

        for (MaterialUsado m : registros) {
            String tipo = m.getTipoMaterial() != null ? m.getTipoMaterial() : "";

            if ("EXTRA".equals(tipo)) {
                continue;
            }

            if ("TELA".equals(tipo)) {
                if (m.getRolloTelaId() != null) {
                    Optional<RolloTela> existente = rolloTelaRepository.findById(m.getRolloTelaId());
                    if (existente.isPresent()) {
                        RolloTela rollo = existente.get();
                        rollo.setLargoRestante(redondear(num(rollo.getLargoRestante()) + m.getMetrosUsados()));
                        rolloTelaRepository.save(rollo);
                    } else {
                        RolloTela recuperado = reconstruirRolloDesdeFuente(m);
                        if (recuperado != null) rolloTelaRepository.save(recuperado);
                    }
                }
            } else if ("RETAZO".equals(tipo)) {
                RetazoTela retazoRecuperado = reconstruirRetazoDesdeFuente(m);
                if (retazoRecuperado != null) {
                    retazoTelaRepository.save(retazoRecuperado);
                }
            } else if (m.getPiezaInsumoId() != null) {
                Optional<PiezaInsumo> existente = piezaInsumoRepository.findById(m.getPiezaInsumoId());
                if (existente.isPresent()) {
                    PiezaInsumo pieza = existente.get();
                    double nuevo = redondear(num(pieza.getLargoRestante()) + m.getMetrosUsados());
                    double inicial = num(pieza.getLargoInicial());
                    pieza.setLargoRestante(inicial > 0 ? Math.min(nuevo, inicial) : nuevo);
                    piezaInsumoRepository.save(pieza);
                } else {
                    PiezaInsumo recuperada = reconstruirPiezaDesdeFuente(m);
                    if (recuperada != null) piezaInsumoRepository.save(recuperada);
                }
            } else {
                String nombreInsumo = tipo.replace("_", " ");
                insumoRepository.findByNombreIgnoreCase(nombreInsumo).ifPresent(insumo -> {
                    int actual = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;
                    insumo.setStockUnidades(actual + (int) Math.round(m.getMetrosUsados()));
                    insumoRepository.save(insumo);
                });
            }
        }

        materialUsadoRepository.deleteAll(registros);
    }

    private static final Pattern PATRON_SOBRABAN = Pattern.compile("sobraban ([0-9]+(?:\\.[0-9]+)?) m");
    private static final Pattern PATRON_PIEZA_ORIGINAL = Pattern.compile("pieza original de ([0-9]+(?:\\.[0-9]+)?) m");
    private static final Pattern PATRON_ROLLO = Pattern.compile(
            "^Rollo (.+?) ([0-9]+(?:\\.[0-9]+)?)m \\(#\\d+, rollo original de ([0-9]+(?:\\.[0-9]+)?) m\\)");

    /** Lo que se botó por quedar muy corto (solo los registros nuevos lo traen). */
    private double descartadoDe(MaterialUsado m) {
        String desc = m.getFuenteDescripcion();
        if (desc == null) return 0.0;
        Matcher mt = PATRON_SOBRABAN.matcher(desc);
        return mt.find() ? Double.parseDouble(mt.group(1)) : 0.0;
    }

    private PiezaInsumo reconstruirPiezaDesdeFuente(MaterialUsado m) {
        try {
            String nombreInsumo = m.getTipoMaterial().replace("_", " ");
            Optional<Insumo> insumo = insumoRepository.findByNombreIgnoreCase(nombreInsumo);
            if (insumo.isEmpty()) return null;

            double devolver = redondear(m.getMetrosUsados() + descartadoDe(m));
            if (devolver <= 0.001) return null;

            double inicial = devolver;
            String desc = m.getFuenteDescripcion();
            if (desc != null) {
                Matcher mt = PATRON_PIEZA_ORIGINAL.matcher(desc);
                if (mt.find()) inicial = Math.max(devolver, Double.parseDouble(mt.group(1)));
            }

            PiezaInsumo pieza = new PiezaInsumo();
            pieza.setInsumo(insumo.get());
            pieza.setLargoInicial(inicial);
            pieza.setLargoRestante(devolver);
            return pieza;
        } catch (Exception e) {
            return null;
        }
    }

    private RolloTela reconstruirRolloDesdeFuente(MaterialUsado m) {
        try {
            String desc = m.getFuenteDescripcion();
            if (desc == null) return null;
            Matcher mt = PATRON_ROLLO.matcher(desc);
            if (!mt.find()) return null;

            double devolver = redondear(m.getMetrosUsados() + descartadoDe(m));
            if (devolver <= 0.001) return null;

            RolloTela rollo = new RolloTela();
            rollo.setColor(mt.group(1).trim());
            rollo.setAncho(Double.parseDouble(mt.group(2)));
            rollo.setLargoInicial(Math.max(devolver, Double.parseDouble(mt.group(3))));
            rollo.setLargoRestante(devolver);
            return rollo;
        } catch (Exception e) {
            return null;
        }
    }

    private RetazoTela reconstruirRetazoDesdeFuente(MaterialUsado m) {
        try {
            String desc = m.getFuenteDescripcion();
            if (desc == null || !desc.startsWith("Retazo ")) return null;

            String color;
            double ancho;

            int idxMedidaOriginal = desc.indexOf("medida original ");
            if (idxMedidaOriginal >= 0) {
                int idxParentesis = desc.indexOf(" (#");
                if (idxParentesis <= 0) return null;
                color = desc.substring("Retazo ".length(), idxParentesis).trim();
                String medidas = desc.substring(idxMedidaOriginal + "medida original ".length());
                String anchoTexto = medidas.split("×")[0].replace("m", "").trim();
                ancho = Double.parseDouble(anchoTexto);
            } else {
                String sinPrefijo = desc.substring("Retazo ".length());
                int idxNumero = -1;
                for (int i = 0; i < sinPrefijo.length(); i++) {
                    if (Character.isDigit(sinPrefijo.charAt(i))) { idxNumero = i; break; }
                }
                if (idxNumero < 1) return null;
                color = sinPrefijo.substring(0, idxNumero).trim();
                String medidas = sinPrefijo.substring(idxNumero);
                String[] partes = medidas.split("m");
                if (partes.length < 1) return null;
                ancho = Double.parseDouble(partes[0].trim());
            }

            double altoOriginal = redondear(m.getMetrosUsados() + m.getMetrosSobrantes());
            if (altoOriginal <= 0.001) return null;

            RetazoTela retazo = new RetazoTela();
            retazo.setColor(color);
            retazo.setAncho(ancho);
            retazo.setAlto(altoOriginal);
            return retazo;
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PROCESAMIENTO DE LOTE
    // ═══════════════════════════════════════════════════════════════

    public void procesarLoteCompleto(List<Pedido> pedidos) {
        aplicarPlanes(planificarLote(pedidos, null));
    }

    // ─── Textos de "tenía → quedan" ────────────────────────────────────────

    /** Para el registro guardado: " · tenía 3.00 m → quedan 1.53 m". */
    private String cambio(double antes, double despues, boolean descartado) {
        if (descartado) {
            if (despues <= 0.001) {
                return " · tenía " + num2(antes) + " m → se gastó completa";
            }
            return " · tenía " + num2(antes) + " m → sobraban " + num2(despues) + " m (muy poco, se descartó)";
        }
        return " · tenía " + num2(antes) + " m → quedan " + num2(despues) + " m";
    }

    /** Para la vista previa: " · tiene 3.00 m → quedarán 1.53 m". */
    private String textoCambio(double antes, double despues, boolean descartado) {
        if (descartado) {
            if (despues <= 0.001) {
                return " · tiene " + num2(antes) + " m → se gasta completa";
            }
            return " · tiene " + num2(antes) + " m → sobran " + num2(despues) + " m (muy poco, se bota)";
        }
        return " · tiene " + num2(antes) + " m → quedarán " + num2(despues) + " m";
    }

    /** 1.5 → "1.50", 1.475 → "1.475", 3 → "3.00". */
    private static String num2(double v) {
        BigDecimal b = BigDecimal.valueOf(Math.round(v * 1000.0) / 1000.0).setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (b.scale() < 2) b = b.setScale(2, RoundingMode.HALF_UP);
        return b.toPlainString();
    }

    /** Lee un número aunque el modelo lo tenga como double o como Double (null = 0). */
    private static double num(Object valor) {
        return valor == null ? 0.0 : ((Number) valor).doubleValue();
    }

    // ═══════════════════════════════════════════════════════════════
    // PREVISUALIZACIÓN EN VIVO — FABRICACIÓN
    // ═══════════════════════════════════════════════════════════════

    public PrevisualizacionMaterial previsualizar(Pedido pedido) {
        PrevisualizacionMaterial res = new PrevisualizacionMaterial();

        intentar(res, () -> {
            RetazoTela retazo = buscarMejorRetazo(
                    pedido.getColorTelaDeseado(), pedido.getCorteTelaAncho(), pedido.getCorteTelaAlto());
            if (retazo != null) {
                double sobrante = redondear(retazo.getAlto() - pedido.getCorteTelaAlto());
                res.retazoSugerido = "✂ Retazo " + retazo.getColor() + " " + retazo.getAncho()
                        + "m × " + retazo.getAlto() + "m (#" + retazo.getId()
                        + ") · quedarían " + sobrante + " m de alto";
            } else {
                double anchoNecesario = anchoComercialDe(pedido);
                RolloEncontrado encontrado = buscarMejorRolloConSustituto(
                        pedido.getColorTelaDeseado(), anchoNecesario, metrosADescontarDeRollo(pedido));
                RolloTela r = encontrado.rollo;
                String texto = "Rollo " + r.getColor() + " " + r.getAncho() + "m (#" + r.getId()
                        + ") · quedarían " + redondear(r.getLargoRestante() - metrosADescontarDeRollo(pedido)) + " m";
                if (encontrado.esSustituto) {
                    texto += " ⚠ Se necesitaba " + anchoNecesario + "m pero no hay stock; se usará uno más ancho.";
                }
                res.rolloSugerido = texto;
            }
        });

        intentar(res, () -> {
            Insumo tubo = obtenerInsumoPorNombre("Tubo " + pedido.getTuboRecomendado());
            PiezaInsumo p = buscarMejorPieza(tubo, pedido.getCorteTuberia());
            res.tuboSugerido = tubo.getNombre() + " (#" + p.getId() + ") · quedarían "
                    + redondear(p.getLargoRestante() - pedido.getCorteTuberia()) + " m";
        });

        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            intentar(res, () -> {
                Insumo cab = obtenerInsumoPorNombre("Cabezal");
                PiezaInsumo p = buscarMejorPieza(cab, pedido.getMedidaCabezal());
                res.cabezalSugerido = "Cabezal (#" + p.getId() + ") · quedarían "
                        + redondear(p.getLargoRestante() - pedido.getMedidaCabezal()) + " m";
            });
        }

        intentar(res, () -> {
            Insumo pesa = obtenerInsumoPorNombre("Pesa");
            PiezaInsumo p = buscarMejorPieza(pesa, pedido.getCorteTuberia());
            res.pesaSugerida = "Pesa (#" + p.getId() + ") · quedarían "
                    + redondear(p.getLargoRestante() - pedido.getCorteTuberia()) + " m";
        });

        intentar(res, () -> {
            Insumo cuerda = obtenerInsumoPorNombre("Cuerda");
            PiezaInsumo p = buscarMejorPieza(cuerda, pedido.getMetrosCuerda());
            res.cuerdaSugerida = "Cuerda (#" + p.getId() + ") · quedarían "
                    + redondear(p.getLargoRestante() - pedido.getMetrosCuerda()) + " m";
        });

        // ── Control (bifurcado: Paquete R24 vs. flujo normal) ──
        if (pedido.isUsaPaqueteControlR24()) {
            intentar(res, () -> {
                Insumo paquete = obtenerInsumoPorNombre("Paquete de Control R24");
                int stock = paquete.getStockUnidades() != null ? paquete.getStockUnidades() : 0;
                if (stock < 1) throw new MaterialInsuficienteException("Sin stock de \"Paquete de Control R24\".");
                res.controlInfo = "Paquete de Control R24 (incluye control, terminal, acoples y soportes) · quedarían " + (stock - 1) + " unidad(es)";
            });
        } else {
            intentar(res, () -> {
                Insumo control = obtenerInsumoPorNombre(pedido.getTipoControl().trim());
                int stock = control.getStockUnidades() != null ? control.getStockUnidades() : 0;
                if (stock < 1) throw new MaterialInsuficienteException("Sin stock de \"" + control.getNombre() + "\".");
                res.controlInfo = control.getNombre() + " · quedarían " + (stock - 1) + " unidad(es)";
            });

            if (pedido.getCantidadAcoples() > 0) {
                intentar(res, () -> {
                    Insumo acople = obtenerInsumoPorNombre("Acople");
                    int stock = acople.getStockUnidades() != null ? acople.getStockUnidades() : 0;
                    int necesario = pedido.getCantidadAcoples();
                    if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Acople\".");
                    res.acopleInfo = "Acople ×" + necesario + " · quedarían " + (stock - necesario);
                });
            }

            intentar(res, () -> {
                Insumo terminal = obtenerInsumoPorNombre("Terminal");
                int stock = terminal.getStockUnidades() != null ? terminal.getStockUnidades() : 0;
                int necesario = pedido.getCantidadTerminal();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Terminal\".");
                res.terminalInfo = "Terminal ×" + necesario + " · quedarían " + (stock - necesario);
            });
        }

        if (Boolean.TRUE.equals(pedido.getUsaPitilloPesa())) {
            intentar(res, () -> {
                Insumo pitillo = obtenerInsumoPorNombre("Pitillo");
                List<PiezaInsumo> piezas = resolverPitillo(pitillo, pedido.getCortePitilloPesa());
                if (piezas.size() == 1) {
                    PiezaInsumo p = piezas.get(0);
                    res.pitilloSugerido = "Pitillo (#" + p.getId() + ") · quedarían "
                            + redondear(p.getLargoRestante() - pedido.getCortePitilloPesa()) + " m";
                } else {
                    String ids = piezas.stream().map(p -> "#" + p.getId())
                            .collect(java.util.stream.Collectors.joining(" + "));
                    res.pitilloSugerido = "Pitillo combinando " + piezas.size() + " retazos (" + ids + ")";
                }
            });
        }

        if (Boolean.TRUE.equals(pedido.getUsaConectorTope())) {
            intentar(res, () -> {
                Insumo conector = obtenerInsumoPorNombre("Conector");
                int stockConector = conector.getStockUnidades() != null ? conector.getStockUnidades() : 0;
                int necesario = pedido.getCantidadConectores();
                if (stockConector < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Conector\".");
                String info = "Conector ×" + necesario + " · quedarían " + (stockConector - necesario);
                if (pedido.getCantidadTopes() > 0) {
                    Insumo tope = obtenerInsumoPorNombre("Tope Control");
                    int stockTope = tope.getStockUnidades() != null ? tope.getStockUnidades() : 0;
                    if (stockTope < pedido.getCantidadTopes()) throw new MaterialInsuficienteException("Sin stock suficiente de \"Tope Control\".");
                    info += " | Tope ×" + pedido.getCantidadTopes() + " · quedarían " + (stockTope - pedido.getCantidadTopes());
                }
                res.conectorInfo = info;
            });
        }

        // Soporte: solo si NO es Control R24.
        if (!pedido.isUsaPaqueteControlR24()) {
            intentar(res, () -> {
                Insumo soporte = obtenerInsumoPorNombre("Soporte");
                int stock = soporte.getStockUnidades() != null ? soporte.getStockUnidades() : 0;
                int necesario = pedido.getCantidadSoportes();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Soporte\".");
                res.soporteInfo = "Soporte ×" + necesario + " · quedarían " + (stock - necesario);
            });
        }

        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            intentar(res, () -> {
                Insumo tapa = obtenerInsumoPorNombre("Tapa Cabezal");
                int stock = tapa.getStockUnidades() != null ? tapa.getStockUnidades() : 0;
                int necesario = pedido.getCantidadTapas();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Tapa Cabezal\".");
                res.tapaInfo = "Tapa Cabezal ×" + necesario + " · quedarían " + (stock - necesario);
            });
        }

        intentar(res, () -> {
            Insumo tapaPerfil = obtenerInsumoPorNombre("Tapa Perfil");
            int stock = tapaPerfil.getStockUnidades() != null ? tapaPerfil.getStockUnidades() : 0;
            int necesario = pedido.getCantidadTapasPerfil();
            if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Tapa Perfil\".");
            res.tapaPerfilInfo = "Tapa Perfil ×" + necesario + " · quedarían " + (stock - necesario);
        });

        intentar(res, () -> {
            Insumo tornillo = obtenerInsumoPorNombre("Tornillo");
            int stock = tornillo.getStockUnidades() != null ? tornillo.getStockUnidades() : 0;
            int necesario = pedido.getCantidadTornillos();
            if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Tornillo\".");
            res.tornilloInfo = "Tornillo ×" + necesario + " · quedarían " + (stock - necesario);
        });

        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            intentar(res, () -> {
                Insumo tornilloPerf = obtenerInsumoPorNombre("Tornillo Perforante");
                int stock = tornilloPerf.getStockUnidades() != null ? tornilloPerf.getStockUnidades() : 0;
                int necesario = pedido.getCantidadTornillosPerforantes();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Tornillo Perforante\".");
                res.tornilloPerforanteInfo = "Tornillo Perforante ×" + necesario + " · quedarían " + (stock - necesario);
            });
        }

        return res;
    }

    // ═══════════════════════════════════════════════════════════════
    // PREVISUALIZACIÓN EN VIVO — RIEL DE ONDA SERENA
    //
    // Consulta el inventario REAL, igual que previsualizar() hace para
    // fabricación. Antes la vista previa del riel era solo un cálculo
    // matemático en el navegador que nunca consultaba si el material
    // realmente existía en stock.
    // ═══════════════════════════════════════════════════════════════

    public PrevisualizacionMaterial previsualizarRiel(Pedido pedido) {
        PrevisualizacionMaterial res = new PrevisualizacionMaterial();

        intentar(res, () -> {
            Insumo riel = obtenerInsumoPorNombre("Riel Onda Serena");
            PiezaInsumo p = buscarMejorPieza(riel, pedido.getCorteRiel());
            res.rielInfo = "Riel Onda Serena (#" + p.getId() + ") · quedarían "
                    + redondear(p.getLargoRestante() - pedido.getCorteRiel()) + " m";
        });

        intentar(res, () -> {
            Insumo roachina = obtenerInsumoPorNombre("Roachina");
            PiezaInsumo p = buscarMejorPieza(roachina, pedido.getCorteRielPines());
            res.roachinaInfo = "Roachina (#" + p.getId() + ") · quedarían "
                    + redondear(p.getLargoRestante() - pedido.getCorteRielPines()) + " m";
        });

        intentar(res, () -> {
            Insumo riata = obtenerInsumoPorNombre("Riata");
            PiezaInsumo p = buscarMejorPieza(riata, pedido.getMedidaRiata());
            res.riataInfo = "Riata (#" + p.getId() + ") · quedarían "
                    + redondear(p.getLargoRestante() - pedido.getMedidaRiata()) + " m";
        });

        if (Boolean.TRUE.equals(pedido.getUsaPolea())) {
            intentar(res, () -> {
                Insumo cuerdaOnda = obtenerInsumoPorNombre("Cuerda Onda Serena");
                PiezaInsumo p = buscarMejorPieza(cuerdaOnda, pedido.getCorteCuerdaOnda());
                res.cuerdaOndaInfo = "Cuerda Onda Serena (#" + p.getId() + ") · quedarían "
                        + redondear(p.getLargoRestante() - pedido.getCorteCuerdaOnda()) + " m";
            });

            intentar(res, () -> {
                Insumo polea = obtenerInsumoPorNombre("Polea");
                int stock = polea.getStockUnidades() != null ? polea.getStockUnidades() : 0;
                int necesario = pedido.getCantidadPoleas();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Polea\".");
                res.poleaInfo = "Polea ×" + necesario + " · quedarían " + (stock - necesario);
            });

            intentar(res, () -> {
                Insumo crusador = obtenerInsumoPorNombre("Crusador");
                int stock = crusador.getStockUnidades() != null ? crusador.getStockUnidades() : 0;
                int necesario = pedido.getCantidadTerminalPolea();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Crusador\".");
                res.crusadorInfo = "Crusador ×" + necesario + " · quedarían " + (stock - necesario);
            });
        } else {
            intentar(res, () -> {
                Insumo tapaRiel = obtenerInsumoPorNombre("Tapa Riel");
                int stock = tapaRiel.getStockUnidades() != null ? tapaRiel.getStockUnidades() : 0;
                int necesario = pedido.getCantidadTapasRiel();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Tapa Riel\".");
                res.tapaRielInfo = "Tapa Riel ×" + necesario + " · quedarían " + (stock - necesario);
            });

            intentar(res, () -> {
                String nombreBaston = (pedido.getBastonElegido() != null && !pedido.getBastonElegido().isBlank())
                        ? pedido.getBastonElegido() : "Bastón 0.80";
                Insumo baston = obtenerInsumoPorNombre(nombreBaston);
                int stock = baston.getStockUnidades() != null ? baston.getStockUnidades() : 0;
                int necesario = pedido.getCantidadBaston();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"" + nombreBaston + "\".");
                res.bastonInfo = nombreBaston + " ×" + necesario + " · quedarían " + (stock - necesario);
            });
        }

        intentar(res, () -> {
            Insumo soporteRiel = obtenerInsumoPorNombre("Soporte Riel");
            int stock = soporteRiel.getStockUnidades() != null ? soporteRiel.getStockUnidades() : 0;
            int necesario = pedido.getCantidadSoportesRiel();
            if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Soporte Riel\".");
            res.soporteRielInfo = "Soporte Riel ×" + necesario + " · quedarían " + (stock - necesario);
        });

        return res;
    }

    // ═══════════════════════════════════════════════════════════════
    // AUXILIARES
    // ═══════════════════════════════════════════════════════════════

    private void intentar(PrevisualizacionMaterial res, Runnable accion) {
        try {
            accion.run();
        } catch (MaterialInsuficienteException e) {
            res.disponible = false;
            res.faltantes.add(e.getMessage());
        }
    }

    public double anchoComercialDe(Pedido pedido) {
        double largo = pedido.getCorteTelaAlto();
        if (largo <= 1.83) return 1.83;
        if (largo <= 2.50) return 2.50;
        return 3.00;
    }

    public double metrosADescontarDeRollo(Pedido pedido) {
        return pedido.getCorteTelaAncho();
    }

    private double redondear(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // ═══════════════════════════════════════════════════════════════
    // MÉTODOS DE CONSULTA
    // ═══════════════════════════════════════════════════════════════

    public List<RolloTela> getTodosLosRollos() {
        return rolloTelaRepository.findAllByOrderByColorAscAnchoAscLargoRestanteAsc();
    }

    public List<Insumo> getTodosLosInsumos() {
        return insumoRepository.findAllByOrderByNombreAsc();
    }

    public List<PiezaInsumo> getPiezasDeInsumo(int insumoId) {
        return piezaInsumoRepository.findByInsumoIdOrderByLargoRestanteAsc(insumoId);
    }

    public List<MaterialUsado> getHistorialDePedido(int pedidoId) {
        return materialUsadoRepository.findByPedidoIdOrderByFechaAsc(pedidoId);
    }

    public List<PiezaInsumo> getTodasLasPiezas() {
        return piezaInsumoRepository.findAll();
    }

    public List<RetazoTela> getTodosLosRetazos() {
        return retazoTelaRepository.findAllByOrderByColorAscAnchoAscAltoAsc();
    }

    public List<ResumenMaterial> obtenerResumenConsumo(LocalDateTime desde, LocalDateTime hasta) {
        List<MaterialUsado> registros = (desde != null && hasta != null)
                ? materialUsadoRepository.findByFechaBetweenOrderByFechaAsc(desde, hasta)
                : materialUsadoRepository.findAll();

        Map<String, List<MaterialUsado>> agrupado = registros.stream()
                .collect(java.util.stream.Collectors.groupingBy(MaterialUsado::getTipoMaterial));

        List<ResumenMaterial> resumen = new ArrayList<>();
        for (Map.Entry<String, List<MaterialUsado>> entry : agrupado.entrySet()) {
            ResumenMaterial r = new ResumenMaterial();
            r.tipoMaterial = entry.getKey();
            r.detalle = entry.getValue().stream()
                    .sorted((a, b) -> a.getFecha().compareTo(b.getFecha()))
                    .collect(java.util.stream.Collectors.toList());
            r.vecesUsado = r.detalle.size();

            if ("TELA".equals(r.tipoMaterial) || "RETAZO".equals(r.tipoMaterial)) {
                r.unidad = "m²";
                r.totalUsado = redondear(r.detalle.stream()
                        .mapToDouble(m -> m.getMetrosCuadrados() != null ? m.getMetrosCuadrados() : 0.0)
                        .sum());
            } else if (!r.detalle.isEmpty() && r.detalle.get(0).getPiezaInsumoId() != null) {
                r.unidad = "m";
                r.totalUsado = redondear(r.detalle.stream().mapToDouble(MaterialUsado::getMetrosUsados).sum());
            } else {
                r.unidad = "unidad(es)";
                r.totalUsado = redondear(r.detalle.stream().mapToDouble(MaterialUsado::getMetrosUsados).sum());
            }

            resumen.add(r);
        }
        resumen.sort((a, b) -> a.tipoMaterial.compareTo(b.tipoMaterial));
        return resumen;
    }

    // ═══════════════════════════════════════════════════════════════
    // ALERTAS DE STOCK BAJO
    // ═══════════════════════════════════════════════════════════════

    public List<AlertaInventario> obtenerAlertasInventario() {
        List<AlertaInventario> alertas = new ArrayList<>();

        alertas.addAll(alertasDeTela());
        alertas.addAll(alertasDeInsumos());

        alertas.sort(Comparator.comparingInt(a -> nivelPrioridad(a.nivel)));
        return alertas;
    }

    private int nivelPrioridad(NivelAlerta nivel) {
        switch (nivel) {
            case AGOTADO: return 0;
            case CRITICO: return 1;
            default: return 2;
        }
    }

    private List<AlertaInventario> alertasDeTela() {
        List<AlertaInventario> alertas = new ArrayList<>();

        List<RolloTela> rollos = rolloTelaRepository.findAllByOrderByColorAscAnchoAscLargoRestanteAsc();

        Map<String, List<RolloTela>> agrupado = rollos.stream()
                .collect(java.util.stream.Collectors.groupingBy(r -> r.getColor() + "|" + r.getAncho()));

        for (Map.Entry<String, List<RolloTela>> entry : agrupado.entrySet()) {
            List<RolloTela> grupo = entry.getValue();

            List<RolloTela> conMaterial = grupo.stream()
                    .filter(r -> !r.isAgotado())
                    .collect(java.util.stream.Collectors.toList());

            if (conMaterial.isEmpty()) continue;

            String color = grupo.get(0).getColor();
            double ancho = grupo.get(0).getAncho();
            String titulo = "Tela " + color + " " + ancho + "m";

            if (conMaterial.size() == 1) {
                RolloTela unico = conMaterial.get(0);
                boolean aLaMitad = unico.getLargoRestante() <= (unico.getLargoInicial() / 2.0) + 0.001;

                if (aLaMitad) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.CRITICO,
                            titulo,
                            "Queda solo 1 rollo y ya está a la mitad o menos ("
                                    + redondear(unico.getLargoRestante()) + " m de "
                                    + redondear(unico.getLargoInicial()) + " m). Pedir pronto.",
                            "TELA"));
                } else {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.ADVERTENCIA,
                            titulo,
                            "Queda solo 1 rollo disponible ("
                                    + redondear(unico.getLargoRestante()) + " m restantes).",
                            "TELA"));
                }
            }
        }

        return alertas;
    }

    private List<AlertaInventario> alertasDeInsumos() {
        List<AlertaInventario> alertas = new ArrayList<>();

        List<Insumo> insumos = insumoRepository.findAllByOrderByNombreAsc();

        for (Insumo insumo : insumos) {
            if (Boolean.TRUE.equals(insumo.getTieneMedida())) {
                List<PiezaInsumo> piezas = piezaInsumoRepository.findByInsumoIdOrderByLargoRestanteAsc(insumo.getId());

                List<PiezaInsumo> conMaterial = piezas.stream()
                        .filter(p -> !p.isAgotada())
                        .collect(java.util.stream.Collectors.toList());

                double totalMetros = conMaterial.stream()
                        .mapToDouble(PiezaInsumo::getLargoRestante)
                        .sum();

                long piezasCompletas = conMaterial.stream()
                        .filter(p -> p.getLargoRestante() >= p.getLargoInicial() - 0.01)
                        .count();
                long piezasParciales = conMaterial.size() - piezasCompletas;

                double umbralCritico = (insumo.getUmbralAlerta() != null && insumo.getUmbralAlerta() > 0)
                        ? insumo.getUmbralAlerta()
                        : UMBRAL_METROS_CRITICO_DEFECTO;
                double umbralAdvertencia = umbralCritico * 2;

                if (totalMetros <= 0.001) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.AGOTADO,
                            insumo.getNombre(),
                            "No hay material disponible de \"" + insumo.getNombre() + "\".",
                            "INSUMO_MEDIDA"));

                } else if (piezasCompletas == 0) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.CRITICO,
                            insumo.getNombre(),
                            "Ya no quedan piezas completas de \"" + insumo.getNombre() + "\", solo "
                                    + piezasParciales + " retazo(s) sueltos (" + redondear(totalMetros)
                                    + " m en total). Pedir ya.",
                            "INSUMO_MEDIDA"));

                } else if (totalMetros < umbralCritico || piezasCompletas == 1) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.CRITICO,
                            insumo.getNombre(),
                            "Quedan solo " + piezasCompletas + " pieza(s) completa(s) y "
                                    + piezasParciales + " retazo(s) de \"" + insumo.getNombre() + "\" ("
                                    + redondear(totalMetros) + " m en total). Pedir ya.",
                            "INSUMO_MEDIDA"));

                } else if (totalMetros < umbralAdvertencia || piezasCompletas <= 2) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.ADVERTENCIA,
                            insumo.getNombre(),
                            "Quedan " + piezasCompletas + " pieza(s) completa(s) y " + piezasParciales
                                    + " retazo(s) de \"" + insumo.getNombre() + "\" (" + redondear(totalMetros)
                                    + " m en total). Conviene reponer pronto.",
                            "INSUMO_MEDIDA"));
                }

            } else {
                int stock = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;

                int umbralAdvertencia = (insumo.getUmbralAlerta() != null && insumo.getUmbralAlerta() > 0)
                        ? insumo.getUmbralAlerta()
                        : UMBRAL_UNIDAD_ADVERTENCIA_DEFECTO;
                int umbralCritico = Math.max(1, umbralAdvertencia / 10);

                if (stock == 0) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.AGOTADO,
                            insumo.getNombre(),
                            "No hay stock de \"" + insumo.getNombre() + "\".",
                            "INSUMO_UNIDAD"));
                } else if (stock <= umbralCritico) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.CRITICO,
                            insumo.getNombre(),
                            "Solo quedan " + stock + " unidad(es) de \"" + insumo.getNombre() + "\". Pedir ya.",
                            "INSUMO_UNIDAD"));
                } else if (stock < umbralAdvertencia) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.ADVERTENCIA,
                            insumo.getNombre(),
                            "Quedan " + stock + " unidades de \"" + insumo.getNombre() + "\". Conviene reponer pronto.",
                            "INSUMO_UNIDAD"));
                }
            }
        }

        return alertas;
    }

    // ═══════════════════════════════════════════════════════════════
    // INSUMOS EXTRA (agregados manualmente al pedido)
    // ═══════════════════════════════════════════════════════════════

    public void verificarExtras(List<ExtraInsumo> extras) {
        if (extras == null) return;
        for (ExtraInsumo ex : extras) {
            if (ex.cantidad <= 0) continue;
            if (ex.insumoId == null) continue;

            Insumo insumo = insumoRepository.findById(ex.insumoId)
                    .orElseThrow(() -> new MaterialInsuficienteException(
                            "El insumo extra seleccionado ya no existe en el catálogo."));

            if (Boolean.TRUE.equals(insumo.getTieneMedida())) {
                double disponible = piezaInsumoRepository
                        .findByInsumoIdOrderByLargoRestanteAsc(insumo.getId())
                        .stream().mapToDouble(PiezaInsumo::getLargoRestante).sum();
                if (disponible < ex.cantidad - 0.001) {
                    throw new MaterialInsuficienteException(
                            "No hay suficiente \"" + insumo.getNombre() + "\" para el insumo extra. Disponible: "
                            + redondear(disponible) + " m, necesario: " + ex.cantidad + " m.");
                }
            } else {
                int stock = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;
                if (stock < ex.cantidad) {
                    throw new MaterialInsuficienteException(
                            "No hay suficiente \"" + insumo.getNombre() + "\" para el insumo extra. Disponible: "
                            + stock + " unidad(es), necesario: " + (int) ex.cantidad + ".");
                }
            }
        }
    }

    public void procesarExtras(Pedido pedido, List<ExtraInsumo> extras) {
        if (extras == null) return;
        for (ExtraInsumo ex : extras) {
            if (ex.cantidad <= 0) continue;

            if (ex.insumoId == null) {
                MaterialUsado r = new MaterialUsado();
                r.setPedidoId(pedido.getId());
                r.setTipoMaterial("EXTRA");
                r.setFuenteDescripcion((ex.nombreLibre != null && !ex.nombreLibre.isBlank() ? ex.nombreLibre : "Insumo extra")
                        + " (fuera de catálogo, no descontado del inventario)");
                r.setMetrosUsados(ex.cantidad);
                r.setMetrosSobrantes(0.0);
                r.setSeleccionManual(true);
                materialUsadoRepository.save(r);
                continue;
            }

            Insumo insumo = insumoRepository.findById(ex.insumoId)
                    .orElseThrow(() -> new MaterialInsuficienteException(
                            "El insumo extra seleccionado ya no existe en el catálogo."));

            if (Boolean.TRUE.equals(insumo.getTieneMedida())) {
                List<PiezaInsumo> piezas = piezaInsumoRepository
                        .findByInsumoIdAndLargoRestanteGreaterThanOrderByLargoRestanteAsc(insumo.getId(), 0.0);
                double restante = ex.cantidad;
                for (PiezaInsumo p : piezas) {
                    if (restante <= 0.001) break;
                    double aUsar = redondear(Math.min(p.getLargoRestante(), restante));

                    MaterialUsado r = new MaterialUsado();
                    r.setPedidoId(pedido.getId());
                    r.setTipoMaterial(insumo.getNombre().toUpperCase().replace(" ", "_"));
                    r.setPiezaInsumoId(p.getId());
                    r.setFuenteDescripcion(insumo.getNombre() + " (#" + p.getId() + ", pieza original de "
                            + redondear(p.getLargoInicial()) + " m) — extra agregado al pedido");
                    r.setMetrosUsados(aUsar);
                    r.setSeleccionManual(true);

                    double sobrante = redondear(p.getLargoRestante() - aUsar);
                    if (sobrante < UMBRAL_DESCARTE_PIEZA) {
                        r.setMetrosSobrantes(0.0);
                        piezaInsumoRepository.delete(p);
                    } else {
                        p.setLargoRestante(sobrante);
                        piezaInsumoRepository.save(p);
                        r.setMetrosSobrantes(sobrante);
                    }
                    materialUsadoRepository.save(r);
                    restante -= aUsar;
                }
                if (restante > 0.001) {
                    throw new MaterialInsuficienteException(
                            "No hay suficiente \"" + insumo.getNombre() + "\" para completar el insumo extra.");
                }
            } else {
                int disponible = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;
                int necesario = (int) Math.round(ex.cantidad);
                if (disponible < necesario) {
                    throw new MaterialInsuficienteException(
                            "No hay suficiente \"" + insumo.getNombre() + "\" para el insumo extra. Disponible: "
                            + disponible + ", necesario: " + necesario + ".");
                }
                insumo.setStockUnidades(disponible - necesario);
                insumoRepository.save(insumo);

                MaterialUsado r = new MaterialUsado();
                r.setPedidoId(pedido.getId());
                r.setTipoMaterial(insumo.getNombre().toUpperCase().replace(" ", "_"));
                r.setFuenteDescripcion(insumo.getNombre() + " (unidad) — extra agregado al pedido");
                r.setMetrosUsados(necesario);
                r.setMetrosSobrantes(insumo.getStockUnidades());
                r.setSeleccionManual(true);
                materialUsadoRepository.save(r);
            }
        }
    }
}