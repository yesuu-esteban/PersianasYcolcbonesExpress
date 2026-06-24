package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.*;
import Colcones_Persinas.proyecto_express.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class InventarioServicio {

    private final RolloTelaRepository rolloTelaRepository;
    private final InsumoRepository insumoRepository;
    private final PiezaInsumoRepository piezaInsumoRepository;
    private final MaterialUsadoRepository materialUsadoRepository;
    private final RetazoTelaRepository retazoTelaRepository;

    /** Umbral de descarte para pitillo: por debajo de esto, una pieza ya no sirve para nada y se elimina del inventario. */
    private static final double UMBRAL_DESCARTE_PITILLO = 0.05;

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
        public Integer retazoTelaId;   // nuevo: si viene, se usa retazo en lugar de rollo
        public Integer piezaTuboId;
        public Integer piezaCabezalId;
        public Integer piezaPesaId;
        public Integer piezaCuerdaId;
        public Integer piezaPitilloId;
    }

    public static class PrevisualizacionMaterial {
        public boolean disponible = true;
        public String rolloSugerido;
        public String retazoSugerido;  // nuevo: descripción del retazo sugerido (si aplica)
        public String tuboSugerido;
        public String cabezalSugerido;
        public String pesaSugerida;
        public String cuerdaSugerida;
        public String controlInfo;
        public String pitilloSugerido;
        public String conectorInfo;
        public String soporteInfo;   // nuevo
        public String tapaInfo;      // nuevo
        public List<String> faltantes = new ArrayList<>();
    }

    public static class ResumenMaterial {
        public String tipoMaterial;
        public double totalUsado;
        public String unidad;          // "m²", "m" o "unidad(es)"
        public int vecesUsado;
        public List<MaterialUsado> detalle;
    }

    // ═══════════════════════════════════════════════════════════════
    // BÚSQUEDA DE RETAZO
    // ═══════════════════════════════════════════════════════════════

    /**
     * Busca el retazo más ajustado (menor alto sobrante) que sirva para el pedido,
     * probando AMBAS orientaciones: la pieza puede entrar tal cual (ancho×alto) o
     * rotada (alto×ancho), igual que se hace con los rollos. Se queda con la mejor
     * opción entre las dos orientaciones (la de menor sobrante).
     * Retorna null si no hay ninguno — en ese caso se usa rollo normal.
     */
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

        // Se queda con el que deje menos sobrante "de alto" usado en cada orientación
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

    /**
     * Resuelve cómo cubrir la medida de pitillo necesaria:
     * 1) Si una sola pieza alcanza, se usa esa (la más pequeña que sirva).
     * 2) Si ninguna sola alcanza, combina varias piezas pequeñas (de menor a
     *    mayor) hasta sumar la medida necesaria, para no desperdiciar los
     *    retazos sueltos que ya quedaron de otros pedidos.
     * Lanza MaterialInsuficienteException si ni combinando todo lo disponible alcanza.
     */
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

    /**
     * Descuenta una pieza de pitillo. Si el sobrante después del corte es
     * menor o igual al umbral de descarte, no queda como pieza reutilizable:
     * se elimina directamente del inventario en vez de guardarse como un
     * retazo inservible.
     */
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
        r.setFuenteDescripcion(pieza.getInsumo().getNombre() + " (#" + pieza.getId() + ")"
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

    /**
     * Descuenta de un retazo. Detecta automáticamente la orientación: si el retazo
     * entra "directo" (ancho>=corteTelaAncho y alto>=corteTelaAlto) descuenta del alto;
     * si solo entra "rotado" (ancho>=corteTelaAlto y alto>=corteTelaAncho) descuenta
     * igual del alto pero comparando contra el corte rotado. En ambos casos lo que se
     * reduce es el campo "alto" del retazo porque es la dimensión que se va consumiendo
     * al cortar tiras de la pieza.
     */
    public MaterialUsado descontarRetazo(Pedido pedido, RetazoTela retazo, boolean manual) {
        double corteAncho = pedido.getCorteTelaAncho();
        double corteAlto = pedido.getCorteTelaAlto();

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
        r.setFuenteDescripcion("Retazo " + retazo.getColor() + " " + retazo.getAncho()
                + "m × " + retazo.getAlto() + "m (#" + retazo.getId() + ")");
        r.setMetrosUsados(altoUsado);
        r.setMetrosSobrantes(sobrante);
        r.setMetrosCuadrados(redondear(pedido.getCorteTelaAncho() * pedido.getCorteTelaAlto()));
        r.setSeleccionManual(manual);
        return materialUsadoRepository.save(r);
    }

    public MaterialUsado descontarTela(Pedido pedido, RolloTela rollo, double metros, boolean manual) {
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
        r.setFuenteDescripcion("Rollo " + rollo.getColor() + " " + rollo.getAncho() + "m (#" + rollo.getId() + ")");
        r.setMetrosUsados(metros);
        r.setMetrosSobrantes(rollo.getLargoRestante());
        r.setMetrosCuadrados(redondear(pedido.getCorteTelaAncho() * pedido.getCorteTelaAlto()));
        r.setSeleccionManual(manual);
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
        r.setFuenteDescripcion(pieza.getInsumo().getNombre() + " (#" + pieza.getId() + ")");
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
    // VERIFICACIÓN PREVIA (sin descontar nada)
    // ═══════════════════════════════════════════════════════════════

    public void verificarDisponibilidad(Pedido pedido) {
        // 1. Tela: primero busca retazo, si no hay busca rollo
        RetazoTela retazo = buscarMejorRetazo(
                pedido.getColorTelaDeseado(),
                pedido.getCorteTelaAncho(),
                pedido.getCorteTelaAlto());
        if (retazo == null) {
            buscarMejorRollo(pedido.getColorTelaDeseado(), anchoComercialDe(pedido), metrosADescontarDeRollo(pedido));
        }

        // 2. Tubo
        Insumo tubo = obtenerInsumoPorNombre("Tubo " + pedido.getTuboRecomendado());
        buscarMejorPieza(tubo, pedido.getCorteTuberia());

        // 3. Cabezal
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            Insumo cabezal = obtenerInsumoPorNombre("Cabezal");
            buscarMejorPieza(cabezal, pedido.getMedidaCabezal());
        }

        // 4. Pesa
        Insumo pesa = obtenerInsumoPorNombre("Pesa");
        buscarMejorPieza(pesa, pedido.getCorteTuberia());

        // 5. Cuerda
        Insumo cuerda = obtenerInsumoPorNombre("Cuerda");
        buscarMejorPieza(cuerda, pedido.getMetrosCuerda());

        // 6. Control
        Insumo control = obtenerInsumoPorNombre(pedido.getTipoControl().trim());
        int stockControl = control.getStockUnidades() != null ? control.getStockUnidades() : 0;
        if (stockControl < 1) {
            throw new MaterialInsuficienteException(
                    "No hay stock de \"" + control.getNombre() + "\". Disponible: 0 unidades.");
        }

        // 7. Pitillo (puede combinar retazos pequeños si ninguna pieza sola alcanza)
        if (Boolean.TRUE.equals(pedido.getUsaPitilloPesa())) {
            Insumo pitillo = obtenerInsumoPorNombre("Pitillo");
            resolverPitillo(pitillo, pedido.getCortePitilloPesa());
        }

        // 8. Conector + Tope
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

        // 9. Soportes (siempre 2, con o sin cabezal)
        Insumo soporte = obtenerInsumoPorNombre("Soporte");
        int stockSoporte = soporte.getStockUnidades() != null ? soporte.getStockUnidades() : 0;
        if (stockSoporte < pedido.getCantidadSoportes()) {
            throw new MaterialInsuficienteException(
                    "No hay suficiente \"Soporte\". Disponible: " + stockSoporte
                    + " unidad(es), necesario: " + pedido.getCantidadSoportes() + ".");
        }

        // 10. Tapas (solo si usa cabezal)
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            Insumo tapa = obtenerInsumoPorNombre("Tapa");
            int stockTapa = tapa.getStockUnidades() != null ? tapa.getStockUnidades() : 0;
            if (stockTapa < pedido.getCantidadTapas()) {
                throw new MaterialInsuficienteException(
                        "No hay suficiente \"Tapa\". Disponible: " + stockTapa
                        + " unidad(es), necesario: " + pedido.getCantidadTapas() + ".");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DESCUENTO REAL
    // ═══════════════════════════════════════════════════════════════

    public void descontarMaterialDe(Pedido pedido) {
        descontarMaterialDe(pedido, null);
    }

    public void descontarMaterialDe(Pedido pedido, SeleccionManual sel) {

        // 1. Tela: prioridad retazo → rollo
        if (sel != null && sel.retazoTelaId != null) {
            // Jefe eligió retazo manualmente
            RetazoTela retazo = obtenerRetazoPorId(sel.retazoTelaId);
            descontarRetazo(pedido, retazo, true);
        } else if (sel != null && sel.rolloTelaId != null) {
            // Jefe eligió rollo manualmente
            RolloTela rollo = obtenerRolloPorId(sel.rolloTelaId);
            descontarTela(pedido, rollo, metrosADescontarDeRollo(pedido), true);
        } else {
            // Automático: retazo primero, si no hay, rollo
            RetazoTela retazo = buscarMejorRetazo(
                    pedido.getColorTelaDeseado(),
                    pedido.getCorteTelaAncho(),
                    pedido.getCorteTelaAlto());
            if (retazo != null) {
                descontarRetazo(pedido, retazo, false);
            } else {
                RolloTela rollo = buscarMejorRollo(
                        pedido.getColorTelaDeseado(), anchoComercialDe(pedido), metrosADescontarDeRollo(pedido));
                descontarTela(pedido, rollo, metrosADescontarDeRollo(pedido), false);
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

        // 8. Conector + Tope
        if (Boolean.TRUE.equals(pedido.getUsaConectorTope())) {
            descontarInsumoPorUnidad(pedido, "Conector", pedido.getCantidadConectores());
            if (pedido.getCantidadTopes() > 0) {
                descontarInsumoPorUnidad(pedido, "Tope Control", pedido.getCantidadTopes());
            }
        }

        // 9. Soportes (siempre 2)
        descontarInsumoPorUnidad(pedido, "Soporte", pedido.getCantidadSoportes());

        // 10. Tapas (solo si usa cabezal)
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            descontarInsumoPorUnidad(pedido, "Tapa", pedido.getCantidadTapas());
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

        // 1. Tela: retazo primero, si no hay, rollo
        intentar(res, () -> {
            RetazoTela retazo = buscarMejorRetazo(
                    pedido.getColorTelaDeseado(),
                    pedido.getCorteTelaAncho(),
                    pedido.getCorteTelaAlto());
            if (retazo != null) {
                double sobrante = redondear(retazo.getAlto() - pedido.getCorteTelaAlto());
                res.retazoSugerido = "✂ Retazo " + retazo.getColor() + " " + retazo.getAncho()
                        + "m × " + retazo.getAlto() + "m (#" + retazo.getId()
                        + ") · quedarían " + sobrante + " m de alto";
            } else {
                RolloTela r = buscarMejorRollo(
                        pedido.getColorTelaDeseado(), anchoComercialDe(pedido), metrosADescontarDeRollo(pedido));
                res.rolloSugerido = "Rollo " + r.getColor() + " " + r.getAncho() + "m (#" + r.getId()
                        + ") · quedarían " + redondear(r.getLargoRestante() - metrosADescontarDeRollo(pedido)) + " m";
            }
        });

        // 2. Tubo
        intentar(res, () -> {
            Insumo tubo = obtenerInsumoPorNombre("Tubo " + pedido.getTuboRecomendado());
            PiezaInsumo p = buscarMejorPieza(tubo, pedido.getCorteTuberia());
            res.tuboSugerido = tubo.getNombre() + " (#" + p.getId() + ") · quedarían "
                    + redondear(p.getLargoRestante() - pedido.getCorteTuberia()) + " m";
        });

        // 3. Cabezal
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            intentar(res, () -> {
                Insumo cab = obtenerInsumoPorNombre("Cabezal");
                PiezaInsumo p = buscarMejorPieza(cab, pedido.getMedidaCabezal());
                res.cabezalSugerido = "Cabezal (#" + p.getId() + ") · quedarían "
                        + redondear(p.getLargoRestante() - pedido.getMedidaCabezal()) + " m";
            });
        }

        // 4. Pesa
        intentar(res, () -> {
            Insumo pesa = obtenerInsumoPorNombre("Pesa");
            PiezaInsumo p = buscarMejorPieza(pesa, pedido.getCorteTuberia());
            res.pesaSugerida = "Pesa (#" + p.getId() + ") · quedarían "
                    + redondear(p.getLargoRestante() - pedido.getCorteTuberia()) + " m";
        });

        // 5. Cuerda
        intentar(res, () -> {
            Insumo cuerda = obtenerInsumoPorNombre("Cuerda");
            PiezaInsumo p = buscarMejorPieza(cuerda, pedido.getMetrosCuerda());
            res.cuerdaSugerida = "Cuerda (#" + p.getId() + ") · quedarían "
                    + redondear(p.getLargoRestante() - pedido.getMetrosCuerda()) + " m";
        });

        // 6. Control
        intentar(res, () -> {
            Insumo control = obtenerInsumoPorNombre(pedido.getTipoControl().trim());
            int stock = control.getStockUnidades() != null ? control.getStockUnidades() : 0;
            if (stock < 1) throw new MaterialInsuficienteException("Sin stock de \"" + control.getNombre() + "\".");
            res.controlInfo = control.getNombre() + " · quedarían " + (stock - 1) + " unidad(es)";
        });

        // 7. Pitillo (puede combinar retazos pequeños)
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

        // 8. Conector + Tope
        if (Boolean.TRUE.equals(pedido.getUsaConectorTope())) {
            intentar(res, () -> {
                Insumo conector = obtenerInsumoPorNombre("Conector");
                int stockConector = conector.getStockUnidades() != null ? conector.getStockUnidades() : 0;
                int necesario = pedido.getCantidadConectores();
                if (stockConector < necesario) {
                    throw new MaterialInsuficienteException("Sin stock suficiente de \"Conector\".");
                }
                String info = "Conector ×" + necesario + " · quedarían " + (stockConector - necesario);
                if (pedido.getCantidadTopes() > 0) {
                    Insumo tope = obtenerInsumoPorNombre("Tope Control");
                    int stockTope = tope.getStockUnidades() != null ? tope.getStockUnidades() : 0;
                    if (stockTope < pedido.getCantidadTopes()) {
                        throw new MaterialInsuficienteException("Sin stock suficiente de \"Tope Control\".");
                    }
                    info += " | Tope ×" + pedido.getCantidadTopes() + " · quedarían "
                            + (stockTope - pedido.getCantidadTopes());
                }
                res.conectorInfo = info;
            });
        }

        // 9. Soportes (siempre 2)
        intentar(res, () -> {
            Insumo soporte = obtenerInsumoPorNombre("Soporte");
            int stock = soporte.getStockUnidades() != null ? soporte.getStockUnidades() : 0;
            int necesario = pedido.getCantidadSoportes();
            if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Soporte\".");
            res.soporteInfo = "Soporte ×" + necesario + " · quedarían " + (stock - necesario);
        });

        // 10. Tapas (solo si usa cabezal)
        if (Boolean.TRUE.equals(pedido.getUsaCabezal())) {
            intentar(res, () -> {
                Insumo tapa = obtenerInsumoPorNombre("Tapa");
                int stock = tapa.getStockUnidades() != null ? tapa.getStockUnidades() : 0;
                int necesario = pedido.getCantidadTapas();
                if (stock < necesario) throw new MaterialInsuficienteException("Sin stock suficiente de \"Tapa\".");
                res.tapaInfo = "Tapa ×" + necesario + " · quedarían " + (stock - necesario);
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

    /**
     * Ancho COMERCIAL del rollo necesario (1.83 / 2.50 / 3.00).
     * Se basa en el lado MENOR del corte de tela, porque la pieza se puede
     * rotar: el lado menor es el que se acomoda dentro del ancho del rollo,
     * y el lado mayor es el que se va "desenrollando" a lo largo del rollo.
     */
    public double anchoComercialDe(Pedido pedido) {
        double menor = Math.min(pedido.getCorteTelaAncho(), pedido.getCorteTelaAlto());
        if (menor <= 1.83) return 1.83;
        if (menor <= 2.50) return 2.50;
        return 3.00;
    }

    /**
     * Cuántos metros de LARGO del rollo se consumen realmente al cortar esta
     * pieza. Es el lado MAYOR del corte de tela (el que se desenrolla),
     * complementario a anchoComercialDe().
     */
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
            } else if (r.detalle.get(0).getPiezaInsumoId() != null) {
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
}