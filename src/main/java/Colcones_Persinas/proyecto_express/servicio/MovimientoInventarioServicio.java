package Colcones_Persinas.proyecto_express.servicio;

import Colcones_Persinas.proyecto_express.modelo.MovimientoInventario;
import Colcones_Persinas.proyecto_express.repository.MovimientoInventarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MovimientoInventarioServicio {

    private final MovimientoInventarioRepository repo;

    public MovimientoInventarioServicio(MovimientoInventarioRepository repo) {
        this.repo = repo;
    }

    public void registrarEntrada(String categoria, String descripcion) {
        registrar("ENTRADA", categoria, descripcion, nombreUsuarioActual());
    }

    public void registrarLimpiezaAutomatica(String categoria, String descripcion) {
        registrar("LIMPIEZA_AUTOMATICA", categoria, descripcion, "sistema (automático)");
    }

    private void registrar(String tipoMovimiento, String categoria, String descripcion, String creadoPor) {
        MovimientoInventario m = new MovimientoInventario();
        m.setTipoMovimiento(tipoMovimiento);
        m.setCategoria(categoria);
        m.setDescripcion(descripcion);
        m.setCreadoPor(creadoPor);
        repo.save(m);
    }

    /** Últimos N movimientos, más recientes primero. */
    public List<MovimientoInventario> getUltimosMovimientos(int cantidad) {
        return repo.findAllByOrderByFechaDesc(PageRequest.of(0, cantidad));
    }

    private String nombreUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "desconocido";
    }
}