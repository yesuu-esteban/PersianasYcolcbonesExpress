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

    // ─── TRAZABILIDAD DE FECHAS ─────────────────────────────────────────────

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    /**
     * Se ejecuta automáticamente una sola vez, justo antes del primer guardado (INSERT).
     * Así no hay que recordar asignar la fecha manualmente en el controlador.
     */
    @PrePersist
    protected void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    /**
     * Se ejecuta automáticamente cada vez que se actualiza un registro existente (UPDATE).
     */
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

    // ─── CÁLCULOS TRANSIENT (visibles por Thymeleaf vía getters) ───────────────

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

    /**
     * Medida del cabezal: solo aplica cuando usaCabezal = true.
     * Es el mismo ancho menos un margen de 0.01 m a cada lado.
     */
    @Transient
    public double getMedidaCabezal() {
        return Math.round((this.ancho - 0.05) * 1000.0) / 1000.0;
    }

    @Transient
    public String getTipoSistema() {
        return Boolean.TRUE.equals(this.usaCabezal) ? "CON CABEZAL" : "SIN CABEZAL";
    }

    // ─── LÓGICA DE NEGOCIO ─────────────────────────────────────────────────────

    public void calcularFichaTecnica() {
        // 1. Tipo de control según ancho
        this.tipoControl = (this.ancho > 1.50) ? "Control A" : "Control B";

        // 2. Tubo según peso/tamaño (el cabezal siempre exige tubo más robusto)
        boolean esPesado = (this.ancho > 2.50 || this.altura > 2.50 || Boolean.TRUE.equals(this.usaCabezal));
        this.tuboRecomendado = esPesado ? "R24" : "R16";

        // 3. Longitud de cuerda
        this.medidaCuerda = (this.altura <= 1.50) ? "3 metros" : "4 metros";

        // 4. Resumen de corte persistido (para la tabla de pedidos)
        this.rolloParaCortar = "Tela: " + getCorteTelaAncho() + " x " + getCorteTelaAlto()
                             + " | Tubo: " + getCorteTuberia();
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