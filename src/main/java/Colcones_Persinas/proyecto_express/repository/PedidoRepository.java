package Colcones_Persinas.proyecto_express.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import Colcones_Persinas.proyecto_express.modelo.Pedido;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Integer> {
    // Spring Data JPA implementa automáticamente todos los métodos (findAll, findById, save)
}