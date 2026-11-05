package Colcones_Persinas.proyecto_express.repository.fabrica;

import Colcones_Persinas.proyecto_express.modelo.fabrica.VentaDirectaLinea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface VentaDirectaLineaRepository extends JpaRepository<VentaDirectaLinea, Integer> {
    List<VentaDirectaLinea> findByPedidoIdOrderByOrdenAsc(int pedidoId);

    @Modifying
    @Transactional
    void deleteByPedidoId(int pedidoId);
}