/* Pantalla: templates/fabrica/editar_pedido_riel.html */

const campoAncho    = document.getElementById('campoAncho');
const campoAltura   = document.getElementById('campoAltura');
const ckUsaPolea    = document.getElementById('ckUsaPolea');
const caja          = document.getElementById('cajaPrevisualizacion');

async function calcularPreview() {
    const ancho = parseFloat(campoAncho.value);
    const alto = parseFloat(campoAltura.value) || 0;
    const usaPolea = ckUsaPolea.checked;
    const selectApertura = document.getElementById('selectLadoApertura');
    const ladoApertura = selectApertura ? selectApertura.value : 'Izquierda';
    const haciaExtremos = ladoApertura === 'Hacia los extremos';
    const selectBastonEl = document.querySelector('select[name="bastonElegido"]');
    const bastonElegido = selectBastonEl ? selectBastonEl.value : 'Bastón 0.80';

    if (!ancho || ancho <= 0) {
        caja.className = 'previsualizacion';
        caja.innerHTML = '<span class="cargando">Completa el ancho para ver el corte estimado.</span>';
        return;
    }

    caja.className = 'previsualizacion';
    caja.innerHTML = '<span class="cargando">🔍 Consultando inventario...</span>';

    const corteRiel = (ancho - 0.075).toFixed(3);
    let soportes;
    if (ancho >= 3.50) soportes = 5;
    else if (ancho >= 2.50) soportes = 4;
    else if (ancho >= 1.50) soportes = 3;
    else soportes = 2;

    try {
        const params = new URLSearchParams({ ancho, altura: alto, usaPolea, bastonElegido, ladoApertura });
        const token = localStorage.getItem('authToken');
        if (token) params.set('token', token);

        const resp = await fetch('/taller/previsualizar-material-riel?' + params.toString());
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const d = await resp.json();

        if (d.disponible) {
            let html = '<strong>✓ Material disponible</strong>';
            html += '<table style="width:100%;border-collapse:collapse;margin-top:6px">';
            if (d.rielInfo)     html += trRiel('🪟 Riel',      corteRiel + ' m', d.rielInfo);
            if (d.roachinaInfo) html += trRiel('📌 Roachina',  corteRiel + ' m', d.roachinaInfo);
            if (d.riataInfo)    html += trRiel('🪢 Riata',     corteRiel + ' m (siempre obligatoria)', d.riataInfo);
            if (usaPolea) {
                const corteCuerda = (ancho * 2 + 4).toFixed(3);
                if (d.cuerdaOndaInfo) html += trRiel('🧶 Cuerda de onda', corteCuerda + ' m', d.cuerdaOndaInfo);
                if (d.poleaInfo)      html += trRiel('🔧 Poleas', '2 und.', d.poleaInfo);
                if (d.crusadorInfo)   html += trRiel('🎛 Crusador', (haciaExtremos ? '2' : '1') + ' und.' + (haciaExtremos ? ' (hacia los extremos)' : ''), d.crusadorInfo);
            } else {
                if (d.tapaRielInfo) html += trRiel('🧢 Tapas de riel', '2 und.', d.tapaRielInfo);
                if (d.bastonInfo)   html += trRiel('🪄 Bastón', (haciaExtremos ? '2' : '1') + ' und.' + (haciaExtremos ? ' (hacia los extremos)' : ''), d.bastonInfo);
            }
            if (d.soporteRielInfo) html += trRiel('🔩 Soportes de riel', soportes + ' und. (según ancho)', d.soporteRielInfo);
            html += '</table>';
            caja.className = 'previsualizacion ok';
            caja.innerHTML = html;
        } else {
            caja.className = 'previsualizacion falta';
            caja.innerHTML = '<strong>⚠ Falta material:</strong><br>' + d.faltantes.join('<br>');
        }
    } catch (e) {
        console.error('Error consultando previsualización de riel:', e);
        caja.className = 'previsualizacion falta';
        caja.innerHTML = '<span>No se pudo consultar el inventario.</span>';
    }
}

