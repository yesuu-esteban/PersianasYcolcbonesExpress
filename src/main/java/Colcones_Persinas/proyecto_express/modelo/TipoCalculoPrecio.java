package Colcones_Persinas.proyecto_express.modelo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter


public enum TipoCalculoPrecio {
    POR_AREA("Ancho x Alto (m²) "),
    POR_ANCHO("Ancho(m) "),
    POR_LARGO("Largo / metros de cuerda"),
    POR_CANTIDAD("Cantidad de piezas usadas "),
    FIJO("Precio fijo (no se multiplica)");

    private final String etiqueta;

    TipoCalculoPrecio(String etiqueta){
        this.etiqueta = etiqueta;

    }



    
}
