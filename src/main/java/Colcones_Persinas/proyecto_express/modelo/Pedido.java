package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pedido")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pedido {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String nombreDecorador = "";
    private String nombreClienteFinal = ""; 
    private String descripcion = "";
    
    // Valores por defecto para evitar cálculos con nulos
    private int cantidad = 1;
    private double altura = 0.0;
    private double ancho = 0.0;
    
    private String medidaCuerda = "0 metros"; 
    private String estado = "Pendiente";
    private String tipoControl = ""; 
    private String ladoControl = "";
    private String colorTelaDeseado = "";

    private String rolloParaCortar = "";
    private String tuboRecomendado = "";
    
    @Column(name = "usa_cabezal", nullable = false)
    private Boolean usaCabezal = false; 

    @Column(nullable = false)
    private Boolean telaCortada = false;
    
    @Column(nullable = false)
    private Boolean perfileriaCortada = false;
    
    @Column(nullable = false)
    private Boolean ensamblado = false;

    // --- CÁLCULOS PROTEGIDOS ---
    @Transient
    public double getCorteTelaAncho() {
        return Math.round((this.ancho - (Boolean.TRUE.equals(usaCabezal) ? 0.035 : 0.03)) * 1000.0) / 1000.0;
    }

    @Transient
    public double getCorteTelaAlto() {
        return Math.round((this.altura + 0.20) * 1000.0) / 1000.0;
    }

    @Transient
    public double getCorteTuberia() {
        return Math.round((this.ancho - (Boolean.TRUE.equals(usaCabezal) ? 0.03 : 0.025)) * 1000.0) / 1000.0;
    }

    @Transient
    public String getTipoSistema() {
        return Boolean.TRUE.equals(this.usaCabezal) ? "COBERLIG (NORMAL)" : "BLACKOUT";
    }

    public void calcularFichaTecnica() {
        this.tipoControl = (this.ancho > 1.50) ? "Control A" : "Control B";
        this.tuboRecomendado = (this.ancho > 2.50 || this.altura > 2.50) ? "R24" : "R16";
        this.medidaCuerda = (this.altura <= 1.50) ? "3 metros" : "4 metros";
    }

    public void calcularEstadoGeneral() {
        if (Boolean.TRUE.equals(ensamblado)) this.estado = "Listo para Despacho";
        else if (Boolean.TRUE.equals(telaCortada) && Boolean.TRUE.equals(perfileriaCortada)) this.estado = "Listo para Ensamblar";
        else if (Boolean.TRUE.equals(telaCortada) || Boolean.TRUE.equals(perfileriaCortada)) this.estado = "En Proceso";
        else this.estado = "Pendiente";
    }
}