package Colcones_Persinas.proyecto_express.controlador.inicio;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalController {

    @GetMapping({"/", "/portal"})
    public String mostrarPortal() {
        // Busca el archivo en src/main/resources/templates/inicio/portal.html
        return "inicio/portal";
    }
}