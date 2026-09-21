package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "pedido")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pedido {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String nombreDecorador = "";
    private String nombreClienteFinal = "";
    private String descripcion = "";

    private int cantidad = 1;
    private double altura = 0.0;
    private double ancho = 0.0;

    private String medidaCuerda = "0 metros";
    private String estado = "Pendiente";
    private String tipoControl = "";
    private String ladoControl = "";
    private String colorTelaDeseado = "";
    private String tuboRecomendado = "";
    private String rolloParaCortar = "";

    @Column(name = "tipo", nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'FABRICACION'")
    private String tipo = "FABRICACION";

    @Column(name = "usa_cabezal", nullable = false)
    private Boolean usaCabezal = false;

    @Column(nullable = false)
    private Boolean ensamblado = false;

    @Column(name = "despachado", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean despachado = false;

    @Column(name = "usa_pitillo_pesa", nullable = false)
    private Boolean usaPitilloPesa = true;

    @Column(name = "usa_conector_tope", nullable = false)
    private Boolean usaConectorTope = true;

    @Column(name = "usa_polea", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean usaPolea = false;

    @Column(name = "tubo_manual_elegido")
    private String tuboManualElegido;

    /** Solo aplica a Riel de Onda Serena sin polea: cuál de los bastones fijos elegido eligió el jefe. */
    @Column(name = "baston_elegido")
    private String bastonElegido;

    /**
     * Solo aplica a Riel de Onda Serena: hacia qué lado abre.
     * Valores: "Izquierda", "Derecha" o "Hacia los extremos".
     */
    @Column(name = "lado_apertura")
    private String ladoApertura;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        // Si el formulario ya trajo una fecha de inicio elegida manualmente
        // (fechaCreacion viene distinta de null), se respeta tal cual; si no
        // se especificó, se usa el momento actual como antes.
        if (this.fechaCreacion == null) {
            this.fechaCreacion = ahora;
        }
        this.fechaActualizacion = ahora;
        if (this.tipo == null || this.tipo.isBlank()) {
            this.tipo = "FABRICACION";
        }
    }

    @PreUpdate
    protected void alActualizar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transient
    public String getFechaCreacionFormateada() {
        return this.fechaCreacion != null ? this.fechaCreacion.format(FORMATO_FECHA) : "";
    }

    @Transient
    public String getFechaActualizacionFormateada() {
        return this.fechaActualizacion != null ? this.fechaActualizacion.format(FORMATO_FECHA) : "";
    }

    @Transient
    public boolean isVentaDirecta() {
        return "VENTA_DIRECTA".equalsIgnoreCase(this.tipo);
    }

    @Transient
    public boolean isRielOndaSerena() {
        return "RIEL_ONDA_SERENA".equalsIgnoreCase(this.tipo);
    }

    @Transient
    public double getCorteTelaAncho() {
        double descuento = Boolean.TRUE.equals(usaCabezal) ? 0.035 : 0.03;
        return Math.round((this.ancho - descuento) * 1000.0) / 1000.0;
    }

    @Transient
    public double getCorteTelaAlto() {
        return Math.round((this.altura + 0.20) * 1000.0) / 1000.0;
    }

    @Transient
    public double getCorteTuberia() {
        double descuento = Boolean.TRUE.equals(usaCabezal) ? 0.03 : 0.025;
        return Math.round((this.ancho - descuento) * 1000.0) / 1000.0;
    }

    @Transient
    public double getMedidaCabezal() {
        return Math.round((this.ancho - 0.005) * 1000.0) / 1000.0;
    }

    @Transient
    public double getMetrosCuerda() {
        return this.altura <= 1.50 ? 3.0 : 4.0;
    }

    @Transient
    public String getTipoSistema() {
        return Boolean.TRUE.equals(this.usaCabezal) ? "CON CABEZAL" : "SIN CABEZAL";
    }

    @Transient
    public String getRolloTela() {
        double ladoMenor = Math.min(this.ancho, this.altura);
        if (ladoMenor <= 1.83) {
            return "Rollo 1.83m";
        } else if (ladoMenor <= 2.50) {
            return "Rollo 2.50m";
        } else {
            return "Rollo 3.00m";
        }
    }

    @Transient
    public double getCortePitilloPesa() {
        return getCorteTelaAncho();
    }

    @Transient
    public int getCantidadConectores() {
        return esControlR16() ? 2 : 1;
    }

    @Transient
    public int getCantidadTopes() {
        return esControlR16() ? 0 : 1;
    }

    /**
     * Acoples: obligatorios en cualquiera de estos dos casos, independientes
     * entre sí:
     *  1) El corte de ancho llega a 2.0m o más (refuerzo por ancho grande).
     *  2) El tubo elegido es R24, sin importar el ancho — un tubo R24 es más
     *     pesado/rígido y necesita acoples de refuerzo aunque el pedido no
     *     llegue al tamaño de "Paquete de Control R24" (eso pasa cuando solo
     *     UNA de las dos medidas —ancho o alto— supera 2.50m: ahí el tubo ya
     *     es R24 pero el control se queda en R16, y hacen falta los acoples
     *     sueltos).
     * Cuando el control SÍ es el Paquete R24 (isUsaPaqueteControlR24(), que
     * requiere AMBAS medidas por encima de 2.50m), los acoples ya vienen
     * embebidos dentro del Paquete y no se descuentan sueltos — por eso este
     * método regresa 0 en ese caso, y es InventarioServicio quien decide no
     * descontarlos aparte usando isUsaPaqueteControlR24().
     */
    @Transient
    public int getCantidadAcoples() {
        if (isUsaPaqueteControlR24()) return 0;
        boolean necesarioPorAncho = getCorteTelaAncho() >= 2.0;
        boolean necesarioPorTuboR24 = "R24".equalsIgnoreCase(this.tuboRecomendado);
        return (necesarioPorAncho || necesarioPorTuboR24) ? 2 : 0;
    }

    @Transient
    public int getCantidadTerminal() {
        return 1;
    }

    @Transient
    public int getCantidadTapas() {
        return Boolean.TRUE.equals(usaCabezal) ? 2 : 0;
    }

    @Transient
    public int getCantidadSoportes() {
        return 2;
    }

    /**
     * Tapa Perfil (antes había también un ítem separado llamado "Tope Pesa",
     * que se fusionó aquí: eran el mismo accesorio físico, así que ahora solo
     * existe UN ítem — "Tapa Perfil" — con 2 unidades, obligatorio en TODO
     * pedido de fabricación, con o sin cabezal.
     */
    @Transient
    public int getCantidadTapasPerfil() {
        return 2;
    }

    @Transient
    public int getCantidadTornillos() {
        return Boolean.TRUE.equals(usaCabezal) ? 8 : 2;
    }

    @Transient
    public int getCantidadTornillosPerforantes() {
        return Boolean.TRUE.equals(usaCabezal) ? 4 : 0;
    }

    private boolean esControlR16() {
        return this.tipoControl != null && this.tipoControl.trim().startsWith("Control R16");
    }

    /**
     * Cuando el pedido usa Control R24, el control, terminal, acoples y
     * soportes ya NO se descuentan por separado del inventario: todo eso
     * viene físicamente empacado junto en un solo ítem, "Paquete de Control
     * R24", del que se descuenta 1 unidad. Este helper le indica a
     * InventarioServicio y a CalculadoraCostoFabricacionServicio cuándo
     * tomar esa ruta especial en vez de la normal (Control + Acople +
     * Terminal + Soporte por separado).
     *
     * Nota: esto responde SOLO a lo que diga tipoControl en este momento,
     * sea cual sea el origen (la recomendación automática de
     * calcularFichaTecnica() o un cambio manual que haga el jefe al editar
     * el pedido). Si el jefe fuerza manualmente "Control R24" aunque el
     * tamaño automático no lo hubiera elegido, el sistema igual usa el
     * Paquete completo — y viceversa.
     */
    @Transient
    public boolean isUsaPaqueteControlR24() {
        return "Control R24".equals(this.tipoControl);
    }

    @Transient
    public double getAnchoComercialUsado() {
        double largo = getCorteTelaAlto();
        if (largo <= 1.83) return 1.83;
        if (largo <= 2.50) return 2.50;
        return 3.00;
    }

    @Transient
    public double getPrecioVenta() {
        if (isVentaDirecta() || isRielOndaSerena()) return 0.0;

        double metrosCuadrados = this.ancho * this.altura;
        double anchoComercial = getAnchoComercialUsado();

        double valorPorM2;
        if (anchoComercial <= 1.83) {
            valorPorM2 = 51200.0;
        } else if (anchoComercial <= 2.50) {
            valorPorM2 = 53800.0;
        } else {
            valorPorM2 = 74250.0;
        }

        return Math.round(metrosCuadrados * valorPorM2);
    }

    // ─── CÁLCULOS TRANSIENT — RIEL DE ONDA SERENA ────────────────────────────

    @Transient
    public double getCorteRiel() {
        return Math.round((this.ancho - 0.075) * 1000.0) / 1000.0;
    }

    @Transient
    public double getCorteRielPines() {
        return getCorteRiel();
    }

    @Transient
    public double getMedidaRiata() {
        return getCorteRiel();
    }

    @Transient
    public double getCorteCuerdaOnda() {
        return Math.round((this.ancho * 2 + 4.0) * 1000.0) / 1000.0;
    }

    @Transient
    public int getCantidadPoleas() {
        return Boolean.TRUE.equals(this.usaPolea) ? 2 : 0;
    }

    /**
     * Crusador: obligatorio solo cuando el riel lleva polea.
     * Normalmente 1, pero si el riel abre "Hacia los extremos" se necesitan
     * 2 (uno en cada extremo).
     */
    @Transient
    public int getCantidadTerminalPolea() {
        if (!Boolean.TRUE.equals(this.usaPolea)) return 0;
        return "Hacia los extremos".equalsIgnoreCase(this.ladoApertura) ? 2 : 1;
    }

    @Transient
    public int getCantidadTapasRiel() {
        return Boolean.TRUE.equals(this.usaPolea) ? 0 : 2;
    }

    /**
     * Bastón: pieza fija por unidad (0.80 / 1.20 / 1.50 m según el elegido).
     * Solo aplica cuando el riel NO lleva polea. Normalmente 1 unidad
     * completa, pero si el riel abre "Hacia los extremos" se necesitan 2
     * unidades del mismo tipo elegido (una en cada extremo).
     */
    @Transient
    public int getCantidadBaston() {
        if (Boolean.TRUE.equals(this.usaPolea)) return 0;
        return "Hacia los extremos".equalsIgnoreCase(this.ladoApertura) ? 2 : 1;
    }

    @Transient
    public int getCantidadSoportesRiel() {
        int soportes;
        if (this.ancho >= 3.50) {
            soportes = 5;
        } else if (this.ancho >= 2.50) {
            soportes = 4;
        } else if (this.ancho >= 1.50) {
            soportes = 3;
        } else {
            soportes = 2;
        }
        return soportes;
    }

    // ─── LÓGICA DE NEGOCIO ──────────────────────────────────────────────────

    public void calcularFichaTecnica() {
        if (isVentaDirecta()) {
            return;
        }
        if (isRielOndaSerena()) {
            return;
        }

        // ── Tubo R24: se usa si CUALQUIERA de las dos medidas (ancho O
        // alto) supera 2.50m. ──
        // ── Control R24 (Paquete completo, con acoples/terminal/soportes
        // incluidos): solo se usa si AMBAS medidas superan 2.50m A LA VEZ.
        // Si solo una de las dos supera 2.50m, el tubo ya es R24 (por la
        // regla anterior) pero el control se queda en R16 — y como el tubo
        // R24 igual necesita refuerzo, se agregan 2 Acoples aparte (ver
        // getCantidadAcoples(), que ya revisa si tuboRecomendado es R24).
        // El cabezal no influye en ninguna de estas dos decisiones. ──
        boolean esTuboGrande = (this.ancho > 2.50 || this.altura > 2.50);
        boolean esPaqueteControlR24 = (this.ancho > 2.50 && this.altura > 2.50);

        if (this.tuboManualElegido != null && !this.tuboManualElegido.isBlank()
                && !"auto".equalsIgnoreCase(this.tuboManualElegido.trim())) {
            this.tuboRecomendado = this.tuboManualElegido.trim().toUpperCase();
        } else {
            this.tuboRecomendado = esTuboGrande ? "R24" : "R16";
        }

        // El control usa su PROPIA regla (esPaqueteControlR24), distinta a
        // la del tubo (esTuboGrande) — a propósito, porque un tubo R24
        // "suelto" (sin llegar al tamaño de Paquete) sigue usando Control
        // R16 + Acoples, no el Paquete completo. Si el tubo terminó siendo
        // R8 (elegido manualmente), el control va a Control R8 A sin
        // importar el tamaño.
        if ("R8".equalsIgnoreCase(this.tuboRecomendado)) {
            this.tipoControl = "Control R8 A";
        } else if (esPaqueteControlR24) {
            this.tipoControl = "Control R24";
        } else {
            this.tipoControl = (this.ancho > 1.50) ? "Control R16" : "Control R8 B";
        }

        this.medidaCuerda = (this.altura <= 1.50) ? "3 metros" : "4 metros";

        this.rolloParaCortar = "Tela: " + getCorteTelaAncho() + " x " + getCorteTelaAlto()
                            + " | Tubo: " + getCorteTuberia()
                            + " | " + getRolloTela();
    }

    public void calcularEstadoGeneral() {
        if (isVentaDirecta()) {
            this.estado = "Vendido";
            return;
        }

        if (Boolean.TRUE.equals(despachado)) {
            this.estado = "Despachado";
        } else if (Boolean.TRUE.equals(ensamblado)) {
            this.estado = "Finalizado";
        } else {
            this.estado = "Pendiente";
        }
    }
}