package Colcones_Persinas.proyecto_express.servicio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServicio {

    @Autowired
    private JavaMailSender mailSender;

    public void enviarCorreoRecuperacion(String destinatario, String enlaceRecuperacion) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(destinatario);
        mensaje.setSubject("Recuperación de contraseña — Persianas y Colchones Express");
        mensaje.setText(
            "Hola,\n\n" +
            "Recibimos una solicitud para restablecer tu contraseña.\n" +
            "Si fuiste tú, entra al siguiente enlace (válido por 30 minutos):\n\n" +
            enlaceRecuperacion + "\n\n" +
            "Si no fuiste tú, simplemente ignora este correo: tu contraseña actual seguirá funcionando.\n\n" +
            "— Persianas y Colchones Express"
        );
        mailSender.send(mensaje);
    }
}