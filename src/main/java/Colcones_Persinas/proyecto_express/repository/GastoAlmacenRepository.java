package Colcones_Persinas.proyecto_express.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Colcones_Persinas.proyecto_express.modelo.GatoAlmacen;

@Repository
public interface GastoAlmacenRepository extends JpaRepository<GatoAlmacen, Integer> {

    List<GatoAlmacen> findAllByOrderByFechaDesc();

    List<GatoAlmacen> findByFechaBetweenOrderByFechaDesc(LocalDate fechaInicio, LocalDate fechaFin);
    
}