function trRiel(mat, med, info) {
    return `<tr style="border-top:1px solid #d6efdd">
        <td style="padding:2px 4px;font-weight:600;white-space:nowrap">${mat}</td>
        <td style="padding:2px 6px;color:#555;white-space:nowrap">${med}</td>
        <td style="padding:2px 4px;color:#1a6b3a">${info}</td>
    </tr>`;
}

let timerRiel = null;
function dispararPreviewRiel() {
    clearTimeout(timerRiel);
    timerRiel = setTimeout(calcularPreview, 420);
}

campoAncho.addEventListener('input', dispararPreviewRiel);
campoAltura.addEventListener('input', dispararPreviewRiel);
ckUsaPolea.addEventListener('change', dispararPreviewRiel);
ckUsaPolea.addEventListener('change', function() {
    document.getElementById('filaBastonRiel').style.display = this.checked ? 'none' : '';
});
document.getElementById('selectLadoApertura').addEventListener('change', dispararPreviewRiel);
const selectBastonListener = document.querySelector('select[name="bastonElegido"]');
if (selectBastonListener) selectBastonListener.addEventListener('change', dispararPreviewRiel);

let extraIndex = 0;

function opcionesCatalogo() {
    let html = '<option value="libre">✏ Insumo fuera del catálogo (texto libre)</option>';
    catalogoInsumos.forEach(ins => {
        html += `<option value="${ins.id}">${ins.nombre}${ins.tieneMedida ? ' (por medida)' : ' (por unidad)'}</option>`;
    });
    return html;
}

function opcionesPieza(nombreInsumo) {
    const filtradas = piezasDisponibles.filter(p => {
        const nombre = p.insumoNombre || (p.insumo && p.insumo.nombre) || '';
        return nombre.toLowerCase() === nombreInsumo.toLowerCase() && p.largoRestante > 0.001;
    });
    if (!filtradas.length) return `<option value="auto">– Sin piezas de "${nombreInsumo}" –</option>`;
    let html = '<option value="auto">⚡ Automático (sugerido)</option>';
    filtradas.forEach(p => { html += `<option value="${p.id}">Pieza #${p.id} · ${p.largoRestante.toFixed(3)} m restantes</option>`; });
    return html;
}

function filaExtraHtml(idx) {
    return `
    <div class="row g-2 align-items-center mb-1 fila-extra" data-indice="${idx}">
        <div class="col-md-5">
            <select name="extraInsumoId[${idx}]" class="form-select form-select-sm select-extra-insumo" onchange="actualizarInfoExtra(this)">
                ${opcionesCatalogo()}
            </select>
        </div>
        <div class="col-md-4 campo-libre-extra" style="display:none;">
            <input type="text" name="extraNombreLibre[${idx}]" class="form-control form-control-sm" placeholder="Nombre del insumo">
        </div>
        <div class="col-md-2">
            <input type="number" step="0.01" min="0" name="extraCantidad[${idx}]" class="form-control form-control-sm" placeholder="Cantidad">
        </div>
        <div class="col-md-1">
            <button type="button" class="btn btn-sm btn-outline-danger" onclick="this.closest('.fila-extra').remove()">✕</button>
        </div>
    </div>
    <div class="row mb-2 fila-extra-info" data-indice="${idx}">
        <div class="col-md-7">
            <div class="info-stock-insumo"></div>
        </div>
        <div class="col-md-5 campo-pieza-insumo" style="display:none;">
            <label class="form-label mb-1" style="font-size:0.76rem">🔩 ¿De qué pieza específica se corta?</label>
            <select name="extraPiezaId[${idx}]" class="form-select form-select-sm select-pieza-insumo">
                <option value="auto">⚡ Automático (sugerido)</option>
            </select>
        </div>
    </div>`;
}

function toggleLibre(select) {
    const fila = select.closest('.fila-extra');
    const campoLibre = fila.querySelector('.campo-libre-extra');
    campoLibre.style.display = select.value === 'libre' ? '' : 'none';
}

