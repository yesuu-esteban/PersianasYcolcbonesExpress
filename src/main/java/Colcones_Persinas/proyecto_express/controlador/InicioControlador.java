package Colcones_Persinas.proyecto_express.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * La aplicación no tiene una página de inicio propia: la raíz "/"
 * simplemente redirige a la pantalla principal de pedidos, para que
 * entrar a http://localhost:8080 sin ninguna ruta no muestre un 404.
 */
@Controller
public class InicioControlador {

    @GetMapping("/")
    public String irAInicio() {
        return "redirect:/taller/pedidos";
    }
}