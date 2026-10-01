package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.FormularioTarea;
import Colcones_Persinas.proyecto_express.modelo.PedidoTienda;
import Colcones_Persinas.proyecto_express.modelo.TareaCalendario;
import Colcones_Persinas.proyecto_express.modelo.Usuario;
import Colcones_Persinas.proyecto_express.repository.PedidoTiendaRepository;
import Colcones_Persinas.proyecto_express.repository.TareaCalendarioRepository;
import Colcones_Persinas.proyecto_express.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reglas del módulo de Instalaciones y de la agenda de los instaladores.
 *
 * PERMISOS
 *  - Jefe (TIENDA_ADMIN / ADMIN): crea, edita y elimina cualquier tarea y asigna
 *    los pedidos "En Bodega" a 1 o 2 instaladores  → métodos "...ComoAdmin".
 *  - Instalador: ve las tareas donde participa. Puede agregar tareas propias
 *    (instalación, limpieza, arreglo, cotización, otro) y editar/eliminar SOLO las
 *    que él mismo creó. Las que puso el jefe solo las puede empezar y terminar.
 *
 * REGLAS
 *  - Máximo 2 instaladores por tarea.
 *  - Un instalador no puede tener dos tareas activas cruzadas en horario.
 *  - Un pedido "En Bodega" solo puede tener una instalación activa a la vez.
 *  - Al terminar una instalación ligada a un pedido, el pedido pasa a "Instalado"
 *    (y vuelve a "En Bodega" si el jefe la des-termina o la elimina).
 */
@Service
public class CalendarioServicio {

    public static final String ROL_INSTALADOR   = "INSTALADOR";
    public static final String PEDIDO_EN_BODEGA = "En Bodega";
    public static final String PEDIDO_INSTALADO = "Instalado";

    private static final DateTimeFormatter FMT_ISO  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("h:mm a", new Locale("es", "CO"));

    private final TareaCalendarioRepository tareaRepository;
    private final PedidoTiendaRepository pedidoTiendaRepository;
    private final UsuarioRepository usuarioRepository;

    public CalendarioServicio(TareaCalendarioRepository tareaRepository,
                              PedidoTiendaRepository pedidoTiendaRepository,
                              UsuarioRepository usuarioRepository) {
        this.tareaRepository = tareaRepository;
        this.pedidoTiendaRepository = pedidoTiendaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSULTAS
    // ═══════════════════════════════════════════════════════════════

    public List<Usuario> listarInstaladoresActivos() {
        return usuarioRepository.findAllByOrderByUsernameAsc().stream()
                .filter(u -> ROL_INSTALADOR.equals(u.getRol()) && u.isActivo())
                .collect(Collectors.toList());
    }

    /**
     * "Instalaciones pendientes": pedidos de almacén en estado "En Bodega" que
     * todavía no tienen una instalación activa. Los de entrega más próxima, primero.
     */
    public List<PedidoTienda> instalacionesPendientes() {
        Set<Integer> yaAsignados = tareaRepository
                .findByTipoAndEstadoInAndPedidoTiendaIsNotNull(TareaCalendario.INSTALACION, TareaCalendario.ESTADOS_ACTIVOS)
                .stream().map(t -> t.getPedidoTienda().getId()).collect(Collectors.toSet());

        return pedidoTiendaRepository.findAllByOrderByFechaPedidoDescIdDesc().stream()
                .filter(p -> PEDIDO_EN_BODEGA.equalsIgnoreCase(p.getEstado()))
                .filter(p -> !yaAsignados.contains(p.getId()))
                .sorted(Comparator.comparing(PedidoTienda::getFechaEntrega,
                        Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())))
                .collect(Collectors.toList());
    }

    public Optional<TareaCalendario> buscar(int id) {
        return tareaRepository.findById(id);
    }

    /** Vista del jefe: un instalador concreto, o todos si instaladorId es null. */
    public List<TareaCalendario> listarParaAdmin(LocalDateTime desde, LocalDateTime hasta, Integer instaladorId) {
        if (instaladorId != null) {
            return tareaRepository.findDistinctByInstaladoresIdAndFechaProgramadaBetweenOrderByFechaProgramadaAsc(
                    instaladorId, desde, hasta);
        }
        return tareaRepository.findByFechaProgramadaBetweenOrderByFechaProgramadaAsc(desde, hasta);
    }

