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

    // Flags de estado
    @Column(name = "usa_cabezal", nullable = false)
    private Boolean usaCabezal = false;

    @Column(nullable = false)
    private Boolean telaCortada = false;

    @Column(nullable = false)
    private Boolean perfileriaCortada = false;

    @Column(nullable = false)
    private Boolean ensamblado = false;

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

    @PrePersist
    protected void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
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

    // ─── CÁLCULOS TRANSIENT ─────────────────────────────────────────────────

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

    // ─── LÓGICA DE NEGOCIO ──────────────────────────────────────────────────

    public void calcularFichaTecnica() {
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
        if (Boolean.TRUE.equals(ensamblado)) {
            this.estado = "Listo para Despacho";
        } else if (Boolean.TRUE.equals(telaCortada) && Boolean.TRUE.equals(perfileriaCortada)) {
            this.estado = "Listo para Ensamblar";
        } else if (Boolean.TRUE.equals(telaCortada) || Boolean.TRUE.equals(perfileriaCortada)) {
            this.estado = "En Proceso";
        } else {
            this.estado = "Pendiente";
        }
    }
}