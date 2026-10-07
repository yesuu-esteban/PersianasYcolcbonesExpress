package Colcones_Persinas.proyecto_express.modelo.tienda;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Foto de un producto de la tienda, guardada en la base de datos (columna bytea).
 * Se guarda en la base y no en una carpeta porque en Railway los archivos subidos
 * a disco se pierden en cada despliegue. Se sirve en /tienda/img/{id}.
 */
@Entity
@Table(name = "tienda_imagen")
@Getter @Setter @NoArgsConstructor
public class ImagenTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "content_type", nullable = false)
    private String contentType = "image/jpeg";

    @Column(nullable = false)
    private byte[] datos;
}