    /** Vista del instalador: tareas donde participa, sin canceladas. */
    public List<TareaCalendario> listarParaInstalador(String username, LocalDateTime desde, LocalDateTime hasta) {
        Usuario yo = usuarioRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (yo == null) return List.of();
        return tareaRepository.findDistinctByInstaladoresIdAndFechaProgramadaBetweenOrderByFechaProgramadaAsc(
                        yo.getId(), desde, hasta).stream()
                .filter(t -> !TareaCalendario.CANCELADA.equals(t.getEstado()))
                .collect(Collectors.toList());
    }

    public List<TareaCalendario> delDiaParaInstalador(String username, LocalDate dia) {
        return listarParaInstalador(username, dia.atStartOfDay(), dia.atTime(23, 59, 59));
    }

    public String nombreVisible(String username) {
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .map(TareaCalendario::nombreVisible).orElse(username);
    }

    // ═══════════════════════════════════════════════════════════════
    // JEFE (TIENDA_ADMIN / ADMIN)
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public TareaCalendario guardarComoAdmin(Integer id, FormularioTarea f, String usuarioActual) {
        final boolean esNueva = (id == null);
        final TareaCalendario t = esNueva ? new TareaCalendario() : tareaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Esa tarea ya no existe."));

        String estadoAnterior = esNueva ? null : t.getEstado();
        PedidoTienda pedidoAnterior = esNueva ? null : t.getPedidoTienda();

        String tipo = validarTipo(f.getTipo());
        String estado = (f.getEstado() != null && TareaCalendario.ESTADOS.contains(f.getEstado()))
                ? f.getEstado() : TareaCalendario.PROGRAMADA;
        LocalDateTime fecha = exigirFecha(f.getFechaProgramada());
        int duracion = validarDuracion(f.getDuracionMinutos());

        List<Usuario> instaladores = resolverInstaladores(f.getInstaladorId(), f.getInstalador2Id(),
                esNueva ? List.of() : t.getInstaladores());

        // ── Pedido (solo instalaciones) ──
        PedidoTienda pedido = null;
        if (TareaCalendario.INSTALACION.equals(tipo) && f.getPedidoTiendaId() != null) {
            pedido = pedidoTiendaRepository.findById(f.getPedidoTiendaId())
                    .orElseThrow(() -> new IllegalArgumentException("El pedido elegido no existe."));
            boolean cambioPedido = pedidoAnterior == null || pedidoAnterior.getId() != pedido.getId();
            if (cambioPedido && !PEDIDO_EN_BODEGA.equalsIgnoreCase(pedido.getEstado())) {
                throw new IllegalArgumentException("El pedido de " + pedido.getNombreCliente() + " está en \""
                        + pedido.getEstado() + "\". Solo se asignan pedidos que ya están En Bodega.");
            }
            if (TareaCalendario.ESTADOS_ACTIVOS.contains(estado)) {
                boolean yaAsignado = tareaRepository.findByPedidoTiendaIdAndTipoAndEstadoIn(
                                pedido.getId(), TareaCalendario.INSTALACION, TareaCalendario.ESTADOS_ACTIVOS).stream()
                        .anyMatch(otra -> esNueva || otra.getId() != t.getId());
                if (yaAsignado) {
                    throw new IllegalArgumentException("Ese pedido ya tiene una instalación asignada. "
                            + "Edítala en el calendario en vez de crear otra.");
                }
            }
        }

        // ── Contenido ──
        if (pedido != null) {
            t.setCliente(nvl(pedido.getNombreCliente()));
            t.setDireccion(nvl(pedido.getDireccion()));
            t.setTelefono(nvl(pedido.getTelefono()));
        } else {
            copiarContacto(t, tipo, f);
        }
        t.setTitulo(nvl(f.getTitulo()).trim());
        validarContenido(tipo, t);

        if (TareaCalendario.ESTADOS_ACTIVOS.contains(estado)) {
            for (Usuario u : instaladores) {
                validarCruce(u, fecha, duracion, esNueva ? null : t.getId(), false);
            }
        }

        t.setTipo(tipo);
        t.getInstaladores().clear();
        t.getInstaladores().addAll(instaladores);
        t.setPedidoTienda(pedido);
        t.setFechaProgramada(fecha);
        t.setDuracionMinutos(duracion);
        t.setNotas(nvl(f.getNotas()).trim());
        if (esNueva) {
            t.setOrigen(TareaCalendario.ORIGEN_ADMIN);
            t.setCreadoPor(nvl(usuarioActual));
        }
        aplicarCambioDeEstado(t, estadoAnterior, estado, pedidoAnterior, null);
        return tareaRepository.save(t);
    }

    @Transactional
    public void eliminarComoAdmin(int id) {
        TareaCalendario t = tareaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Esa tarea ya no existe."));
        aplicarCambioDeEstado(t, t.getEstado(), TareaCalendario.CANCELADA, t.getPedidoTienda(), null);
        tareaRepository.delete(t);
    }

