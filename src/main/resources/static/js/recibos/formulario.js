/* Pantallas: templates/recibos/almacen/nuevo.html, templates/recibos/almacen/editar.html, templates/recibos/fabrica/nuevo.html, templates/recibos/fabrica/editar.html */

function formatearMoneda(valor) {
    return valor.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function recalcularTotales() {
    let total = 0;
    document.querySelectorAll('.fila-producto-form').forEach(fila => {
        const precio = parseFloat(fila.querySelector('input[name="precios"]').value) || 0;
        const cantidad = parseFloat(fila.querySelector('input[name="cantidades"]').value) || 0;
        total += precio * cantidad;
    });

    // ── Descuento: no puede ser negativo ni superar el total bruto (mismo límite que el servidor) ──
    let descuento = parseFloat(document.getElementById('descuento').value) || 0;
    if (descuento < 0) descuento = 0;
    if (descuento > total) descuento = total;

    const totalConDescuento = total - descuento;
    const abono = parseFloat(document.getElementById('abono').value) || 0;
    const saldo = totalConDescuento - abono;

    document.getElementById('lblTotalGeneral').textContent = formatearMoneda(total);
    document.getElementById('lblTotalConDescuento').textContent = formatearMoneda(totalConDescuento);
    document.getElementById('saldo').value = saldo.toFixed(2);

    const lineaDescuento = document.getElementById('lineaDescuentoAplicado');
    if (descuento > 0) {
        lineaDescuento.style.display = 'block';
        document.getElementById('lblDescuentoAplicado').textContent = formatearMoneda(descuento);
    } else {
        lineaDescuento.style.display = 'none';
    }
}

function engancharFila(fila) {
    fila.querySelectorAll('input[name="precios"], input[name="cantidades"]').forEach(input => {
        input.addEventListener('input', recalcularTotales);
    });
}

function agregarFila() {
    const contenedor = document.getElementById('contenedorProductos');
    // En los recibos de fábrica los campos usan Bootstrap (form-control); en los de almacén no.
    const bs = contenedor.querySelector('input.form-control') ? 'form-control ' : '';
    const div = document.createElement('div');
    div.className = 'fila-producto-form';
    div.innerHTML = `
        <input type="text" name="nombresProducto" class="${bs}col-nombre" placeholder="Nombre del producto" required>
        <input type="number" name="precios" class="${bs}col-precio" step="0.01" min="0.01" placeholder="Precio" required>
        <input type="number" name="cantidades" class="${bs}col-cant" min="1" value="1" placeholder="Cant.">
        <button type="button" class="btn-eliminar-producto" onclick="eliminarFila(this)">Quitar</button>
    `;
    contenedor.appendChild(div);
    engancharFila(div);
}

function eliminarFila(boton) {
    const contenedor = document.getElementById('contenedorProductos');
    if (contenedor.children.length > 1) {
        boton.closest('.fila-producto-form').remove();
    }
    recalcularTotales();
}

document.getElementById('abono').addEventListener('input', recalcularTotales);
document.getElementById('descuento').addEventListener('input', recalcularTotales);
document.querySelectorAll('.fila-producto-form').forEach(engancharFila);
recalcularTotales();