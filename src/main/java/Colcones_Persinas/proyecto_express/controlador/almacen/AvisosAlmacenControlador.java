package Colcones_Persinas.proyecto_express.controlador.almacen;

import Colcones_Persinas.proyecto_express.modelo.almacen.PedidoTienda;
import Colcones_Persinas.proyecto_express.repository.almacen.PedidoTiendaNuevosRepository;
import Colcones_Persinas.proyecto_express.servicio.tienda.TiendaServicio;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aviso de pedidos nuevos de la tienda virtual para Almacén.
 *
 * Un pedido cuenta como "nuevo" mientras lo haya creado la tienda virtual (vendedor
 * "Tienda virtual") y siga en estado "Pendiente". Apenas en Almacén le cambian el
 * estado (por ejemplo a "Pedido"), deja de salir en el aviso. No guarda nada aparte.
 *
 * La dirección queda bajo /almacen/**, así que solo la pueden consultar los mismos
 * usuarios que entran a Almacén. La consulta el archivo /js/almacen/avisos_tienda.js
 * desde el listado de Almacén y desde el portal.
 */
@Controller
@RequestMapping("/almacen/api")
public class AvisosAlmacenControlador {

    private static final String ESTADO_SIN_ATENDER = "Pendiente";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PedidoTiendaNuevosRepository nuevosRepository;

    public AvisosAlmacenControlador(PedidoTiendaNuevosRepository nuevosRepository) {
        this.nuevosRepository = nuevosRepository;
    }

    @GetMapping("/pedidos-tienda-nuevos")
    @ResponseBody
    public Map<String, Object> pedidosTiendaNuevos() {
        List<Map<String, Object>> pedidos = new ArrayList<>();
        for (PedidoTienda p : nuevosRepository.findTop20ByVendedorAndEstadoOrderByFechaPedidoDesc(
                TiendaServicio.VENDEDOR_TIENDA, ESTADO_SIN_ATENDER)) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", p.getId());
            fila.put("cliente", p.getNombreCliente());
            fila.put("cedula", p.getCedula());
            fila.put("fecha", p.getFechaPedido() != null ? p.getFechaPedido().format(FECHA) : "");
            fila.put("total", p.getTotal());
            pedidos.add(fila);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cantidad", nuevosRepository.countByVendedorAndEstado(TiendaServicio.VENDEDOR_TIENDA, ESTADO_SIN_ATENDER));
        r.put("pedidos", pedidos);
        return r;
    }
}