    /** Valida 1 o 2 instaladores distintos, con rol INSTALADOR y activos. */
    private List<Usuario> resolverInstaladores(Integer id1, Integer id2, List<Usuario> anteriores) {
        if (id1 == null) throw new IllegalArgumentException("Elige al menos un instalador.");
        if (id2 != null && id2.equals(id1)) {
            throw new IllegalArgumentException("Elegiste el mismo instalador dos veces.");
        }
        List<Usuario> lista = new ArrayList<>();
        for (Integer idInst : (id2 != null ? List.of(id1, id2) : List.of(id1))) {
            Usuario u = usuarioRepository.findById(idInst)
                    .orElseThrow(() -> new IllegalArgumentException("Uno de los instaladores elegidos no existe."));
            if (!ROL_INSTALADOR.equals(u.getRol())) {
                throw new IllegalArgumentException(u.getUsername() + " no tiene el rol INSTALADOR.");
            }
            boolean yaEstaba = anteriores.stream().anyMatch(a -> a.getId() == u.getId());
            if (!u.isActivo() && !yaEstaba) {
                throw new IllegalArgumentException("La cuenta de " + u.getUsername() + " está desactivada.");
            }
            lista.add(u);
        }
        if (lista.size() > TareaCalendario.MAX_INSTALADORES) {
            throw new IllegalArgumentException("Máximo " + TareaCalendario.MAX_INSTALADORES + " instaladores por tarea.");
        }
        return lista;
    }

    // ═══════════════════════════════════════════════════════════════
    // INSTALADOR
    // ═══════════════════════════════════════════════════════════════

    /** Cualquier tarea donde participa (para verla). */
    public TareaCalendario obtenerParaInstalador(int id, String username) {
        TareaCalendario t = tareaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Esa tarea ya no existe."));
        if (!t.tieneInstalador(username)) {
            throw new IllegalArgumentException("Esa tarea no está en tu agenda.");
        }
        if (TareaCalendario.CANCELADA.equals(t.getEstado())) {
            throw new IllegalArgumentException("Esa tarea fue cancelada por el jefe.");
        }
        return t;
    }

    /** ¿Puede este instalador editar/eliminar la tarea? Solo si él mismo la creó. */
    public boolean puedeModificar(TareaCalendario t, String username) {
        return !t.isAsignadaPorAdmin() && t.getCreadoPor() != null && t.getCreadoPor().equalsIgnoreCase(username);
    }

    public TareaCalendario obtenerPropiaModificable(int id, String username) {
        TareaCalendario t = obtenerParaInstalador(id, username);
        if (t.isAsignadaPorAdmin()) {
            throw new IllegalArgumentException("Esta tarea la puso el jefe: solo él la puede editar o eliminar. "
                    + "Si hay algún problema, avísale.");
        }
        if (!puedeModificar(t, username)) {
            throw new IllegalArgumentException("Esta tarea la agregó " + t.getCreadoPor()
                    + "; solo esa persona o el jefe la pueden editar o eliminar.");
        }
        return t;
    }

    @Transactional
    public TareaCalendario guardarComoInstalador(Integer id, FormularioTarea f, String username) {
        Usuario yo = usuarioRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró tu usuario."));
        if (!ROL_INSTALADOR.equals(yo.getRol())) {
            throw new IllegalArgumentException("Solo los instaladores pueden agregar tareas a su agenda.");
        }

        final boolean esNueva = (id == null);
        final TareaCalendario t = esNueva ? new TareaCalendario() : obtenerPropiaModificable(id, username);
        if (!esNueva && TareaCalendario.COMPLETADA.equals(t.getEstado())) {
            throw new IllegalArgumentException("Esta tarea ya está terminada; no se puede modificar.");
        }

        String tipo = validarTipo(f.getTipo());
        LocalDateTime fecha = exigirFecha(f.getFechaProgramada());
        int duracion = validarDuracion(f.getDuracionMinutos());

        copiarContacto(t, tipo, f);
        t.setTitulo(nvl(f.getTitulo()).trim());
        validarContenido(tipo, t);

        validarCruce(yo, fecha, duracion, esNueva ? null : t.getId(), true);

        t.setTipo(tipo);
        t.setFechaProgramada(fecha);
        t.setDuracionMinutos(duracion);
        t.setNotas(nvl(f.getNotas()).trim());
        if (esNueva) {
            t.getInstaladores().add(yo);
            t.setPedidoTienda(null);
            t.setOrigen(TareaCalendario.ORIGEN_INSTALADOR);
            t.setEstado(TareaCalendario.PROGRAMADA);
            t.setCreadoPor(yo.getUsername());
        }
        return tareaRepository.save(t);
    }

