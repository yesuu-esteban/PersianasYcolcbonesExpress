package Colcones_Persinas.proyecto_express.controlador;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * La raíz "/" redirige según el rol del usuario autenticado:
 * fábrica/admin van a /taller/pedidos, tienda va a /tienda/listado.
 */
@Controller
public class InicioControlador {

    @GetMapping("/")
    public String irAInicio() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean esFabricaOAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FABRICA")
                            || a.getAuthority().equals("ROLE_ADMIN"));

        return esFabricaOAdmin ? "redirect:/taller/pedidos" : "redirect:/tienda/listado";
    }
}