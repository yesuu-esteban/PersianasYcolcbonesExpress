/* Pantallas: templates/almacen/formulario.html, templates/almacen/editar_pedido.html */

const OPCIONES_PRODUCTO = [
    "PERSIANA VERTICAL", "PANEL JAPONÉS", "SHEER ELEGANCE", "BLACKOUT ENROLLABLE",
    "MINI PERSIANA", "MACRO MADERA", "CORTINA DE ONDA SERENA", "TOLDOS VERTICALES", "ENROLLABLE"
];
const OPCIONES_MATERIAL = ["POLIÉSTER", "MADERA", "BLACKOUT", "SCREEN"];

function construirOpcionesSelect(opciones) {
    let html = '<option value="">-- Selecciona --</option>';
    opciones.forEach(op => {
        html += `<option value="${op}">${op}</option>`;
    });
    html += '<option value="__OTRO__">✏️ Otro (escribir)</option>';
    return html;
}

function agregarFilaProducto() {
    const idx = indiceDetalle++;
    const fila = `
        <tr>
            <td>
                <div class="campo-selector">
                    <select class="form-select form-select-sm text-uppercase campo-producto-select"
                            name="detalles[${idx}].producto">
                        ${construirOpcionesSelect(OPCIONES_PRODUCTO)}
                    </select>
                    <div class="campo-manual d-none">
                        <input type="text" class="form-control form-control-sm text-uppercase campo-producto-manual"
                               autocomplete="off" name="detalles[${idx}].producto" placeholder="Escribe el producto..." disabled>
                        <button type="button" class="btn-volver-lista" title="Volver a la lista">↩</button>
                    </div>
                </div>
            </td>
            <td>
                <div class="campo-selector">
                    <select class="form-select form-select-sm text-uppercase campo-material-select"
                            name="detalles[${idx}].material">
                        ${construirOpcionesSelect(OPCIONES_MATERIAL)}
                    </select>
                    <div class="campo-manual d-none">
                        <input type="text" class="form-control form-control-sm text-uppercase campo-material-manual"
                               autocomplete="off" name="detalles[${idx}].material" placeholder="Escribe el material..." disabled>
                        <button type="button" class="btn-volver-lista" title="Volver a la lista">↩</button>
                    </div>
                </div>
            </td>
            <td><input type="number" class="form-control form-control-sm" name="detalles[${idx}].cantidad" value="1" min="1" step="1" required></td>
            <td>
                <input type="number" step="0.01" min="0.01" class="form-control form-control-sm money-input" name="detalles[${idx}].precioUnitario" value="0.01" required>
                <small class="preview-precio"></small>
            </td>
            <td>
                <input type="number" step="0.01" min="0" class="form-control form-control-sm money-input" name="detalles[${idx}].precioFabricaUnitario" value="0">
                <small class="preview-precio"></small>
            </td>
            <td><button type="button" class="btn btn-sm btn-outline-danger" onclick="this.closest('tr').remove(); calcularTotales();">✕</button></td>
        </tr>`;
    document.getElementById('cuerpoDetalles').insertAdjacentHTML('beforeend', fila);
    activarPreviewsPrecios();
    activarSelectoresOtro();
    calcularTotales();
}

// ── Cuadro resumen: suma cantidad × precio venta de cada producto, menos el descuento ──
function calcularTotales() {
    let totalProductos = 0;

    document.querySelectorAll('#cuerpoDetalles tr').forEach(fila => {
        const inputCantidad = fila.querySelector('input[name$=".cantidad"]');
        const inputPrecio = fila.querySelector('input[name$=".precioUnitario"]');
        if (!inputCantidad || !inputPrecio) return;

        const cantidad = parseFloat(inputCantidad.value) || 0;
        const precio = parseFloat(inputPrecio.value) || 0;
        totalProductos += cantidad * precio;
    });

    const inputDescuento = document.querySelector('input[name="descuento"]');
    const descuento = inputDescuento ? (parseFloat(inputDescuento.value) || 0) : 0;
    const totalAPagar = Math.max(totalProductos - descuento, 0);

    const elTotalProductos = document.getElementById('totalProductos');
    const elTotalDescuento = document.getElementById('totalDescuento');
    const elTotalAPagar = document.getElementById('totalAPagar');
    if (elTotalProductos) elTotalProductos.textContent = '$ ' + formatearMiles(totalProductos);
    if (elTotalDescuento) elTotalDescuento.textContent = '$ ' + formatearMiles(descuento);
    if (elTotalAPagar) elTotalAPagar.textContent = '$ ' + formatearMiles(totalAPagar);
}

function formatearMiles(numero) {
    return new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 }).format(numero);
}

function activarPreviewsPrecios() {
    document.querySelectorAll('.money-input').forEach(input => {
        if (input.dataset.previewActivo) return;
        input.dataset.previewActivo = "1";

        const preview = input.parentElement.querySelector('.preview-precio');
        const actualizar = () => {
            const valor = parseFloat(input.value);
            if (preview) {
                preview.textContent = !isNaN(valor) && valor > 0 ? '$ ' + formatearMiles(valor) : '';
            }
        };
        input.addEventListener('input', actualizar);
        actualizar();
    });
}

// ── Selector Producto/Material: select nativo + campo manual "Otro" ──
function inicializarSelectorOtro(select) {
    if (select.dataset.otroInit) return;
    select.dataset.otroInit = "1";

    const wrapper = select.closest('.campo-selector');
    const manualWrap = wrapper.querySelector('.campo-manual');
    const manualInput = manualWrap.querySelector('input[type="text"]');
    const btnVolver = manualWrap.querySelector('.btn-volver-lista');

    function mostrarManual(enfocar) {
        select.classList.add('d-none');
        select.disabled = true;
        manualWrap.classList.remove('d-none');
        manualInput.disabled = false;
        if (enfocar) manualInput.focus();
    }

    function mostrarSelect() {
        manualWrap.classList.add('d-none');
        manualInput.disabled = true;
        manualInput.value = '';
        select.classList.remove('d-none');
        select.disabled = false;
        select.value = '';
    }

    // Si el campo ya trae un valor libre (no está en las opciones), arrancar en modo manual.
    // Esto pasa, por ejemplo, con pedidos ya guardados que usaron "Otro" antes de este cambio.
    const opcionesDisponibles = Array.from(select.options).map(o => o.value);
    const valorActual = (manualInput.value || '').trim();

    if (valorActual !== '' && !opcionesDisponibles.includes(valorActual)) {
        mostrarManual(false);
    } else {
        manualInput.disabled = true;
        if (valorActual !== '') {
            select.value = valorActual;
        }
    }

    select.addEventListener('change', () => {
        if (select.value === '__OTRO__') {
            manualInput.value = '';
            mostrarManual(true);
        }
    });

    btnVolver.addEventListener('click', () => mostrarSelect());
}

function activarSelectoresOtro() {
    document.querySelectorAll('.campo-producto-select, .campo-material-select, .campo-vendedor-select').forEach(inicializarSelectorOtro);
}

document.addEventListener('DOMContentLoaded', () => {
    activarPreviewsPrecios();
    activarSelectoresOtro();
    calcularTotales();
    document.querySelector('form').addEventListener('input', calcularTotales);
});