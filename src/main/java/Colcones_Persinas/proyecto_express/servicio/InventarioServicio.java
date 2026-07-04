package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.*;
import Colcones_Persinas.proyecto_express.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class InventarioServicio {

    private final RolloTelaRepository rolloTelaRepository;
    private final InsumoRepository insumoRepository;
    private final PiezaInsumoRepository piezaInsumoRepository;
    private final MaterialUsadoRepository materialUsadoRepository;
    private final RetazoTelaRepository retazoTelaRepository;

    private static final double UMBRAL_DESCARTE_PITILLO = 0.05;
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
        public String topePesaInfo;
        public String tornilloInfo;
        public String tornilloPerforanteInfo;
        public List<String> faltantes = new ArrayList<>();
    }

    public static class ResumenMaterial {
        public String tipoMaterial;
        public double totalUsado;
        public String unidad;
        public int vecesUsado;
        public List<MaterialUsado> detalle;
    }

    /**
     * Nivel de severidad de una alerta de inventario.
     * ADVERTENCIA = amarillo (ya hay que pensar en pedir más).
     * CRITICO     = rojo (pedir ya, riesgo real de quedarse sin material).
     * AGOTADO     = gris/negro (ya no hay nada).
     */
    public enum NivelAlerta {
        ADVERTENCIA, CRITICO, AGOTADO
    }

    public static class AlertaInventario {
        public NivelAlerta nivel;
        public String titulo;       // ej: "Tela Blanco 1.83m"
        public String mensaje;      // ej: "Solo queda 1 rollo (12.5 m restantes)"
        public String categoria;    // "TELA", "INSUMO_UNIDAD", "INSUMO_MEDIDA"

        public AlertaInventario(NivelAlerta nivel, String titulo, String mensaje, String categoria) {
            this.nivel = nivel;
            this.titulo = titulo;
            this.mensaje = mensaje;
            this.categoria = categoria;
        }
    }

    /**
     * Insumo agregado manualmente al pedido (fuera del cálculo automático),
     * por ejemplo "2 Tornillos extra" o "1 Control de más" que el jefe
     * agrega a discreción para una orden específica.
     *
     * insumoId != null  → existe en el catálogo, se descuenta del inventario real.
     * insumoId == null  → nombre libre, no existe en catálogo; solo queda
     *                      registrado en el reporte, no se descuenta nada.
     */
    public static class ExtraInsumo {
        public Integer insumoId;
        public String nombreLibre;
        public double cantidad;
    }

    /**
     * Ítem de tela vendida directamente por metros lineales (Venta Directa),
     * fuera del flujo de fabricación con corte calculado. rolloId permite
     * seleccionar un rollo específico; si es null se busca automáticamente
     * por color + ancho comercial.
     */
    public static class ItemTelaVenta {
        public Integer rolloId;
        public String color;
        public double ancho;
        public double metros;
    }

    // ── Umbrales centralizados de alertas ───────────────────────
    // Insumos POR UNIDAD (ej: Control, Tornillo, Conector...):
    // se alerta cuando quedan MENOS de 50 unidades; si además quedan
    // 5 o menos, se marca como CRITICO en vez de ADVERTENCIA.
    private static final int UMBRAL_UNIDAD_ADVERTENCIA = 50; // < 50 -> advertencia
    private static final int UMBRAL_UNIDAD_CRITICO = 5;      // <= 5 -> critico

    // Insumos POR MEDIDA (ej: Tubo, Cuerda, Pesa...), contando piezas
    // completas disponibles: se alerta únicamente cuando quedan MENOS de 5.
    private static final int UMBRAL_PIEZAS_CRITICO = 5;      // < 5 piezas -> alerta (critico)

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
        if (pieza.getLargoRestante() < metros - 0.001) {
            throw new MaterialInsuficienteException(
                    "Pieza #" + pieza.getId() + " (" + pieza.getInsumo().getNombre() + ") no tiene suficiente material ("
                    + pieza.getLargoRestante() + " m disponibles, " + metros + " m necesarios).");
        }

        double sobrante = redondear(pieza.getLargoRestante() - metros);

        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial(pieza.getInsumo().getNombre().toUpperCase().replace(" ", "_"));
        r.setPiezaInsumoId(pieza.getId());
        r.setFuenteDescripcion(pieza.getInsumo().getNombre() + " (#" + pieza.getId()
                + ", pieza original de " + redondear(pieza.getLargoInicial()) + " m)"
                + (combinado ? " [combinado con otra(s) pieza(s)]" : ""));
        r.setMetrosUsados(metros);
        r.setSeleccionManual(manual);

        if (sobrante <= UMBRAL_DESCARTE_PITILLO) {
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
    // DESCUENTOS UNITARIOS
    // ═══════════════════════════════════════════════════════════════

    public MaterialUsado descontarRetazo(Pedido pedido, RetazoTela retazo, boolean manual) {
        double corteAncho = pedido.getCorteTelaAncho();
        double corteAlto = pedido.getCorteTelaAlto();

        // Guardamos la medida ORIGINAL del retazo antes de recortarlo, para
        // que el reporte siempre muestre de qué tamaño era la pieza de la
        // que se cortó, sin importar si el retazo termina agotado o no.
        double anchoOriginal = retazo.getAncho();
        double altoOriginal = retazo.getAlto();

        boolean entraDirecto = retazo.getAncho() >= corteAncho - 0.001 && retazo.getAlto() >= corteAlto - 0.001;
        boolean entraRotado = retazo.getAncho() >= corteAlto - 0.001 && retazo.getAlto() >= corteAncho - 0.001;

        double altoUsado;
        if (entraDirecto && (!entraRotado || retazo.getAlto() - corteAlto <= retazo.getAlto() - corteAncho)) {
            altoUsado = corteAlto;
        } else if (entraRotado) {
            altoUsado = corteAncho;
        } else {
            throw new MaterialInsuficienteException(
                    "Retazo #" + retazo.getId() + " no tiene medidas suficientes ("
                    + retazo.getAncho() + "m × " + retazo.getAlto() + "m disponibles, se necesitan "
                    + corteAncho + "m × " + corteAlto + "m, en cualquier orientación).");
        }

        double sobrante = redondear(retazo.getAlto() - altoUsado);
        if (sobrante <= 0.05) {
            retazoTelaRepository.delete(retazo);
        } else {
            retazo.setAlto(sobrante);
            retazoTelaRepository.save(retazo);
        }
        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial("RETAZO");
        r.setFuenteDescripcion("Retazo " + retazo.getColor() + " (#" + retazo.getId()
                + ") — medida original " + anchoOriginal + "m × " + altoOriginal + "m");
        r.setMetrosUsados(altoUsado);
        r.setMetrosSobrantes(sobrante);
        r.setMetrosCuadrados(redondear(pedido.getCorteTelaAncho() * pedido.getCorteTelaAlto()));
        r.setSeleccionManual(manual);
        return materialUsadoRepository.save(r);
    }

    public MaterialUsado descontarTela(Pedido pedido, RolloTela rollo, double metros, boolean manual) {
        return descontarTela(pedido, rollo, metros, manual, false, 0.0);
    }

    public MaterialUsado descontarTela(Pedido pedido, RolloTela rollo, double metros, boolean manual,
                                        boolean esSustituto, double anchoOriginalNecesario) {
        if (rollo.getLargoRestante() < metros - 0.001) {
            throw new MaterialInsuficienteException(
                    "Rollo #" + rollo.getId() + " no tiene suficiente material ("
                    + rollo.getLargoRestante() + " m disponibles, " + metros + " m necesarios).");
        }
        rollo.setLargoRestante(redondear(rollo.getLargoRestante() - metros));
        rolloTelaRepository.save(rollo);

        String fuente = "Rollo " + rollo.getColor() + " " + rollo.getAncho() + "m (#" + rollo.getId()
                + ", rollo original de " + redondear(rollo.getLargoInicial()) + " m)";
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
        r.setMetrosSobrantes(rollo.getLargoRestante());
        r.setMetrosCuadrados(redondear(pedido.getCorteTelaAncho() * pedido.getCorteTelaAlto()));
        r.setSeleccionManual(manual);
        return materialUsadoRepository.save(r);
    }

    /**
     * Descuenta tela vendida directamente por metros lineales (Venta Directa),
     * sin pasar por el cálculo de corte de fabricación. El área se calcula
     * como ancho del rollo × metros vendidos.
     */
    public MaterialUsado descontarTelaVentaDirecta(Pedido pedido, RolloTela rollo, double metros) {
        if (rollo.getLargoRestante() < metros - 0.001) {
            throw new MaterialInsuficienteException(
                    "Rollo #" + rollo.getId() + " no tiene suficiente material ("
                    + rollo.getLargoRestante() + " m disponibles, " + metros + " m necesarios).");
        }
        rollo.setLargoRestante(redondear(rollo.getLargoRestante() - metros));
        rolloTelaRepository.save(rollo);

        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial("TELA");
        r.setRolloTelaId(rollo.getId());
        r.setFuenteDescripcion("Rollo " + rollo.getColor() + " " + rollo.getAncho()
                + "m (#" + rollo.getId() + ", rollo original de " + redondear(rollo.getLargoInicial())
                + " m) — venta directa");
        r.setMetrosUsados(metros);
        r.setMetrosSobrantes(rollo.getLargoRestante());
        r.setMetrosCuadrados(redondear(rollo.getAncho() * metros));
        r.setSeleccionManual(false);
        return materialUsadoRepository.save(r);
    }

    public MaterialUsado descontarInsumoConMedida(Pedido pedido, PiezaInsumo pieza, double metros, boolean manual) {
        if (pieza.getLargoRestante() < metros - 0.001) {
            throw new MaterialInsuficienteException(
                    "Pieza #" + pieza.getId() + " (" + pieza.getInsumo().getNombre() + ") no tiene suficiente material ("
                    + pieza.getLargoRestante() + " m disponibles, " + metros + " m necesarios).");
        }
        pieza.setLargoRestante(redondear(pieza.getLargoRestante() - metros));
        piezaInsumoRepository.save(pieza);
        MaterialUsado r = new MaterialUsado();
        r.setPedidoId(pedido.getId());
        r.setTipoMaterial(pieza.getInsumo().getNombre().toUpperCase().replace(" ", "_"));
        r.setPiezaInsumoId(pieza.getId());
        r.setFuenteDescripcion(pieza.getInsumo().getNombre() + " (#" + pieza.getId()
                + ", pieza original de " + redondear(pieza.getLargoInicial()) + " m)");
        r.setMetrosUsados(metros);
        r.setMetrosSobrantes(pieza.getLargoRestante());
        r.setSeleccionManual(manual);
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
        r.setFuenteDescripcion(insumo.getNombre() + " (unidad)");
        r.setMetrosUsados(unidades);
        r.setMetrosSobrantes(insumo.getStockUnidades());
        r.setSeleccionManual(false);
        return materialUsadoRepository.save(r);
    }

    // ═══════════════════════════════════════════════════════════════
    // VERIFICACIÓN PREVIA — respeta la selección manual del jefe
    // ═══════════════════════════════════════════════════════════════

    /** Atajo sin selección manual (para compatibilidad con código existente). */
    public void verificarDisponibilidad(Pedido pedido) {
        verificarDisponibilidad(pedido, null);
    }

    /**
     * Verifica que hay material suficiente para el pedido.
     * Si hay selección manual para un material, valida ESE rollo/retazo/pieza específico.
     * Si no hay selección manual, usa la lógica automática (buscar la mejor opción disponible).
     * No descuenta nada — solo lanza MaterialInsuficienteException si algo falta.
     */
    public void verificarDisponibilidad(Pedido pedido, SeleccionManual sel) {

        // 1. Tela: retazo manual > rollo manual > retazo automático > rollo automático
        if (sel != null && sel.retazoTelaId != null) {
            verificarRetazoManual(pedido, sel.retazoTelaId);
        } else if (sel != null && sel.rolloTelaId != null) {
            verificarRolloManual(pedido, sel.rolloTelaId);
        } else {
            RetazoTela retazo = buscarMejorRetazo(
                    pedido.getColorTelaDeseado(),
                    pedido.getCorteTelaAncho(),
                    pedido.getCorteTelaAlto());
            if (retazo == null) {
                buscarMejorRolloConSustituto(
                        pedido.getColorTelaDeseado(), anchoComercialDe(pedido), metrosADescontarDeRollo(pedido));
            }
        }

        // 2. Tubo
        Insumo tubo = obtenerInsumoPorNombre("Tubo " + pedido.getTuboRecomendado());
        if (sel != null && sel.piezaTuboId != null) {
            verificarPiezaManual(sel.piezaTuboId, tubo, pedido.getCorteTuberia());
        } else {
            buscarMejorPieza(tubo, pedido.getCorteTuberia());
        }

        // 3. Cabezal
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            Insumo cabezal = obtenerInsumoPorNombre("Cabezal");
            if (sel != null && sel.piezaCabezalId != null) {
                verificarPiezaManual(sel.piezaCabezalId, cabezal, pedido.getMedidaCabezal());
            } else {
                buscarMejorPieza(cabezal, pedido.getMedidaCabezal());
            }
        }

        // 4. Pesa
        Insumo pesa = obtenerInsumoPorNombre("Pesa");
        if (sel != null && sel.piezaPesaId != null) {
            verificarPiezaManual(sel.piezaPesaId, pesa, pedido.getCorteTuberia());
        } else {
            buscarMejorPieza(pesa, pedido.getCorteTuberia());
        }

        // 5. Cuerda
        Insumo cuerda = obtenerInsumoPorNombre("Cuerda");
        if (sel != null && sel.piezaCuerdaId != null) {
            verificarPiezaManual(sel.piezaCuerdaId, cuerda, pedido.getMetrosCuerda());
        } else {
            buscarMejorPieza(cuerda, pedido.getMetrosCuerda());
        }

        // 6. Control (siempre automático — no hay selección manual para controles)
        Insumo control = obtenerInsumoPorNombre(pedido.getTipoControl().trim());
        int stockControl = control.getStockUnidades() != null ? control.getStockUnidades() : 0;
        if (stockControl < 1) {
            throw new MaterialInsuficienteException(
                    "No hay stock de \"" + control.getNombre() + "\". Disponible: 0 unidades.");
        }

        // 7. Pitillo
        if (Boolean.TRUE.equals(pedido.getUsaPitilloPesa())) {
            Insumo pitillo = obtenerInsumoPorNombre("Pitillo");
            if (sel != null && sel.piezaPitilloId != null) {
                verificarPiezaManual(sel.piezaPitilloId, pitillo, pedido.getCortePitilloPesa());
            } else {
                resolverPitillo(pitillo, pedido.getCortePitilloPesa());
            }
        }

        // 8. Conector + Tope control
        if (Boolean.TRUE.equals(pedido.getUsaConectorTope())) {
            Insumo conector = obtenerInsumoPorNombre("Conector");
            int stockConector = conector.getStockUnidades() != null ? conector.getStockUnidades() : 0;
            int necesarioConector = pedido.getCantidadConectores();
            if (stockConector < necesarioConector) {
                throw new MaterialInsuficienteException(
                        "No hay suficiente \"Conector\". Disponible: " + stockConector
                        + " unidad(es), necesario: " + necesarioConector + ".");
            }
            if (pedido.getCantidadTopes() > 0) {
                Insumo tope = obtenerInsumoPorNombre("Tope Control");
                int stockTope = tope.getStockUnidades() != null ? tope.getStockUnidades() : 0;
                if (stockTope < pedido.getCantidadTopes()) {
                    throw new MaterialInsuficienteException(
                            "No hay suficiente \"Tope Control\". Disponible: " + stockTope
                            + " unidad(es), necesario: " + pedido.getCantidadTopes() + ".");
                }
            }
        }

        // 9. Soportes (siempre 2)
        Insumo soporte = obtenerInsumoPorNombre("Soporte");
        int stockSoporte = soporte.getStockUnidades() != null ? soporte.getStockUnidades() : 0;
        if (stockSoporte < pedido.getCantidadSoportes()) {
            throw new MaterialInsuficienteException(
                    "No hay suficiente \"Soporte\". Disponible: " + stockSoporte
                    + " unidad(es), necesario: " + pedido.getCantidadSoportes() + ".");
        }

        // 10. Tapas (solo con cabezal)
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            Insumo tapa = obtenerInsumoPorNombre("Tapa");
            int stockTapa = tapa.getStockUnidades() != null ? tapa.getStockUnidades() : 0;
            if (stockTapa < pedido.getCantidadTapas()) {
                throw new MaterialInsuficienteException(
                        "No hay suficiente \"Tapa\". Disponible: " + stockTapa
                        + " unidad(es), necesario: " + pedido.getCantidadTapas() + ".");
            }
        }

        // 11. Tope Pesa (siempre 2)
        Insumo topePesa = obtenerInsumoPorNombre("Tope Pesa");
        int stockTopePesa = topePesa.getStockUnidades() != null ? topePesa.getStockUnidades() : 0;
        if (stockTopePesa < pedido.getCantidadTopePesa()) {
            throw new MaterialInsuficienteException(
                    "No hay suficiente \"Tope Pesa\". Disponible: " + stockTopePesa
                    + " unidad(es), necesario: " + pedido.getCantidadTopePesa() + ".");
        }

        // 12. Tornillos normales
        Insumo tornillo = obtenerInsumoPorNombre("Tornillo");
        int stockTornillo = tornillo.getStockUnidades() != null ? tornillo.getStockUnidades() : 0;
        if (stockTornillo < pedido.getCantidadTornillos()) {
            throw new MaterialInsuficienteException(
                    "No hay suficiente \"Tornillo\". Disponible: " + stockTornillo
                    + " unidad(es), necesario: " + pedido.getCantidadTornillos() + ".");
        }

        // 13. Tornillos perforantes (solo con cabezal)
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            Insumo tornilloPerf = obtenerInsumoPorNombre("Tornillo Perforante");
            int stockTornilloPerf = tornilloPerf.getStockUnidades() != null ? tornilloPerf.getStockUnidades() : 0;
            if (stockTornilloPerf < pedido.getCantidadTornillosPerforantes()) {
                throw new MaterialInsuficienteException(
                        "No hay suficiente \"Tornillo Perforante\". Disponible: " + stockTornilloPerf
                        + " unidad(es), necesario: " + pedido.getCantidadTornillosPerforantes() + ".");
            }
        }
    }

    // ── Helpers de verificación manual ──────────────────────────

    private void verificarRetazoManual(Pedido pedido, int retazoId) {
        RetazoTela retazo = obtenerRetazoPorId(retazoId);
        double corteAncho = pedido.getCorteTelaAncho();
        double corteAlto = pedido.getCorteTelaAlto();
        boolean entraDirecto = retazo.getAncho() >= corteAncho - 0.001 && retazo.getAlto() >= corteAlto - 0.001;
        boolean entraRotado  = retazo.getAncho() >= corteAlto - 0.001  && retazo.getAlto() >= corteAncho - 0.001;
        if (!entraDirecto && !entraRotado) {
            throw new MaterialInsuficienteException(
                    "El retazo #" + retazoId + " elegido no tiene medidas suficientes ("
                    + retazo.getAncho() + "m × " + retazo.getAlto() + "m disponibles, se necesitan "
                    + corteAncho + "m × " + corteAlto + "m, en cualquier orientación).");
        }
        if (!retazo.getColor().equalsIgnoreCase(pedido.getColorTelaDeseado())) {
            throw new MaterialInsuficienteException(
                    "El retazo #" + retazoId + " es de color \"" + retazo.getColor()
                    + "\", pero el pedido necesita color \"" + pedido.getColorTelaDeseado() + "\".");
        }
    }

    private void verificarRolloManual(Pedido pedido, int rolloId) {
        RolloTela rollo = obtenerRolloPorId(rolloId);
        double necesario = metrosADescontarDeRollo(pedido);
        if (!rollo.getColor().equalsIgnoreCase(pedido.getColorTelaDeseado())) {
            throw new MaterialInsuficienteException(
                    "El rollo #" + rolloId + " es de color \"" + rollo.getColor()
                    + "\", pero el pedido necesita color \"" + pedido.getColorTelaDeseado() + "\".");
        }
        if (rollo.getLargoRestante() < necesario - 0.001) {
            throw new MaterialInsuficienteException(
                    "El rollo #" + rolloId + " no tiene suficiente material ("
                    + rollo.getLargoRestante() + " m disponibles, " + necesario + " m necesarios).");
        }
    }

    private void verificarPiezaManual(int piezaId, Insumo insumoEsperado, double metrosNecesarios) {
        PiezaInsumo pieza = obtenerPiezaPorId(piezaId);
        if (pieza.getInsumo() == null || pieza.getInsumo().getId() != insumoEsperado.getId()) {
            String nombreReal = pieza.getInsumo() != null ? pieza.getInsumo().getNombre() : "desconocido";
            throw new MaterialInsuficienteException(
                    "La pieza #" + piezaId + " es de \"" + nombreReal
                    + "\", pero se esperaba una pieza de \"" + insumoEsperado.getNombre() + "\".");
        }
        if (pieza.getLargoRestante() < metrosNecesarios - 0.001) {
            throw new MaterialInsuficienteException(
                    "La pieza #" + piezaId + " (" + insumoEsperado.getNombre() + ") no tiene suficiente material ("
                    + pieza.getLargoRestante() + " m disponibles, " + metrosNecesarios + " m necesarios).");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DESCUENTO REAL
    // ═══════════════════════════════════════════════════════════════

    public void descontarMaterialDe(Pedido pedido) {
        descontarMaterialDe(pedido, null);
    }

    public void descontarMaterialDe(Pedido pedido, SeleccionManual sel) {

        // 1. Tela: retazo manual > rollo manual > retazo automático > rollo automático
        if (sel != null && sel.retazoTelaId != null) {
            RetazoTela retazo = obtenerRetazoPorId(sel.retazoTelaId);
            descontarRetazo(pedido, retazo, true);
        } else if (sel != null && sel.rolloTelaId != null) {
            RolloTela rollo = obtenerRolloPorId(sel.rolloTelaId);
            descontarTela(pedido, rollo, metrosADescontarDeRollo(pedido), true);
        } else {
            RetazoTela retazo = buscarMejorRetazo(
                    pedido.getColorTelaDeseado(),
                    pedido.getCorteTelaAncho(),
                    pedido.getCorteTelaAlto());
            if (retazo != null) {
                descontarRetazo(pedido, retazo, false);
            } else {
                RolloEncontrado encontrado = buscarMejorRolloConSustituto(
                        pedido.getColorTelaDeseado(), anchoComercialDe(pedido), metrosADescontarDeRollo(pedido));
                descontarTela(pedido, encontrado.rollo, metrosADescontarDeRollo(pedido), false,
                        encontrado.esSustituto, anchoComercialDe(pedido));
            }
        }

        // 2. Tubo
        Insumo tubo = obtenerInsumoPorNombre("Tubo " + pedido.getTuboRecomendado());
        PiezaInsumo piezaTubo = (sel != null && sel.piezaTuboId != null)
                ? obtenerPiezaPorId(sel.piezaTuboId)
                : buscarMejorPieza(tubo, pedido.getCorteTuberia());
        descontarInsumoConMedida(pedido, piezaTubo, pedido.getCorteTuberia(), sel != null && sel.piezaTuboId != null);

        // 3. Cabezal
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            Insumo cabezal = obtenerInsumoPorNombre("Cabezal");
            PiezaInsumo piezaCabezal = (sel != null && sel.piezaCabezalId != null)
                    ? obtenerPiezaPorId(sel.piezaCabezalId)
                    : buscarMejorPieza(cabezal, pedido.getMedidaCabezal());
            descontarInsumoConMedida(pedido, piezaCabezal, pedido.getMedidaCabezal(),
                    sel != null && sel.piezaCabezalId != null);
        }

        // 4. Pesa
        Insumo pesa = obtenerInsumoPorNombre("Pesa");
        PiezaInsumo piezaPesa = (sel != null && sel.piezaPesaId != null)
                ? obtenerPiezaPorId(sel.piezaPesaId)
                : buscarMejorPieza(pesa, pedido.getCorteTuberia());
        descontarInsumoConMedida(pedido, piezaPesa, pedido.getCorteTuberia(), sel != null && sel.piezaPesaId != null);

        // 5. Cuerda
        Insumo cuerda = obtenerInsumoPorNombre("Cuerda");
        PiezaInsumo piezaCuerda = (sel != null && sel.piezaCuerdaId != null)
                ? obtenerPiezaPorId(sel.piezaCuerdaId)
                : buscarMejorPieza(cuerda, pedido.getMetrosCuerda());
        descontarInsumoConMedida(pedido, piezaCuerda, pedido.getMetrosCuerda(),
                sel != null && sel.piezaCuerdaId != null);

        // 6. Control
        descontarInsumoPorUnidad(pedido, pedido.getTipoControl().trim(), 1);

        // 7. Pitillo
        if (Boolean.TRUE.equals(pedido.getUsaPitilloPesa())) {
            Insumo pitillo = obtenerInsumoPorNombre("Pitillo");
            if (sel != null && sel.piezaPitilloId != null) {
                PiezaInsumo piezaManual = obtenerPiezaPorId(sel.piezaPitilloId);
                descontarPiezaPitillo(pedido, piezaManual, pedido.getCortePitilloPesa(), true, false);
            } else {
                List<PiezaInsumo> piezasPitillo = resolverPitillo(pitillo, pedido.getCortePitilloPesa());
                boolean combinado = piezasPitillo.size() > 1;
                double restante = pedido.getCortePitilloPesa();
                for (int i = 0; i < piezasPitillo.size(); i++) {
                    PiezaInsumo p = piezasPitillo.get(i);
                    boolean esUltima = (i == piezasPitillo.size() - 1);
                    double aUsar = esUltima ? restante : p.getLargoRestante();
                    descontarPiezaPitillo(pedido, p, aUsar, false, combinado);
                    restante -= aUsar;
                }
            }
        }

        // 8. Conector + Tope control
        if (Boolean.TRUE.equals(pedido.getUsaConectorTope())) {
            descontarInsumoPorUnidad(pedido, "Conector", pedido.getCantidadConectores());
            if (pedido.getCantidadTopes() > 0) {
                descontarInsumoPorUnidad(pedido, "Tope Control", pedido.getCantidadTopes());
            }
        }

        // 9. Soportes
        descontarInsumoPorUnidad(pedido, "Soporte", pedido.getCantidadSoportes());

        // 10. Tapas
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            descontarInsumoPorUnidad(pedido, "Tapa", pedido.getCantidadTapas());
        }

        // 11. Tope Pesa
        descontarInsumoPorUnidad(pedido, "Tope Pesa", pedido.getCantidadTopePesa());

        // 12. Tornillos normales
        descontarInsumoPorUnidad(pedido, "Tornillo", pedido.getCantidadTornillos());

        // 13. Tornillos perforantes
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            descontarInsumoPorUnidad(pedido, "Tornillo Perforante", pedido.getCantidadTornillosPerforantes());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VENTA DIRECTA — verificación y descuento de ítems de tela suelta
    // ═══════════════════════════════════════════════════════════════

    /**
     * Verifica disponibilidad de una lista de ítems de tela vendida por metros.
     * Si el ítem trae rolloId, valida ese rollo específico (color + metros).
     * Si no, busca automáticamente por color + ancho comercial declarado.
     * No descuenta nada, solo lanza excepción si algo falta.
     */
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
                buscarMejorRollo(it.color, it.ancho, it.metros); // lanza MaterialInsuficienteException si no hay
            }
        }
    }

    /**
     * Descuenta del inventario real cada ítem de tela vendida por metros.
     * Debe llamarse DESPUÉS de guardar el Pedido, para que ya tenga id.
     */
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
    // REVERSIÓN DE MATERIAL (para edición de pedidos)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Revierte TODOS los descuentos de material de un pedido ya guardado.
     * Devuelve metros/unidades a rollos, piezas e insumos.
     * Los retazos que se borraron al usarse se recrean.
     * Luego borra todos los registros MaterialUsado del pedido.
     * Debe llamarse dentro de un contexto @Transactional.
     */
    public void revertirMaterialDe(int pedidoId) {
        List<MaterialUsado> registros = materialUsadoRepository.findByPedidoIdOrderByFechaAsc(pedidoId);
        if (registros.isEmpty()) return;

        for (MaterialUsado m : registros) {
            if ("TELA".equals(m.getTipoMaterial())) {
                if (m.getRolloTelaId() != null) {
                    rolloTelaRepository.findById(m.getRolloTelaId()).ifPresent(rollo -> {
                        rollo.setLargoRestante(redondear(rollo.getLargoRestante() + m.getMetrosUsados()));
                        rolloTelaRepository.save(rollo);
                    });
                }
            } else if ("RETAZO".equals(m.getTipoMaterial())) {
                RetazoTela retazoRecuperado = reconstruirRetazoDesdeFuente(m);
                if (retazoRecuperado != null) {
                    retazoTelaRepository.save(retazoRecuperado);
                }
            } else if (m.getPiezaInsumoId() != null) {
                piezaInsumoRepository.findById(m.getPiezaInsumoId()).ifPresent(pieza -> {
                    pieza.setLargoRestante(redondear(pieza.getLargoRestante() + m.getMetrosUsados()));
                    piezaInsumoRepository.save(pieza);
                });
            } else if (!"EXTRA".equals(m.getTipoMaterial())) {
                String nombreInsumo = m.getTipoMaterial().replace("_", " ");
                insumoRepository.findByNombreIgnoreCase(nombreInsumo).ifPresent(insumo -> {
                    int actual = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;
                    insumo.setStockUnidades(actual + (int) m.getMetrosUsados());
                    insumoRepository.save(insumo);
                });
            }
            // "EXTRA" puro (texto libre fuera de catálogo): no había nada que devolver.
        }

        materialUsadoRepository.deleteAll(registros);
    }

    private RetazoTela reconstruirRetazoDesdeFuente(MaterialUsado m) {
        try {
            String desc = m.getFuenteDescripcion();
            if (desc == null || !desc.startsWith("Retazo ")) return null;

            String color;
            double ancho;

            // Formato NUEVO: "Retazo COLOR (#ID) — medida original ANCHOm × ALTOm"
            int idxMedidaOriginal = desc.indexOf("medida original ");
            if (idxMedidaOriginal >= 0) {
                int idxParentesis = desc.indexOf(" (#");
                if (idxParentesis <= 0) return null;
                color = desc.substring("Retazo ".length(), idxParentesis).trim();
                String medidas = desc.substring(idxMedidaOriginal + "medida original ".length());
                String anchoTexto = medidas.split("×")[0].replace("m", "").trim();
                ancho = Double.parseDouble(anchoTexto);
            } else {
                // Formato HEREDADO (registros guardados antes de este cambio):
                // "Retazo COLOR ANCHOm × ALTOm (#ID)"
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

            // El alto original siempre se recupera de metrosUsados + metrosSobrantes,
            // independientemente del formato del texto (esto no cambió).
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
        for (Pedido p : pedidos) verificarDisponibilidad(p);
        for (Pedido p : pedidos) descontarMaterialDe(p);
    }

    // ═══════════════════════════════════════════════════════════════
    // PREVISUALIZACIÓN EN VIVO
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

        intentar(res, () -> {
            Insumo control = obtenerInsumoPorNombre(pedido.getTipoControl().trim());
            int stock = control.getStockUnidades() != null ? control.getStockUnidades() : 0;
            if (stock < 1) throw new MaterialInsuficienteException("Sin stock de \"" + control.getNombre() + "\".");
            res.controlInfo = control.getNombre() + " · quedarían " + (stock - 1) + " unidad(es)";
        });

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

        intentar(res, () -> {
            Insumo soporte = obtenerInsumoPorNombre("Soporte");
            int stock = soporte.getStockUnidades() != null ? soporte.getStockUnidades() : 0;
            int necesario = pedido.getCantidadSoportes();
            if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Soporte\".");
            res.soporteInfo = "Soporte ×" + necesario + " · quedarían " + (stock - necesario);
        });

        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            intentar(res, () -> {
                Insumo tapa = obtenerInsumoPorNombre("Tapa");
                int stock = tapa.getStockUnidades() != null ? tapa.getStockUnidades() : 0;
                int necesario = pedido.getCantidadTapas();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Tapa\".");
                res.tapaInfo = "Tapa ×" + necesario + " · quedarían " + (stock - necesario);
            });
        }

        intentar(res, () -> {
            Insumo topePesa = obtenerInsumoPorNombre("Tope Pesa");
            int stock = topePesa.getStockUnidades() != null ? topePesa.getStockUnidades() : 0;
            int necesario = pedido.getCantidadTopePesa();
            if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Tope Pesa\".");
            res.topePesaInfo = "Tope Pesa ×" + necesario + " · quedarían " + (stock - necesario);
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
        double menor = Math.min(pedido.getCorteTelaAncho(), pedido.getCorteTelaAlto());
        if (menor <= 1.83) return 1.83;
        if (menor <= 2.50) return 2.50;
        return 3.00;
    }

    public double metrosADescontarDeRollo(Pedido pedido) {
        return Math.max(pedido.getCorteTelaAncho(), pedido.getCorteTelaAlto());
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

    /**
     * Calcula todas las alertas de stock bajo del inventario actual:
     * rollos de tela (agrupados por color+ancho), insumos por unidad,
     * e insumos por medida (piezas). No descuenta ni modifica nada,
     * solo lee el estado actual.
     */
    public List<AlertaInventario> obtenerAlertasInventario() {
        List<AlertaInventario> alertas = new ArrayList<>();

        alertas.addAll(alertasDeTela());
        alertas.addAll(alertasDeInsumos());

        // Orden: primero lo más urgente (AGOTADO/CRITICO), después advertencias
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

    // ── Alertas de tela (por color + ancho) ─────────────────────────

    private List<AlertaInventario> alertasDeTela() {
        List<AlertaInventario> alertas = new ArrayList<>();

        List<RolloTela> rollos = rolloTelaRepository.findAllByOrderByColorAscAnchoAscLargoRestanteAsc();

        // Agrupar por color + ancho exacto (ej: "Blanco|1.83")
        Map<String, List<RolloTela>> agrupado = rollos.stream()
                .collect(java.util.stream.Collectors.groupingBy(r -> r.getColor() + "|" + r.getAncho()));

        for (Map.Entry<String, List<RolloTela>> entry : agrupado.entrySet()) {
            List<RolloTela> grupo = entry.getValue();

            // Solo nos importan los rollos que todavía tienen algo de material
            List<RolloTela> conMaterial = grupo.stream()
                    .filter(r -> !r.isAgotado())
                    .collect(java.util.stream.Collectors.toList());

            if (conMaterial.isEmpty()) continue; // ya se muestra como "Agotados" en el resumen general

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
            // Si hay 2 o más rollos disponibles de ese color+ancho, no se alerta todavía.
        }

        return alertas;
    }

    // ── Alertas de insumos (por unidad y por medida/piezas) ─────────

    private List<AlertaInventario> alertasDeInsumos() {
        List<AlertaInventario> alertas = new ArrayList<>();

        List<Insumo> insumos = insumoRepository.findAllByOrderByNombreAsc();

        for (Insumo insumo : insumos) {
            if (Boolean.TRUE.equals(insumo.getTieneMedida())) {
                // ── Insumo por medida (tubo, cuerda, pesa, etc.): se cuentan
                //    las piezas COMPLETAS con material disponible. Solo se
                //    alerta cuando quedan MENOS de 5 piezas (umbral único,
                //    sin nivel intermedio de "advertencia" a 10).
                List<PiezaInsumo> piezas = piezaInsumoRepository.findByInsumoIdOrderByLargoRestanteAsc(insumo.getId());
                long piezasDisponibles = piezas.stream().filter(p -> !p.isAgotada()).count();

                if (piezasDisponibles == 0) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.AGOTADO,
                            insumo.getNombre(),
                            "No hay piezas disponibles de \"" + insumo.getNombre() + "\".",
                            "INSUMO_MEDIDA"));
                } else if (piezasDisponibles < UMBRAL_PIEZAS_CRITICO) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.CRITICO,
                            insumo.getNombre(),
                            "Quedan solo " + piezasDisponibles + " pieza(s) de \"" + insumo.getNombre() + "\". Pedir ya.",
                            "INSUMO_MEDIDA"));
                }
                // 5 piezas o más disponibles -> todavía no se alerta.

            } else {
                // ── Insumo por unidad (ej: Control, Tornillo, Conector...):
                //    se alerta cuando quedan MENOS de 50 unidades; si además
                //    quedan 5 o menos, se marca como CRITICO en vez de ADVERTENCIA.
                int stock = insumo.getStockUnidades() != null ? insumo.getStockUnidades() : 0;

                if (stock == 0) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.AGOTADO,
                            insumo.getNombre(),
                            "No hay stock de \"" + insumo.getNombre() + "\".",
                            "INSUMO_UNIDAD"));
                } else if (stock <= UMBRAL_UNIDAD_CRITICO) {
                    alertas.add(new AlertaInventario(
                            NivelAlerta.CRITICO,
                            insumo.getNombre(),
                            "Solo quedan " + stock + " unidad(es) de \"" + insumo.getNombre() + "\". Pedir ya.",
                            "INSUMO_UNIDAD"));
                } else if (stock < UMBRAL_UNIDAD_ADVERTENCIA) {
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

    /** Verifica que haya stock suficiente para TODOS los extras antes de guardar nada. */
    public void verificarExtras(List<ExtraInsumo> extras) {
        if (extras == null) return;
        for (ExtraInsumo ex : extras) {
            if (ex.cantidad <= 0) continue;
            if (ex.insumoId == null) continue; // texto libre: no se valida contra inventario

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

    /**
     * Descuenta del inventario real cada extra y deja el registro en MaterialUsado
     * (así aparece en el reporte como gasto real). Llamar DESPUÉS de guardar el pedido,
     * para ya tener su id.
     *
     * A los extras que SÍ están en catálogo se les pone como tipoMaterial el nombre
     * del insumo (igual que el material automático), para que revertirMaterialDe()
     * pueda devolver el stock correctamente al editar/eliminar, y para que el reporte
     * los agrupe junto con el resto del consumo de ese mismo insumo. Se distinguen
     * como "extra" solo por la fuenteDescripcion y seleccionManual=true.
     */
    public void procesarExtras(Pedido pedido, List<ExtraInsumo> extras) {
        if (extras == null) return;
        for (ExtraInsumo ex : extras) {
            if (ex.cantidad <= 0) continue;

            if (ex.insumoId == null) {
                // No existe en el catálogo: queda registrado para el reporte,
                // pero no hay de dónde descontarlo.
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
                    if (sobrante <= 0.001) {
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