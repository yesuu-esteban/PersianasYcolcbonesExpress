package Colcones_Persinas.proyecto_express.repository.fabrica;

import org.springframework.data.jpa.repository.JpaRepository;

import Colcones_Persinas.proyecto_express.modelo.fabrica.Insumo;

import java.util.List;
import java.util.Optional;

public interface InsumoRepository extends JpaRepository<Insumo, Integer> {

    Optional<Insumo> findByNombreIgnoreCase(String nombre);

    List<Insumo> findAllByOrderByNombreAsc();
}