package Colcones_Persinas.proyecto_express.modelo.instalaciones;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.usuarios.Usuario;

/**
 * Una tarea en la agenda de los instaladores.
 *
 * TIPO: INSTALACION, LIMPIEZA, ARREGLO, COTIZACION u OTRO.
 *   Una INSTALACION puede ir ligada a un pedido de almacén que está "En Bodega";
 *   al terminarla, ese pedido pasa a "Instalado".
 *
 * INSTALADORES: 1 o 2 por tarea (MAX_INSTALADORES). El primero de la lista es el principal.
 *
 * ORIGEN (quién la puso y, por lo tanto, quién la puede editar/eliminar):
 *   ADMIN      → la puso el jefe (TIENDA_ADMIN / ADMIN). SOLO el jefe la edita o elimina;
 *                para el instalador es de solo lectura.
 *   INSTALADOR → tarea PERSONAL del instalador (sin pedido de almacén). SOLO él la edita o
 *                elimina; el jefe la ve pero no la puede tocar.
 *
 * COLOR: solo las INSTALACIONES cambian de color (verde = instalada / pedido "Instalado" o
 * "Terminado"; rojo = pasó la hora sin instalarse). Los demás tipos conservan su color;
 * gris = cancelada.
 */
@Entity
@Table(name = "agenda_tarea")
@Getter @Setter @NoArgsConstructor
public class TareaCalendario {

    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");
    public static final int MAX_INSTALADORES = 2;

    // ── Tipos ──
    public static final String INSTALACION = "INSTALACION";
    public static final String LIMPIEZA    = "LIMPIEZA";
    public static final String ARREGLO     = "ARREGLO";
    public static final String COTIZACION  = "COTIZACION";
    public static final String OTRO        = "OTRO";
    public static final List<String> TIPOS = List.of(INSTALACION, LIMPIEZA, ARREGLO, COTIZACION, OTRO);

    // ── Estados ──
    public static final String PROGRAMADA = "PROGRAMADA";
    public static final String EN_CURSO   = "EN_CURSO";
    public static final String COMPLETADA = "COMPLETADA";
    public static final String CANCELADA  = "CANCELADA";
    public static final List<String> ESTADOS = List.of(PROGRAMADA, EN_CURSO, COMPLETADA, CANCELADA);
    public static final List<String> ESTADOS_ACTIVOS = List.of(PROGRAMADA, EN_CURSO);

    // ── Estados del PEDIDO de almacén que significan "ya quedó instalado" ──
    public static final List<String> ESTADOS_PEDIDO_TERMINADO = List.of("Instalado", "Terminado");

    // ── Colores de estado en el calendario ──
    public static final String COLOR_TERMINADA = "#2f9e44";   // verde
    public static final String COLOR_VENCIDA   = "#e03131";   // rojo
    public static final String COLOR_CANCELADA = "#868e96";   // gris

    // ── Origen ──
    public static final String ORIGEN_ADMIN      = "ADMIN";
    public static final String ORIGEN_INSTALADOR = "INSTALADOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private String tipo = INSTALACION;

    @Column(nullable = false)
    private String origen = ORIGEN_ADMIN;

    /** 1 o 2 instaladores. @OrderColumn conserva el orden: el primero es el principal. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "agenda_tarea_instalador",
            joinColumns = @JoinColumn(name = "tarea_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_id"))
    @OrderColumn(name = "posicion")
    private List<Usuario> instaladores = new ArrayList<>();

    /** Solo para INSTALACION puesta por el jefe desde "Instalaciones pendientes". Puede ser null. */
    @ManyToOne
    @JoinColumn(name = "pedido_tienda_id")
    private PedidoTienda pedidoTienda;

    /** Asunto corto. Obligatorio en OTRO; opcional en los demás. */
    private String titulo = "";

    // Datos de contacto. Si hay pedido, se copian del pedido al guardar.
    private String cliente = "";
    private String direccion = "";
    private String telefono = "";

    @Column(name = "fecha_programada", nullable = false)
    private LocalDateTime fechaProgramada;

    @Column(name = "duracion_minutos", nullable = false)
    private int duracionMinutos = 120;

    @Column(nullable = false)
    private String estado = PROGRAMADA;

    @Column(length = 1000)
    private String notas = "";

    @Column(name = "observaciones_instalador", length = 1000)
    private String observacionesInstalador = "";

    /** Username de quien la creó. */
    @Column(name = "creado_por")
    private String creadoPor = "";

    /** Username del instalador que la marcó como terminada. */
    @Column(name = "completada_por")
    private String completadaPor = "";

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_inicio_real")
    private LocalDateTime fechaInicioReal;

    @Column(name = "fecha_completada")
    private LocalDateTime fechaCompletada;

    @PrePersist
    protected void alCrear() {
        if (this.fechaCreacion == null) this.fechaCreacion = LocalDateTime.now(ZONA_COLOMBIA);
    }

    // ─── Lógica ─────────────────────────────────────────────────────────

    @Transient
    public LocalDateTime getFechaFin() {
        return fechaProgramada != null ? fechaProgramada.plusMinutes(duracionMinutos) : null;
    }

    @Transient
    public boolean isActiva() {
        return ESTADOS_ACTIVOS.contains(estado);
    }

    /**
     * VERDE: la tarea se marcó como terminada, o es una instalación cuyo pedido
     * el almacén ya pasó a "Instalado" o "Terminado".
     */
    @Transient
    public boolean isTerminada() {
        if (COMPLETADA.equals(estado)) return true;
        return pedidoTienda != null && pedidoTienda.getEstado() != null
                && ESTADOS_PEDIDO_TERMINADO.stream().anyMatch(e -> e.equalsIgnoreCase(pedidoTienda.getEstado()));
    }

