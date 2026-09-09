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

    // Información general
    private String nombreDecorador = "";
    private String nombreClienteFinal = "";
    private String descripcion = "";

    // Medidas y configuración
    private int cantidad = 1;
    private double altura = 0.0;
    private double ancho = 0.0;

    // Campos derivados persistidos
    private String medidaCuerda = "0 metros";
    private String estado = "Pendiente";
    private String tipoControl = "";
    private String ladoControl = "";
    private String colorTelaDeseado = "";
    private String tuboRecomendado = "";
    private String rolloParaCortar = "";

    /**
     * Tipo de pedido: "FABRICACION" (default, flujo normal con ficha técnica
     * y corte de tela/perfilería), "VENTA_DIRECTA" (venta de insumos/tela
     * suelta, sin fabricación) o "RIEL_ONDA_SERENA" (riel + accesorios,
     * sin tela ni tubo).
     *
     * IMPORTANTE — columnDefinition con DEFAULT:
     * Al declarar la columna como NOT NULL sin un valor por defecto a nivel
     * de base de datos, un ALTER TABLE automático (ddl-auto=update) sobre una
     * tabla que ya tiene filas falla, porque esas filas viejas no tendrían
     * ningún valor para la columna nueva. Con columnDefinition, si Hibernate
     * necesita crear la columna, la base de datos misma le pone 'FABRICACION'
     * a las filas existentes y el ALTER no se rompe.
     */
    @Column(name = "tipo", nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'FABRICACION'")
    private String tipo = "FABRICACION";

    // Flags de estado
    @Column(name = "usa_cabezal", nullable = false)
    private Boolean usaCabezal = false;

    /**
     * Marca que el pedido ya fue ensamblado en el taller.
     * Es lo que hace pasar el estado de "Pendiente" a "Finalizado".
     */
    @Column(nullable = false)
    private Boolean ensamblado = false;

    /**
     * Marca que el pedido ya salió despachado de la fábrica.
     * Solo tiene sentido activarlo si el pedido ya está ensamblado.
     * Es lo que hace pasar el estado de "Finalizado" a "Despachado".
     *
     * IMPORTANTE — columnDefinition con DEFAULT:
     * igual que con "tipo", si esta columna se crea con un ALTER TABLE
     * automático sobre una tabla que ya tiene filas, necesita un valor por
     * defecto a nivel de base de datos para no romper el ALTER.
     */
    @Column(name = "despachado", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean despachado = false;

    @Column(name = "usa_pitillo_pesa", nullable = false)
    private Boolean usaPitilloPesa = true;

    @Column(name = "usa_conector_tope", nullable = false)
    private Boolean usaConectorTope = true;

    /**
     * Solo aplica a pedidos tipo RIEL_ONDA_SERENA: si el riel lleva polea
     * (con poleas + cuerda + terminal/control) o no (con tapas + bastón).
     */
    @Column(name = "usa_polea", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean usaPolea = false;

    /** Si el jefe elige el tubo manualmente (R8, R16, R24). Null o "AUTO" = automático. */
    @Column(name = "tubo_manual_elegido")
    private String tuboManualElegido;

    // ─── TRAZABILIDAD DE FECHAS ─────────────────────────────────────────────

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        this.fechaCreacion = ahora;
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

    // ─── TIPO DE PEDIDO ─────────────────────────────────────────────────────

    @Transient
    public boolean isVentaDirecta() {
        return "VENTA_DIRECTA".equalsIgnoreCase(this.tipo);
    }

    @Transient
    public boolean isRielOndaSerena() {
        return "RIEL_ONDA_SERENA".equalsIgnoreCase(this.tipo);
    }

    // ─── CÁLCULOS TRANSIENT — FABRICACIÓN (ENROLLABLE / BLACKOUT) ───────────

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

    /**
     * Metros numéricos de cuerda según la altura del pedido.
     * 3.0 m si altura <= 1.50 m, 4.0 m si es mayor.
     */
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

        System.out.println("DEBUG: Ancho=" + this.ancho + " Alto=" + this.altura + " LadoMenor=" + ladoMenor);

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
     * Tapas de cabezal: siempre 2 unidades, pero SOLO si el pedido lleva cabezal.
     */
    @Transient
    public int getCantidadTapas() {
        return Boolean.TRUE.equals(usaCabezal) ? 2 : 0;
    }

    /**
     * Soportes de instalación: siempre 2, con o sin cabezal.
     */
    @Transient
    public int getCantidadSoportes() {
        return 2;
    }

    /**
     * Tope de pesa: siempre 2 unidades, obligatorio en todo pedido.
     */
    @Transient
    public int getCantidadTopePesa() {
        return 2;
    }

    /**
     * Tornillos normales:
     *   Sin cabezal → 2 (para los 2 soportes)
     *   Con cabezal → 8 (2 soportes + 6 para las 2 tapas)
     */
    @Transient
    public int getCantidadTornillos() {
        return Boolean.TRUE.equals(usaCabezal) ? 8 : 2;
    }

    /**
     * Tornillos perforantes:
     *   Sin cabezal → 0
     *   Con cabezal → 4
     */
    @Transient
    public int getCantidadTornillosPerforantes() {
        return Boolean.TRUE.equals(usaCabezal) ? 4 : 0;
    }

    private boolean esControlR16() {
        return this.tipoControl != null && this.tipoControl.trim().startsWith("Control R16");
    }

    // ─── CÁLCULOS TRANSIENT — RIEL DE ONDA SERENA ────────────────────────────

    /** Riel = Ancho - 0.075 m (siete y medio centímetros de descuento). */
    @Transient
    public double getCorteRiel() {
        return Math.round((this.ancho - 0.075) * 1000.0) / 1000.0;
    }

    /** El riel de pines corre el mismo largo que el riel ya cortado. */
    @Transient
    public double getCorteRielPines() {
        return getCorteRiel();
    }

    /** Solo aplica si usaPolea = true: el doble del ancho + 4 metros. */
    @Transient
    public double getCorteCuerdaOnda() {
        return Math.round((this.ancho * 2 + 4.0) * 1000.0) / 1000.0;
    }

    // ─── LÓGICA DE NEGOCIO ──────────────────────────────────────────────────

    public void calcularFichaTecnica() {
        // Las ventas directas no tienen ficha técnica de fabricación:
        // no hay corte de tela/tubo/cabezal calculado, solo ítems sueltos.
        if (isVentaDirecta()) {
            return;
        }

        // El riel de onda serena tampoco usa tela/tubo/cabezal: sus cortes
        // (riel, riel de pines, cuerda) ya están calculados como @Transient
        // arriba a partir de ancho/usaPolea, no necesitan nada más aquí.
        if (isRielOndaSerena()) {
            return;
        }

        // 1. Tubo: manual si el jefe lo eligió explícitamente, si no, automático por peso/tamaño
        if (this.tuboManualElegido != null && !this.tuboManualElegido.isBlank()
                && !"auto".equalsIgnoreCase(this.tuboManualElegido.trim())) {
            this.tuboRecomendado = this.tuboManualElegido.trim().toUpperCase();
        } else {
            boolean esPesado = (this.ancho > 2.50 || this.altura > 2.50 || Boolean.TRUE.equals(this.usaCabezal));
            this.tuboRecomendado = esPesado ? "R24" : "R16";
        }

        // 2. Tipo de control según ancho... excepto si el tubo elegido es R8 → siempre Control R8 A
        if ("R8".equalsIgnoreCase(this.tuboRecomendado)) {
            this.tipoControl = "Control R8 A";
        } else {
            this.tipoControl = (this.ancho > 1.50) ? "Control R16" : "Control R8 B";
        }

        // 3. Medida de cuerda como texto
        this.medidaCuerda = (this.altura <= 1.50) ? "3 metros" : "4 metros";

        // 4. Resumen persistido
        this.rolloParaCortar = "Tela: " + getCorteTelaAncho() + " x " + getCorteTelaAlto()
                            + " | Tubo: " + getCorteTuberia()
                            + " | " + getRolloTela();
    }

    /**
     * Estados del pedido:
     *   Pendiente  → recién creado, todavía no se ha ensamblado.
     *   Finalizado → ya se ensambló en el taller.
     *   Despachado → ya salió de la fábrica.
     * (Las ventas directas no pasan por este flujo, quedan como "Vendido").
     * Los rieles de onda serena SÍ pasan por este mismo flujo Pendiente →
     * Finalizado → Despachado, igual que fabricación normal.
     */
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