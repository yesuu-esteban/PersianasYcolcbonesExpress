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
     * Tipo de pedido: "FABRICACION" (default), "VENTA_DIRECTA" o
     * "RIEL_ONDA_SERENA".
     */
    @Column(name = "tipo", nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'FABRICACION'")
    private String tipo = "FABRICACION";

    // Flags de estado
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

    /** Si el jefe elige el tubo manualmente (R8, R16, R24). Null o "AUTO" = automático. */
    @Column(name = "tubo_manual_elegido")
    private String tuboManualElegido;

    // ─── TRAZABILIDAD DE FECHAS ─────────────────────────────────────────────

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    // ─── RIEL DE ONDA SERENA ────────────────────────────────────────────────

    @Column(name = "usa_polea", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean usaPolea = false;

    /**
     * Solo aplica si usaPolea = true. Son mutuamente excluyentes: o lleva
     * terminal fijo, o lleva control de polea, nunca ambos.
     * Valores esperados: "TERMINAL" o "CONTROL_POLEA".
     *
     * FIX: se quitó "nullable = false" — este campo NO tiene sentido cuando
     * usaPolea = false (riel con bastón). La obligatoriedad condicional
     * (obligatorio SOLO si usaPolea = true) se valida en el controlador.
     */
    @Column(name = "terminal_polea")
    private String terminalPolea;

    /** Largo de impresión, solo si la tela lleva estampado. Null = no aplica. */
    @Column(name = "largo_impresion")
    private Double largoImpresion;

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

    // ─── TIPOS DE PEDIDO ────────────────────────────────────────────────────

    @Transient
    public boolean isVentaDirecta() {
        return "VENTA_DIRECTA".equalsIgnoreCase(this.tipo);
    }

    @Transient
    public boolean isRielOndaSerena() {
        return "RIEL_ONDA_SERENA".equalsIgnoreCase(this.tipo);
    }

    // ─── CÁLCULOS TRANSIENT — PERSIANA NORMAL ──────────────────────────────

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
        // FIX: se quitó el System.out.println("DEBUG: ...") que quedó
        // olvidado — se ejecutaba en cada render de la tabla de pedidos.
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

    @Transient
    public int getCantidadTapas() {
        return Boolean.TRUE.equals(usaCabezal) ? 2 : 0;
    }

    @Transient
    public int getCantidadSoportes() {
        return 2;
    }

    @Transient
    public int getCantidadTopePesa() {
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

    // ─── CÁLCULOS TRANSIENT — RIEL DE ONDA SERENA ──────────────────────────

    /**
     * Corte del riel: si usa polea, se resta 7.5cm al ancho.
     * Si no usa polea, el riel se corta exactamente al ancho.
     *
     * FIX: antes comparaba usaCabezal (bug de copy-paste); el Riel de Onda
     * Serena nunca usa cabezal, el descuento depende de usaPolea.
     */
    @Transient
    public double getCorteRiel() {
        if (Boolean.TRUE.equals(usaPolea)) {
            return Math.round((this.ancho - 0.075) * 1000.0) / 1000.0;
        }
        return this.ancho;
    }

    /**
     * Cuerda propia de Onda Serena: el doble del ancho más 4 metros.
     * Solo aplica si usaPolea = true.
     *
     * FIX: la fórmula original tenía (ancho * 2) * 4 (multiplicación) en vez
     * de (ancho * 2) + 4.0 (suma).
     */
    @Transient
    public double getCorteCuerdaOndaSerena() {
        if (!Boolean.TRUE.equals(usaPolea)) return 0.0;
        return Math.round(((this.ancho * 2) + 4.0) * 1000.0) / 1000.0;
    }

    /** El riel de pines siempre mide lo mismo que el riel ya cortado. */
    @Transient
    public double getCorteRielPines() {
        return getCorteRiel();
    }

    @Transient
    public int getCantidadPoleas() {
        return Boolean.TRUE.equals(usaPolea) ? 2 : 0;
    }

    @Transient
    public int getCantidadTapasRiel() {
        return Boolean.TRUE.equals(usaPolea) ? 0 : 2;
    }

    @Transient
    public int getCantidadBastones() {
        return Boolean.TRUE.equals(usaPolea) ? 0 : 1;
    }

    /** 1 si lleva polea y el terminal elegido es "TERMINAL", si no 0. */
    @Transient
    public int getCantidadTerminalPolea() {
        return Boolean.TRUE.equals(usaPolea) && "TERMINAL".equalsIgnoreCase(terminalPolea) ? 1 : 0;
    }

    /** 1 si lleva polea y el terminal elegido es "CONTROL_POLEA", si no 0. */
    @Transient
    public int getCantidadControlPolea() {
        return Boolean.TRUE.equals(usaPolea) && "CONTROL_POLEA".equalsIgnoreCase(terminalPolea) ? 1 : 0;
    }

    // ─── LÓGICA DE NEGOCIO ──────────────────────────────────────────────────

    public void calcularFichaTecnica() {
        // Las ventas directas no tienen ficha técnica de fabricación.
        if (isVentaDirecta()) {
            return;
        }

        // FIX: esta rama estaba incompleta ("if (isRielOndaSerena()){ this }")
        // y no compilaba. Ahora arma el resumen y CORTA la ejecución con
        // return, para no seguir hacia el cálculo de tubo/control de
        // persiana normal (que no aplica a un riel).
        if (isRielOndaSerena()) {
            this.rolloParaCortar = "Riel: " + getCorteRiel() + "m"
                    + " | Riel de pines: " + getCorteRielPines() + "m"
                    + (Boolean.TRUE.equals(usaPolea)
                        ? " | Cuerda onda serena: " + getCorteCuerdaOndaSerena() + "m"
                        : " | Con bastón")
                    + (this.largoImpresion != null ? " | Largo impresión: " + this.largoImpresion + "m" : "");
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