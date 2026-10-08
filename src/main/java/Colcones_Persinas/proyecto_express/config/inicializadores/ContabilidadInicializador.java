package Colcones_Persinas.proyecto_express.config.inicializadores;

import Colcones_Persinas.proyecto_express.modelo.contabilidad.CategoriaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.CuentaContable;
import Colcones_Persinas.proyecto_express.modelo.contabilidad.SubcategoriaContable;
import Colcones_Persinas.proyecto_express.repository.contabilidad.CategoriaContableRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.CuentaContableRepository;
import Colcones_Persinas.proyecto_express.repository.contabilidad.SubcategoriaContableRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga el plan de cuentas y las cuentas de la empresa la PRIMERA vez que arranca la app.
 * Si ya hay categorías (o cuentas), no hace nada: lo que se cambie después desde
 * Contabilidad → Categorías / Cuentas se respeta.
 */
@Component
public class ContabilidadInicializador implements CommandLineRunner {

    private final CategoriaContableRepository categoriaRepository;
    private final SubcategoriaContableRepository subcategoriaRepository;
    private final CuentaContableRepository cuentaRepository;

    public ContabilidadInicializador(CategoriaContableRepository categoriaRepository,
                                     SubcategoriaContableRepository subcategoriaRepository,
                                     CuentaContableRepository cuentaRepository) {
        this.categoriaRepository = categoriaRepository;
        this.subcategoriaRepository = subcategoriaRepository;
        this.cuentaRepository = cuentaRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categoriaRepository.count() == 0) cargarPlanDeCuentas();
        if (cuentaRepository.count() == 0) cargarCuentas();
    }

    private void cargarPlanDeCuentas() {
        Map<String, Object[]> plan = new LinkedHashMap<>();
        plan.put("INGRESOS OPERACIONALES", new Object[]{CategoriaContable.INGRESO, List.of(
                "VENTA DE PRODUCTOS", "VENTA DE SERVICIOS", "OTROS INGRESOS")});
        plan.put("COSTOS DE VENTA", new Object[]{CategoriaContable.COSTO, List.of(
                "COMPRA DE MERCANCÍA", "COMPRA DE INSUMOS", "MATERIALES", "INSTALACIONES")});
        plan.put("GASTOS ADMINISTRATIVOS", new Object[]{CategoriaContable.GASTO, List.of(
                "ARRIENDO", "ENERGÍA", "AGUA", "GAS", "INTERNET", "CELULARES", "PAPELERÍA", "ÚTILES DE OFICINA",
                "SOFTWARE", "HONORARIOS", "ASEO", "CAFETERÍA", "PUBLICIDAD", "MARKETING", "REDES", "TRANSPORTE",
                "SERVICIO DE ALARMAS", "OTROS GASTOS")});
        plan.put("GASTOS LABORALES - NÓMINA", new Object[]{CategoriaContable.GASTO, List.of(
                "CARLOS", "FABIAN", "YULI", "ISABEL", "JENNY", "JENNY CONTADORA", "AUXILIO DE TRANSPORTE",
                "SEGURIDAD SOCIAL", "PRESTACIONES SOCIALES", "DOTACIÓN", "ANTICIPO DE NÓMINA", "PAGO DE COMISIÓN POR VENTA")});
        plan.put("GASTOS DE VEHÍCULOS", new Object[]{CategoriaContable.GASTO, List.of(
                "COMBUSTIBLE", "PEAJES", "PARQUEADEROS", "MANTENIMIENTO", "SEGURO", "SOAT")});
        plan.put("GASTOS BANCARIOS", new Object[]{CategoriaContable.GASTO, List.of(
                "4 X 1.000", "COMISIONES", "CUOTA DE MANEJO")});
        plan.put("GASTOS FINANCIEROS", new Object[]{CategoriaContable.GASTO, List.of(
                "INTERESES PRÉSTAMO", "INTERESES BANCARIOS")});
        plan.put("IMPUESTOS", new Object[]{CategoriaContable.GASTO, List.of(
                "ICA", "IVA", "RETENCIÓN EN LA FUENTE", "IMPUESTO DE RENTA")});
        plan.put("OBLIGACIONES FINANCIERAS", new Object[]{CategoriaContable.OTRO, List.of(
                "PAGO PRÉSTAMO VEHÍCULO", "PAGO CRÉDITO BANCARIO")});
        plan.put("ACTIVOS", new Object[]{CategoriaContable.OTRO, List.of(
                "COMPRA COMPUTADOR", "COMPRA IMPRESORA", "COMPRA VEHÍCULO", "COMPRA MUEBLES Y EQUIPOS")});
        plan.put("RETIRO", new Object[]{CategoriaContable.OTRO, List.of(
                "RETIRO EFECTIVO", "TRANSFERENCIA AL PROPIETARIO")});

        int ordenCategoria = 1;
        for (Map.Entry<String, Object[]> e : plan.entrySet()) {
            CategoriaContable c = categoriaRepository.save(
                    new CategoriaContable(e.getKey(), (String) e.getValue()[0], ordenCategoria++));
            @SuppressWarnings("unchecked")
            List<String> subcategorias = (List<String>) e.getValue()[1];
            int orden = 1;
            for (String nombre : subcategorias) subcategoriaRepository.save(new SubcategoriaContable(c, nombre, orden++));
        }
        System.out.println("[Contabilidad] Plan de cuentas cargado.");
    }

    private void cargarCuentas() {
        String[] nombres = {"BANCOLOMBIA", "NEQUI", "DAVIPLATA", "DAVIVIENDA", "ISABELLA", "BOLD", "BBVA", "CAJA", "CAJA YULI"};
        for (int i = 0; i < nombres.length; i++) cuentaRepository.save(new CuentaContable(nombres[i], i + 1));
        System.out.println("[Contabilidad] Cuentas cargadas (con saldo inicial en 0).");
    }
}