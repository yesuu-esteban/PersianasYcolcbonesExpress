package Colcones_Persinas.proyecto_express.modelo;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pedido")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pedido {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String nombreDecorador;
    private String nombreClienteFinal; 
    private String descripcion;
    private int cantidad;
    private double altura;
    private double ancho;
    private String medidaCuerda; 
    
    private String estado;
    private String tipoControl; 
    private String ladoControl;
    private String colorTelaDeseado;

    private String rolloParaCortar;
    private String tuboRecomendado;
    
    // Persistencia forzada en base de datos
    @Column(name = "usa_cabezal", nullable = false)
    private Boolean usaCabezal = false; 

    private Boolean telaCortada = false;
    private Boolean perfileriaCortada = false;
    private Boolean ensamblado = false;

    // Métodos de cálculo persistidos (Sin @Transient para que se guarden en BD)
    public void calcularFichaTecnica() {
        this.tipoControl = (this.ancho > 1.50) ? "Control A" : "Control B";
        this.tuboRecomendado = (this.ancho > 2.50 || this.altura > 2.50) ? "R24" : "R16";
        this.medidaCuerda = (this.altura <= 1.50) ? "3 metros" : "4 metros";
        double dimMayor = Math.max(this.ancho, this.altura);
        if (dimMayor <= 1.83) this.rolloParaCortar = "Rollo 1.83m";
        else if (dimMayor <= 2.50) this.rolloParaCortar = "Rollo 2.50m";
        else if (dimMayor <= 3.00) this.rolloParaCortar = "Rollo 3.00m";
        else this.rolloParaCortar = "Medida especial (Consultar)";
    }

    public void calcularEstadoGeneral() {
        if (Boolean.TRUE.equals(ensamblado)) this.estado = "Listo para Despacho";
        else if (Boolean.TRUE.equals(telaCortada) && Boolean.TRUE.equals(perfileriaCortada)) this.estado = "Listo para Ensamblar";
        else if (Boolean.TRUE.equals(telaCortada) || Boolean.TRUE.equals(perfileriaCortada)) this.estado = "En Proceso";
        else this.estado = "Pendiente";
    }

    // Cálculos dinámicos
    @Transient public double getCorteTelaAncho() { return Math.round((this.ancho - (Boolean.TRUE.equals(usaCabezal) ? 0.035 : 0.03)) * 1000.0) / 1000.0; }
    @Transient public double getCorteTelaAlto() { return Math.round((this.altura + 0.20) * 1000.0) / 1000.0; }
    @Transient public double getCorteTuberia() { return Math.round((this.ancho - (Boolean.TRUE.equals(usaCabezal) ? 0.03 : 0.025)) * 1000.0) / 1000.0; }
    @Transient public double getMedidaCabezal() { return Boolean.TRUE.equals(usaCabezal) ? Math.round((this.ancho - 0.005) * 1000.0) / 1000.0 : 0.0; }
    @Transient public String getTipoSistema() { return Boolean.TRUE.equals(this.usaCabezal) ? "COBERLIG (NORMAL)" : "BLACKOUT"; }
}