    @Transactional
    public void eliminarComoInstalador(int id, String username) {
        TareaCalendario t = obtenerPropiaModificable(id, username);
        tareaRepository.delete(t);
    }

    @Transactional
    public void iniciar(int id, String username) {
        TareaCalendario t = obtenerParaInstalador(id, username);
        if (!TareaCalendario.PROGRAMADA.equals(t.getEstado())) {
            throw new IllegalArgumentException("Solo se puede empezar una tarea que está Programada.");
        }
        aplicarCambioDeEstado(t, t.getEstado(), TareaCalendario.EN_CURSO, t.getPedidoTienda(), username);
        tareaRepository.save(t);
    }

    @Transactional
    public void completar(int id, String username, String observaciones) {
        TareaCalendario t = obtenerParaInstalador(id, username);
        if (!t.isActiva()) {
            throw new IllegalArgumentException("Esta tarea ya estaba marcada como "
                    + t.getEstadoEtiqueta().toLowerCase() + ".");
        }
        t.setObservacionesInstalador(nvl(observaciones).trim());
        aplicarCambioDeEstado(t, t.getEstado(), TareaCalendario.COMPLETADA, t.getPedidoTienda(), username);
        tareaRepository.save(t);
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDACIONES Y ESTADOS
    // ═══════════════════════════════════════════════════════════════

    private String validarTipo(String tipo) {
        if (tipo == null || !TareaCalendario.TIPOS.contains(tipo)) {
            throw new IllegalArgumentException("Elige el tipo de tarea.");
        }
        return tipo;
    }

    private LocalDateTime exigirFecha(String texto) {
        LocalDateTime fecha = parsearFechaFormulario(texto);
        if (fecha == null) throw new IllegalArgumentException("Indica la fecha y la hora.");
        return fecha;
    }

    private int validarDuracion(Integer minutos) {
        int m = (minutos != null) ? minutos : 60;
        if (m < 15 || m > 720) throw new IllegalArgumentException("La duración debe estar entre 15 minutos y 12 horas.");
        return m;
    }

    /** "Otro" no lleva cliente ni teléfono; el resto sí. */
    private void copiarContacto(TareaCalendario t, String tipo, FormularioTarea f) {
        boolean esOtro = TareaCalendario.OTRO.equals(tipo);
        t.setCliente(esOtro ? "" : nvl(f.getCliente()).trim());
        t.setTelefono(esOtro ? "" : nvl(f.getTelefono()).trim());
        t.setDireccion(nvl(f.getDireccion()).trim());
    }

    private void validarContenido(String tipo, TareaCalendario t) {
        if (TareaCalendario.OTRO.equals(tipo)) {
            if (t.getTitulo() == null || t.getTitulo().isBlank()) {
                throw new IllegalArgumentException("Escribe de qué se trata (ej: \"Recoger material en la fábrica\").");
            }
        } else if (t.getCliente() == null || t.getCliente().isBlank()) {
            throw new IllegalArgumentException("Escribe el nombre del cliente"
                    + (TareaCalendario.INSTALACION.equals(tipo) ? " o elige un pedido en bodega." : "."));
        }
    }

    private void validarCruce(Usuario instalador, LocalDateTime inicio, int duracion,
                              Integer excluirId, boolean hablaElInstalador) {
        LocalDateTime fin = inicio.plusMinutes(duracion);
        List<TareaCalendario> cercanas = tareaRepository.findDistinctByInstaladoresIdAndEstadoInAndFechaProgramadaBetween(
                instalador.getId(), TareaCalendario.ESTADOS_ACTIVOS, inicio.minusHours(13), fin);

        for (TareaCalendario otra : cercanas) {
            if (excluirId != null && otra.getId() == excluirId) continue;
            if (otra.getFechaProgramada().isBefore(fin) && otra.getFechaFin().isAfter(inicio)) {
                String quien = hablaElInstalador ? "Ya tienes" : TareaCalendario.nombreVisible(instalador) + " ya tiene";
                throw new IllegalArgumentException(quien + " " + otra.getTipoEtiqueta().toLowerCase()
                        + " \"" + otra.getTituloMostrado() + "\" de " + otra.getHoraRango()
                        + " ese día, y se cruza con este horario.");
            }
        }
    }

    /**
     * Mantiene sincronizado el pedido de almacén: terminar una instalación ligada → "Instalado";
     * des-terminarla, cambiarle el pedido o eliminarla → el pedido vuelve a "En Bodega".
     */
    private void aplicarCambioDeEstado(TareaCalendario t, String anterior, String nuevo,
                                       PedidoTienda pedidoAnterior, String quien) {
        LocalDateTime ahora = LocalDateTime.now(TareaCalendario.ZONA_COLOMBIA);
        PedidoTienda pedidoNuevo = t.getPedidoTienda();
        boolean mismoPedido = pedidoAnterior != null && pedidoNuevo != null
                && pedidoAnterior.getId() == pedidoNuevo.getId();
        boolean eraCompletada = TareaCalendario.COMPLETADA.equals(anterior);
        boolean quedaCompletada = TareaCalendario.COMPLETADA.equals(nuevo);

        t.setEstado(nuevo);
        if (TareaCalendario.EN_CURSO.equals(nuevo) && t.getFechaInicioReal() == null) {
            t.setFechaInicioReal(ahora);
        }

        if (eraCompletada && pedidoAnterior != null && (!quedaCompletada || !mismoPedido)
                && PEDIDO_INSTALADO.equalsIgnoreCase(pedidoAnterior.getEstado())) {
            pedidoAnterior.setEstado(PEDIDO_EN_BODEGA);
            pedidoTiendaRepository.save(pedidoAnterior);
        }

        if (quedaCompletada) {
            if (!eraCompletada) {
                t.setFechaCompletada(ahora);
                t.setCompletadaPor(quien != null ? quien : "");
            }
            if (pedidoNuevo != null && TareaCalendario.INSTALACION.equals(t.getTipo())
                    && (!eraCompletada || !mismoPedido)) {
                pedidoNuevo.setEstado(PEDIDO_INSTALADO);
                pedidoTiendaRepository.save(pedidoNuevo);
            }
        } else {
            t.setFechaCompletada(null);
            t.setCompletadaPor("");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FORMATO PARA FULLCALENDAR
    // ═══════════════════════════════════════════════════════════════

    public Map<String, Object> aEvento(TareaCalendario t, boolean prefijarInstaladores, String urlDetalle) {
        String titulo = (prefijarInstaladores ? t.getNombresInstaladores() + ": " : "")
                + t.getTipoEtiqueta() + " · " + t.getTituloMostrado();

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("urlDetalle", urlDetalle);
        extra.put("tipo", t.getTipoEtiqueta());
        extra.put("estado", t.getEstadoEtiqueta());
        extra.put("origen", t.getOrigenEtiqueta());
        extra.put("instaladores", t.getNombresInstaladores());
        extra.put("direccion", t.getDireccion());
        extra.put("horario", t.getFechaProgramada().format(FMT_HORA) + " – " + t.getFechaFin().format(FMT_HORA));

        List<String> clases = new ArrayList<>();
        if (TareaCalendario.COMPLETADA.equals(t.getEstado())) clases.add("evento-terminado");
        if (TareaCalendario.CANCELADA.equals(t.getEstado()))  clases.add("evento-cancelado");
        if (TareaCalendario.EN_CURSO.equals(t.getEstado()))   clases.add("evento-en-curso");

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("id", t.getId());
        ev.put("title", titulo);
        ev.put("start", t.getFechaProgramada().format(FMT_ISO));
        ev.put("end", t.getFechaFin().format(FMT_ISO));
        ev.put("backgroundColor", t.getColor());
        ev.put("borderColor", t.getColor());
        ev.put("textColor", "#ffffff");
        ev.put("classNames", clases);
        ev.put("extendedProps", extra);
        return ev;
    }

    // ═══════════════════════════════════════════════════════════════
    // FECHAS
    // ═══════════════════════════════════════════════════════════════

    /** FullCalendar manda "2026-09-28T00:00:00-05:00"; se usa la parte local. */
    public static LocalDateTime parsearFechaCalendario(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String s = texto.trim();
        try {
            if (s.length() >= 19) return LocalDateTime.parse(s.substring(0, 19));
            if (s.length() >= 16) return LocalDateTime.parse(s.substring(0, 16));
            return LocalDate.parse(s.substring(0, 10)).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    /** Acepta "2026-10-05" (se asume 8:00 a.m.) o "2026-10-05T09:30". */
    public static LocalDateTime parsearFechaFormulario(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String s = texto.trim();
        try {
            if (s.length() <= 10) return LocalDate.parse(s).atTime(8, 0);
            return LocalDateTime.parse(s.substring(0, 16));
        } catch (Exception e) {
            return null;
        }
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}