    /**
     * ROJO: SOLO para instalaciones. Ya pasó la hora de terminar y la instalación
     * no quedó instalada ni se canceló. Los demás tipos nunca se ponen en rojo.
     */
    @Transient
    public boolean isVencida() {
        if (!isInstalacion()) return false;
        if (isTerminada() || CANCELADA.equals(estado) || fechaProgramada == null) return false;
        return getFechaFin().isBefore(LocalDateTime.now(ZONA_COLOMBIA));
    }

    @Transient
    public boolean isInstalacion() {
        return INSTALACION.equals(tipo);
    }

    @Transient
    public boolean isAsignadaPorAdmin() {
        return ORIGEN_ADMIN.equals(origen);
    }

    public boolean tieneInstalador(String username) {
        return username != null && instaladores.stream()
                .anyMatch(u -> u.getUsername().equalsIgnoreCase(username));
    }

    @Transient
    public Usuario getInstaladorPrincipal() {
        return instaladores.isEmpty() ? null : instaladores.get(0);
    }

    @Transient
    public Usuario getSegundoInstalador() {
        return instaladores.size() > 1 ? instaladores.get(1) : null;
    }

    // ─── Presentación ───────────────────────────────────────────────────

    /** Hora al estilo colombiano: "3:30 p. m." */
    private static final DateTimeFormatter FMT_HORA  = DateTimeFormatter.ofPattern("h:mm a", new Locale("es", "CO"));
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", new Locale("es", "CO"));
    private static final DateTimeFormatter FMT_LARGA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "CO"));

    public static String nombreVisible(Usuario u) {
        if (u == null) return "";
        String n = u.getNombreCompleto();
        return (n != null && !n.isBlank()) ? n.trim() : u.getUsername();
    }

    public static String etiquetaTipo(String tipo) {
        if (tipo == null) return "";
        switch (tipo) {
            case INSTALACION: return "Instalación";
            case LIMPIEZA:    return "Limpieza";
            case ARREGLO:     return "Arreglo";
            case COTIZACION:  return "Cotización";
            case OTRO:        return "Otro";
            default:          return tipo;
        }
    }

    public static String colorTipo(String tipo) {
        if (tipo == null) return "#1976d2";
        switch (tipo) {
            case LIMPIEZA:   return "#0c8599";
            case ARREGLO:    return "#e8590c";
            case COTIZACION: return "#7048e8";
            case OTRO:       return "#a61e4d";
            default:         return "#1976d2";
        }
    }

    public static String etiquetaEstado(String estado) {
        if (estado == null) return "";
        switch (estado) {
            case PROGRAMADA: return "Programada";
            case EN_CURSO:   return "En curso";
            case COMPLETADA: return "Terminada";
            case CANCELADA:  return "Cancelada";
            default:         return estado;
        }
    }

    @Transient
    public String getTipoEtiqueta() {
        return etiquetaTipo(tipo);
    }

    @Transient
    public String getEstadoEtiqueta() {
        if (isTerminada()) return isInstalacion() ? "Instalada" : "Completada";
        if (CANCELADA.equals(estado)) return "Cancelada";
        if (isVencida()) return "Vencida · sin terminar";
        return etiquetaEstado(estado);
    }

    @Transient
    public String getOrigenEtiqueta() {
        return isAsignadaPorAdmin() ? "Asignada por el jefe" : "Personal del instalador";
    }

    /**
     * Solo las INSTALACIONES cambian de color: verde = instalada, rojo = vencida.
     * Limpiezas, arreglos, cotizaciones y otros conservan siempre el color de su tipo
     * (si se completan, el calendario les pone un ✓ y las muestra más claras).
     * Cualquier tarea cancelada sale en gris.
     */
    @Transient
    public String getColor() {
        if (CANCELADA.equals(estado)) return COLOR_CANCELADA;
        if (isInstalacion()) {
            if (isTerminada()) return COLOR_TERMINADA;
            if (isVencida()) return COLOR_VENCIDA;
        }
        return colorTipo(tipo);
    }

    @Transient
    public String getTituloMostrado() {
        if (OTRO.equals(tipo)) {
            return (titulo != null && !titulo.isBlank()) ? titulo.trim() : "Otro";
        }
        if (cliente != null && !cliente.isBlank()) return cliente.trim();
        if (titulo != null && !titulo.isBlank()) return titulo.trim();
        return getTipoEtiqueta();
    }

    /** Ej: "Juan Pérez y Carlos Ruiz". */
    @Transient
    public String getNombresInstaladores() {
        return instaladores.stream().map(TareaCalendario::nombreVisible).collect(Collectors.joining(" y "));
    }

    @Transient
    public String getHoraInicio() {
        return fechaProgramada != null ? fechaProgramada.format(FMT_HORA) : "";
    }

    @Transient
    public String getHoraFin() {
        return fechaProgramada != null ? getFechaFin().format(FMT_HORA) : "";
    }

    @Transient
    public String getHoraRango() {
        if (fechaProgramada == null) return "";
        return fechaProgramada.format(FMT_HORA) + " – " + getFechaFin().format(FMT_HORA);
    }

    @Transient
    public String getFechaFormateada() {
        return fechaProgramada != null ? fechaProgramada.format(FMT_FECHA) : "";
    }

    @Transient
    public String getFechaCompletadaFormateada() {
        return fechaCompletada != null ? fechaCompletada.format(FMT_FECHA) : "";
    }

    /** Ej: "Lunes 5 de octubre". */
    @Transient
    public String getFechaLarga() {
        if (fechaProgramada == null) return "";
        String t = fechaProgramada.format(FMT_LARGA);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}