package Colcones_Persinas.proyecto_express.repository.almacen;

import org.springframework.data.jpa.repository.JpaRepository;

import Colcones_Persinas.proyecto_express.modelo.almacen.DetallePedidoTienda;

public interface DetallePedidoTiendaRepository extends JpaRepository<DetallePedidoTienda, Integer> {
}