function actualizarInfoExtra(select) {
    const filaCampos = select.closest('.fila-extra');
    const filaInfo = filaCampos.nextElementSibling;
    const campoLibre = filaCampos.querySelector('.campo-libre-extra');
    const campoCantidad = filaCampos.querySelector('input[name^="extraCantidad"]');
    const infoDiv = filaInfo ? filaInfo.querySelector('.info-stock-insumo') : null;
    const campoPieza = filaInfo ? filaInfo.querySelector('.campo-pieza-insumo') : null;
    const selectPieza = filaInfo ? filaInfo.querySelector('.select-pieza-insumo') : null;

    if (select.value === 'libre') {
        campoLibre.style.display = '';
        campoCantidad.placeholder = 'Cantidad';
        campoCantidad.step = '1';
        if (campoPieza) campoPieza.style.display = 'none';
        if (infoDiv) {
            infoDiv.className = 'info-stock-insumo';
            infoDiv.textContent = '✏ Insumo fuera de catálogo: no se descuenta del inventario, solo queda registrado en el reporte.';
        }
        return;
    }
    campoLibre.style.display = 'none';

    const insumo = catalogoInsumos.find(ins => String(ins.id) === String(select.value));
    if (!insumo || !infoDiv) return;

    if (insumo.tieneMedida) {
        const piezas = piezasDisponibles.filter(p => {
            const nombre = p.insumoNombre || (p.insumo && p.insumo.nombre) || '';
            return nombre.toLowerCase() === insumo.nombre.toLowerCase() && p.largoRestante > 0.001;
        });
        const totalMetros = piezas.reduce((sum, p) => sum + p.largoRestante, 0);

        campoCantidad.placeholder = 'Metros';
        campoCantidad.step = '0.01';

        if (campoPieza && selectPieza) {
            campoPieza.style.display = '';
            selectPieza.innerHTML = opcionesPieza(insumo.nombre);
        }

        if (piezas.length === 0) {
            infoDiv.className = 'info-stock-insumo sin-stock';
            infoDiv.textContent = `⚠ Este insumo se mide en metros y no hay piezas disponibles de "${insumo.nombre}".`;
        } else {
            infoDiv.className = 'info-stock-insumo ok';
            infoDiv.textContent = `📏 Se mide en metros · disponible: ${totalMetros.toFixed(2)} m en ${piezas.length} pieza(s).`;
        }
    } else {
        const stock = insumo.stockUnidades != null ? insumo.stockUnidades : 0;
        campoCantidad.placeholder = 'Unidades';
        campoCantidad.step = '1';

        if (campoPieza) campoPieza.style.display = 'none';

        if (stock <= 0) {
            infoDiv.className = 'info-stock-insumo sin-stock';
            infoDiv.textContent = `⚠ Se vende por unidad y no hay stock de "${insumo.nombre}".`;
        } else {
            infoDiv.className = 'info-stock-insumo ok';
            infoDiv.textContent = `🔢 Se vende por unidad · disponible: ${stock} unidad(es).`;
        }
    }
}

function agregarFilaExtra(valSelect, valLibre, valCant) {
    const cont = document.getElementById('contenedorExtras');
    const idx = extraIndex++;
    cont.insertAdjacentHTML('beforeend', filaExtraHtml(idx));
    const filaCampos = cont.querySelector(`.fila-extra[data-indice="${idx}"]`);
    const select = filaCampos.querySelector('.select-extra-insumo');
    select.addEventListener('change', function () { toggleLibre(this); });

    if (valSelect) select.value = valSelect;
    if (valLibre) filaCampos.querySelector('input[name^="extraNombreLibre"]').value = valLibre;
    if (valCant) filaCampos.querySelector('input[name^="extraCantidad"]').value = valCant;

    toggleLibre(select);
    actualizarInfoExtra(select);
}

document.addEventListener('DOMContentLoaded', () => {
    calcularPreview();

    document.getElementById('filaBastonRiel').style.display = ckUsaPolea.checked ? 'none' : '';

    if (typeof extrasExistentes !== 'undefined' && extrasExistentes.length) {
        extrasExistentes.forEach(ex => {
            agregarFilaExtra('libre', ex.fuenteDescripcion, ex.metrosUsados);
        });
    }
});