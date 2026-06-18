package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pedido")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pedido {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String nombreCliente;
    private String descripcion;
    private int cantidad;
    private double altura;
    private double ancho;
    private String medidaCuerda; 
    
    private String estado;
    private String tipoControl; 
    private String colorTelaDeseado;

    private String rolloParaCortar;
    private String tuboRecomendado;

    private Boolean telaCortada = false;
    private Boolean perfileriaCortada = false;
    private Boolean ensamblado = false;

    // =========================================================================
    // LÓGICA DE NEGOCIO CON REDONDEO SEGURO
    // =========================================================================

    // 1. Corte de Tela: Ancho - 3cm, Largo + 20cm
    @Transient 
    public double getCorteTelaAncho() { 
        return Math.round((this.ancho - 0.03) * 1000.0) / 1000.0; 
    }
    
    @Transient 
    public double getCorteTelaAlto() { 
        return Math.round((this.altura + 0.20) * 1000.0) / 1000.0; 
    }

    // 2. Corte de Tubería: Ancho - 2.5cm (0.025m)
    @Transient 
    public double getCorteTuberia() { 
        return Math.round((this.ancho - 0.025) * 1000.0) / 1000.0; 
    }

    

    @Transient
    public void calcularFichaTecnica() {
        this.tipoControl = (this.ancho > 1.50) ? "Control A" : "Control B";
        this.tuboRecomendado = (this.ancho > 2.50 || this.altura > 2.50) ? "R24" : "R16";

        // Lógica nueva para la cuerda
        this.medidaCuerda = (this.altura <= 1.50) ? "3 metros" : "4 metros";

        double ladoMenor = Math.min(this.ancho, this.altura);
        
        if (ladoMenor <= 1.83) {
            this.rolloParaCortar = "Rollo 1.83m";
        } else if (ladoMenor <= 2.50) {
            this.rolloParaCortar = "Rollo 2.50m";
        } else if (ladoMenor <= 3.00) {
            this.rolloParaCortar = "Rollo 3.00m";
        } else {
            this.rolloParaCortar = "Medida especial (Consultar)";
        }
    }

    @Transient
    public void calcularEstadoGeneral() {
        if (Boolean.TRUE.equals(ensamblado)) {
            this.estado = "Listo para Despacho";
        } else if (Boolean.TRUE.equals(telaCortada) && Boolean.TRUE.equals(perfileriaCortada)) {
            this.estado = "Listo para Ensamblar";
        } else if (Boolean.TRUE.equals(telaCortada) || Boolean.TRUE.equals(perfileriaCortada)) {
            this.estado = "En Proceso";
        } else {
            this.estado = "Pendiente";
        }
    }
}