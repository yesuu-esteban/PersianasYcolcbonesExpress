package Colcones_Persinas.proyecto_express.repository;

import Colcones_Persinas.proyecto_express.modelo.ReciboCaja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReciboCajaRepository extends JpaRepository<ReciboCaja, Integer> {
    List<ReciboCaja> findAllByOrderByIdDesc();
}