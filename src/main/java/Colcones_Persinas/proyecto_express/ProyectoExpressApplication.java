package Colcones_Persinas.proyecto_express;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  
public class ProyectoExpressApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProyectoExpressApplication.class, args);
	}

}