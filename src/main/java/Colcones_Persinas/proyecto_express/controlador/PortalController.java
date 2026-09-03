package Colcones_Persinas.proyecto_express.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalController {

    @GetMapping({"/", "/vista"})
    public String mostrarVistaPrincipal() {
        // Busca el archivo en src/main/resources/templates/VistaPrincipal/Vista.html
        return "VistaPrincipal/Vista"; 
    }
}