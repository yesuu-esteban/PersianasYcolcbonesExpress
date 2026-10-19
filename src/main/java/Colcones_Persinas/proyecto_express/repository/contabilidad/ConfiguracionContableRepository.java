package Colcones_Persinas.proyecto_express.repository.contabilidad;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.ConfiguracionContable;
import org.springframework.data.jpa.repository.JpaRepository;

/** La fila única de ajustes de la contabilidad (id = 1). */
public interface ConfiguracionContableRepository extends JpaRepository<ConfiguracionContable, Integer> {
}