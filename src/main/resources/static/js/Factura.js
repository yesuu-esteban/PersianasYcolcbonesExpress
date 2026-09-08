// ===================== CONFIGURACIÓN DEL CONSECUTIVO =====================
const CLAVE_CONSECUTIVO = 'ultimoNumeroRecibo';
const NUMERO_INICIAL = 431; // el primer recibo generado saldrá como el 432

// Número ya asignado al recibo que se está armando en pantalla.
// Se conserva mientras se edite/regenere el mismo recibo (no se quema uno nuevo
// cada vez que se da "Modificar Datos" y luego "Generar Factura Final" de nuevo).
let numeroAsignadoActual = null;

// Lista de productos añadidos a la factura actual
let productos = [];

// ===================== REFERENCIAS AL DOM =====================
const panelCaptura = document.getElementById('panelCaptura');
const panelFacturaFinal = document.getElementById('panelFacturaFinal');
const tablaProductos = document.getElementById('tablaProductos');
const inputAbono = document.getElementById('abono');

function formatearMoneda(valor) {
    return valor.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ===================== AÑADIR PRODUCTO =====================
function agregarProducto() {
    const nombre = document.getElementById('nombreProducto').value.trim();
    const precio = parseFloat(document.getElementById('precioProducto').value) || 0;
    const cantidad = parseFloat(document.getElementById('cantidadProducto').value) || 0;

    if (!nombre || precio <= 0 || cantidad <= 0) {
        alert('Completa producto, precio y cantidad antes de añadir.');
        return;
    }

    productos.push({ nombre, precio, cantidad });
    renderizarTablaProductos();
    recalcularTotales();

    // Limpiar los campos para el siguiente producto
    document.getElementById('nombreProducto').value = '';
    document.getElementById('precioProducto').value = '';
    document.getElementById('cantidadProducto').value = 1;
    document.getElementById('nombreProducto').focus();
}

function eliminarProducto(indice) {
    productos.splice(indice, 1);
    renderizarTablaProductos();
    recalcularTotales();
}

function renderizarTablaProductos() {
    tablaProductos.innerHTML = productos.map((p, indice) => {
        const totalFila = p.precio * p.cantidad;
        return `
            <tr>
                <td>${p.nombre}</td>
                <td>$${formatearMoneda(p.precio)}</td>
                <td>${p.cantidad}</td>
                <td>$${formatearMoneda(totalFila)}</td>
                <td><button type="button" class="btn-eliminar-producto" onclick="eliminarProducto(${indice})">Quitar</button></td>
            </tr>
        `;
    }).join('');
}

// ===================== TOTALES EN VIVO =====================
function recalcularTotales() {
    const totalGeneral = productos.reduce((acumulado, p) => acumulado + (p.precio * p.cantidad), 0);
    const abono = parseFloat(inputAbono.value) || 0;
    const saldo = totalGeneral - abono;

    document.getElementById('lblTotalGeneral').textContent = formatearMoneda(totalGeneral);
    document.getElementById('saldo').value = saldo.toFixed(2);
}

inputAbono.addEventListener('input', recalcularTotales);

// ===================== CONSECUTIVO DEL RECIBO =====================
function obtenerSiguienteNumeroRecibo() {
    const ultimo = parseInt(localStorage.getItem(CLAVE_CONSECUTIVO) || NUMERO_INICIAL, 10);
    const siguiente = ultimo + 1;
    localStorage.setItem(CLAVE_CONSECUTIVO, siguiente);
    return siguiente;
}

// ===================== GENERAR FACTURA FINAL =====================
function generarFacturaFinal() {
    const cliente = document.getElementById('cliente').value.trim();
    const direccion = document.getElementById('direccion').value.trim();
    const cedula = document.getElementById('cedula').value.trim();
    const telefono = document.getElementById('telefono').value.trim();

    if (!cliente || productos.length === 0) {
        alert('Ingresa el nombre del cliente y al menos un producto antes de generar la factura.');
        return;
    }

    const totalGeneral = productos.reduce((acumulado, p) => acumulado + (p.precio * p.cantidad), 0);
    const abono = parseFloat(inputAbono.value) || 0;
    const saldo = totalGeneral - abono;

    // Asignar número de recibo solo la primera vez que se genera este recibo
    if (numeroAsignadoActual === null) {
        numeroAsignadoActual = obtenerSiguienteNumeroRecibo();
    }

    const hoy = new Date();

    // Datos del cliente
    document.getElementById('numeroRecibo').textContent = numeroAsignadoActual;
    document.getElementById('fCliente').textContent = cliente;
    document.getElementById('fDireccion').textContent = direccion;
    document.getElementById('fCedula').textContent = cedula;
    document.getElementById('fTelefono').textContent = telefono;
    document.getElementById('fDia').textContent = hoy.getDate();
    document.getElementById('fMes').textContent = hoy.getMonth() + 1;
    document.getElementById('fAnio').textContent = hoy.getFullYear();
    document.getElementById('fAbono').textContent = formatearMoneda(abono);
    document.getElementById('fSaldo').textContent = formatearMoneda(saldo);

    // Tabla de artículos del recibo impreso
    document.getElementById('fTablaCuerpo').innerHTML = productos.map(p => `
        <tr>
            <td class="numerica">${p.cantidad}</td>
            <td>${p.nombre}</td>
            <td class="numerica">$${formatearMoneda(p.precio)}</td>
            <td class="numerica">$${formatearMoneda(p.precio * p.cantidad)}</td>
        </tr>
    `).join('');

    document.getElementById('fSubtotal').textContent = formatearMoneda(totalGeneral);
    document.getElementById('fTotalGeneral').textContent = formatearMoneda(totalGeneral);

    panelCaptura.style.display = 'none';
    panelFacturaFinal.style.display = 'block';
}

// ===================== VOLVER AL EDITOR =====================
function volverAlEditor() {
    // numeroAsignadoActual NO se reinicia: es la misma factura, solo se está editando.
    panelFacturaFinal.style.display = 'none';
    panelCaptura.style.display = 'block';
}