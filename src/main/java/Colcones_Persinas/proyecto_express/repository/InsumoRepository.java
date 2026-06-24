package Colcones_Persinas.proyecto_express.repository;

import Colcones_Persinas.proyecto_express.modelo.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InsumoRepository extends JpaRepository<Insumo, Integer> {

    Optional<Insumo> findByNombreIgnoreCase(String nombre);

    List<Insumo> findAllByOrderByNombreAsc();
}