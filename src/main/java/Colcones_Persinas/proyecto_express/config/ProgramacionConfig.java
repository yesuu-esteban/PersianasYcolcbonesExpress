package Colcones_Persinas.proyecto_express.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas que corren solas cada cierto tiempo (por ejemplo, traer los pedidos de Shopify
 * cada 10 minutos). Si el programa ya tenía @EnableScheduling en otra parte, no pasa nada por tenerlo aquí también.
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}