package Colcones_Persinas.proyecto_express.modelo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;

/**
 * Datos que llegan de los formularios de tareas (jefe e instalador).
 * La fecha viaja como texto ("2026-10-05T09:30") y la convierte el servicio.
 * Es Serializable porque, si hay un error, se devuelve al formulario como
 * flash attribute para no perder lo que el usuario escribió.
 */
@Data
@NoArgsConstructor
public class FormularioTarea implements Serializable {

    private static final DateTimeFormatter FMT_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private String tipo = TareaCalendario.INSTALACION;
    /** Instalador principal (obligatorio cuando la pone el jefe). */
    private Integer instaladorId;
    /** Segundo instalador (opcional). Máximo 2 por tarea. */
    private Integer instalador2Id;
    private Integer pedidoTiendaId;
    private String titulo = "";
    private String cliente = "";
    private String direccion = "";
    private String telefono = "";
    private String fechaProgramada = "";
    private Integer duracionMinutos = 120;
    private String notas = "";
    private String estado = TareaCalendario.PROGRAMADA;

    public static FormularioTarea desde(TareaCalendario t) {
        FormularioTarea f = new FormularioTarea();
        f.setTipo(t.getTipo());
        f.setInstaladorId(t.getInstaladorPrincipal() != null ? t.getInstaladorPrincipal().getId() : null);
        f.setInstalador2Id(t.getSegundoInstalador() != null ? t.getSegundoInstalador().getId() : null);
        f.setPedidoTiendaId(t.getPedidoTienda() != null ? t.getPedidoTienda().getId() : null);
        f.setTitulo(t.getTitulo());
        f.setCliente(t.getCliente());
        f.setDireccion(t.getDireccion());
        f.setTelefono(t.getTelefono());
        f.setFechaProgramada(t.getFechaProgramada() != null ? t.getFechaProgramada().format(FMT_INPUT) : "");
        f.setDuracionMinutos(t.getDuracionMinutos());
        f.setNotas(t.getNotas());
        f.setEstado(t.getEstado());
        return f;
    }
}