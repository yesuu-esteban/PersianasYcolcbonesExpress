/* Pantalla: templates/fabrica/editar_pedido.html */

const campoAncho     = document.getElementById('campoAncho');
const campoAltura    = document.getElementById('campoAltura');
const campoColor     = document.getElementById('campoColor');
const ckCabezal      = document.getElementById('ckCabezal');
const ckPitillo      = document.getElementById('ckPitillo');
const ckConector     = document.getElementById('ckConector');
const selectTipoTubo = document.getElementById('selectTipoTubo');
const caja           = document.getElementById('cajaPrevisualizacion');

const selectRetazo  = document.getElementById('selectRetazo');
const selectRollo   = document.getElementById('selectRollo');
const selectTubo    = document.getElementById('selectTubo');
const selectCabezal = document.getElementById('selectCabezal');
const selectPesa    = document.getElementById('selectPesa');
const selectCuerda  = document.getElementById('selectCuerda');
const selectPitillo = document.getElementById('selectPitillo');
const filaCabezal   = document.getElementById('filaCabezalManual');
const filaPitillo   = document.getElementById('filaPitilloManual');

function toggleManual(btn) {
    const panel = document.getElementById('panelManual');
    const vis = panel.style.display !== 'none';
    panel.style.display = vis ? 'none' : '';
    btn.textContent = vis
        ? '⚙ Cambiar material manualmente (retazos, piezas específicas)'
        : '✕ Cerrar selección manual';
}

function opcionesRetazo(color, anchoNecesario, altoNecesario) {
    const candidatos = retazosDisponibles.filter(r =>
        r.color && r.color.toLowerCase() === color.toLowerCase() &&
        r.ancho >= (anchoNecesario - 0.001) &&
        r.alto  >= (altoNecesario  - 0.001)
    );
    let html = '<option value="auto">⚡ Automático (retazo si hay, si no rollo)</option>';
    if (candidatos.length) {
        html += '<optgroup label="✂ Retazos disponibles">';
        candidatos.forEach(r => {
            html += `<option value="${r.id}">Retazo #${r.id} · ${r.color} ${r.ancho}m × ${r.alto.toFixed(2)}m</option>`;
        });
        html += '</optgroup>';
    }
    return html;
}

function opcionesRollo(color) {
    const todos    = rollosDisponibles.filter(r => r.color && r.color.toLowerCase() === color.toLowerCase() && r.largoRestante > 0.001);
    const normales = todos.filter(r => r.largoRestante >= 3);
    const retazos  = todos.filter(r => r.largoRestante <  3);
    if (!todos.length) return '<option value="auto">– Sin rollos de ese color –</option>';
    let html = '<option value="auto">⚡ Automático (rollo)</option>';
    if (normales.length) {
        html += '<optgroup label="Rollos disponibles">';
        normales.forEach(r => { html += `<option value="${r.id}">Rollo #${r.id} · ${r.color} ${r.ancho}m · ${r.largoRestante.toFixed(2)} m</option>`; });
        html += '</optgroup>';
    }
    if (retazos.length) {
        html += '<optgroup label="⚠ Retazos de rollo (< 3m)">';
        retazos.forEach(r => { html += `<option value="${r.id}">Retazo #${r.id} · ${r.color} ${r.ancho}m · ${r.largoRestante.toFixed(2)} m</option>`; });
        html += '</optgroup>';
    }
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

function actualizarSelectsManual() {
    const color   = campoColor.value;
    const ancho   = parseFloat(campoAncho.value) || 0;
    const alto    = parseFloat(campoAltura.value) || 0;
    const cabezal = ckCabezal.checked;

    const tipoTuboOverride = selectTipoTubo.value;
    let nombreTubo;
    if (tipoTuboOverride && tipoTuboOverride !== 'auto') {
        nombreTubo = 'Tubo ' + tipoTuboOverride;
    } else {
        const esTuboGrande = ancho > 2.50 || alto > 2.50;
        nombreTubo = esTuboGrande ? 'Tubo R24' : 'Tubo R16';
    }

    const descTela   = cabezal ? 0.035 : 0.03;
    const anchoCorte = ancho > 0 ? ancho - descTela : 0;
    const altoCorte  = alto  > 0 ? alto  + 0.20    : 0;

    const retazoVal = selectRetazo.value;
    const rolloVal  = selectRollo.value;

    selectRetazo.innerHTML  = opcionesRetazo(color, anchoCorte, altoCorte);
    selectRollo.innerHTML   = opcionesRollo(color);

    if (retazoVal !== 'auto') selectRetazo.value = retazoVal;
    if (rolloVal  !== 'auto') selectRollo.value  = rolloVal;

    selectTubo.innerHTML    = opcionesPieza(nombreTubo);
    selectPesa.innerHTML    = opcionesPieza('Pesa');
    selectCuerda.innerHTML  = opcionesPieza('Cuerda');
    selectPitillo.innerHTML = opcionesPieza('Pitillo');

    filaCabezal.style.display = cabezal ? '' : 'none';
    selectCabezal.innerHTML   = cabezal ? opcionesPieza('Cabezal') : '<option value="auto">⚡ Automático</option>';

    filaPitillo.style.display = ckPitillo.checked ? '' : 'none';
}

let timer = null;
function disparar() {
    clearTimeout(timer);
    timer = setTimeout(() => {
        consultarPrevisualizacion();
        actualizarSelectsManual();
    }, 420);
}

async function consultarPrevisualizacion() {
    const ancho    = campoAncho.value;
    const alto     = campoAltura.value;
    const color    = campoColor.value;
    const cabezal  = ckCabezal.checked;
    const pitillo  = ckPitillo.checked;
    const conector = ckConector.checked;
    const tipoTuboManual = selectTipoTubo.value;

    const retazoManualId = selectRetazo.value !== 'auto' ? selectRetazo.value : null;
    const rolloManualId  = selectRollo.value  !== 'auto' ? selectRollo.value  : null;

    if (!ancho || !alto) {
        caja.className = 'previsualizacion';
        caja.innerHTML = '<span class="cargando">Completa ancho y alto para ver disponibilidad.</span>';
        return;
    }
    caja.className = 'previsualizacion';
    caja.innerHTML = '<span class="cargando">🔍 Consultando inventario...</span>';

    try {
        const params = new URLSearchParams({
            ancho, altura: alto, color,
            usaCabezal: cabezal,
            usaPitilloPesa: pitillo,
            usaConectorTope: conector,
            tipoTuboManual
        });

        const token = localStorage.getItem('authToken');
        if (token) params.set('token', token);

        const resp = await fetch('/taller/previsualizar-material?' + params.toString());
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const d = await resp.json();

        if (d.disponible) {
            caja.className = 'previsualizacion ok';
            const a = parseFloat(ancho), h = parseFloat(alto);
            const descTela = cabezal ? 0.035 : 0.03;
            const descTubo = cabezal ? 0.03  : 0.025;
            const cAncho = (a - descTela).toFixed(3);
            const cAlto  = (h + 0.20).toFixed(3);
            const cTubo  = (a - descTubo).toFixed(3);

            let html = '<strong>✓ Material disponible para esta configuración</strong>';
            html += '<table style="width:100%;border-collapse:collapse;margin-top:7px">';

            if (retazoManualId) {
                const retazo = retazosDisponibles.find(r => String(r.id) === String(retazoManualId));
                if (retazo) {
                    html += fila2('✂ Retazo', `${cAncho} m × ${cAlto} m`,
                        `⭐ Retazo #${retazo.id} · ${retazo.color} ${retazo.ancho}m × ${retazo.alto.toFixed(2)}m (elegido por ti)`);
                }
            } else if (rolloManualId) {
                const rollo = rollosDisponibles.find(r => String(r.id) === String(rolloManualId));
                if (rollo) {
                    const mayor = Math.max(parseFloat(cAncho), parseFloat(cAlto));
                    const sobrante = (rollo.largoRestante - mayor).toFixed(2);
                    html += fila2('🧵 Tela', `${cAncho} m × ${cAlto} m`,
                        `⭐ Rollo #${rollo.id} · ${rollo.color} ${rollo.ancho}m · quedarían aprox. ${sobrante} m (elegido por ti)`);
                }
            } else {
                if (d.retazoSugerido)       html += fila2('✂ Retazo',  `${cAncho} m × ${cAlto} m`, d.retazoSugerido);
                else if (d.rolloSugerido)   html += fila2('🧵 Tela',   `${cAncho} m × ${cAlto} m`, d.rolloSugerido);
            }

            if (d.tuboSugerido)           html += fila2('🔧 Tubo',          `${cTubo} m`,                    d.tuboSugerido);
            if (d.cabezalSugerido)        html += fila2('🪟 Cabezal',        `${(a - 0.005).toFixed(3)} m`,   d.cabezalSugerido);
            if (d.pesaSugerida)           html += fila2('⚖ Pesa',           `${cTubo} m`,                    d.pesaSugerida);
            if (d.cuerdaSugerida)         html += fila2('🧶 Cuerda',          h <= 1.50 ? '3 m' : '4 m',      d.cuerdaSugerida);
            if (d.controlInfo)            html += fila2('🎛 Control',         '1 und.',                        d.controlInfo);
            if (d.acopleInfo)             html += fila2('🔩 Acople',          '',                              d.acopleInfo);
            if (d.terminalInfo)           html += fila2('🔌 Terminal',        '1 und.',                        d.terminalInfo);
            if (d.pitilloSugerido)        html += fila2('🪡 Pitillo',        `${cAncho} m`,                   d.pitilloSugerido);
            if (d.conectorInfo)           html += fila2('🔗 Conec./Tope',    '',                               d.conectorInfo);
            if (d.soporteInfo)            html += fila2('🔩 Soporte',         '2 und.',                        d.soporteInfo);
            if (d.tapaInfo)               html += fila2('🧢 Tapa Cabezal',    '2 und.',                        d.tapaInfo);
            if (d.tapaPerfilInfo)         html += fila2('🧢 Tapa Perfil',    '2 und.',                        d.tapaPerfilInfo);
            if (d.tornilloInfo)           html += fila2('🔨 Tornillo',        cabezal ? '8 und.' : '2 und.',   d.tornilloInfo);
            if (d.tornilloPerforanteInfo) html += fila2('🔧 T. Perforante',  '4 und.',                        d.tornilloPerforanteInfo);

            html += '</table>';
            caja.innerHTML = html;
        } else {
            caja.className = 'previsualizacion falta';
            caja.innerHTML = '<strong>⚠ Falta material:</strong><br>' + d.faltantes.join('<br>');
        }
    } catch (e) {
        console.error('Error consultando previsualización:', e);
        caja.className = 'previsualizacion falta';
        caja.innerHTML = '<span>No se pudo consultar el inventario.</span>';
    }
}

function fila2(material, medida, info) {
    return `<tr style="border-top:1px solid #d6efdd">
        <td style="padding:2px 4px;font-weight:600;white-space:nowrap">${material}</td>
        <td style="padding:2px 6px;color:#555;white-space:nowrap">${medida}</td>
        <td style="padding:2px 4px;color:#1a6b3a">${info}</td>
    </tr>`;
}

selectRetazo.addEventListener('change', function () {
    if (this.value !== 'auto') selectRollo.value = 'auto';
    consultarPrevisualizacion();
});
selectRollo.addEventListener('change', function () {
    if (this.value !== 'auto') selectRetazo.value = 'auto';
    consultarPrevisualizacion();
});

campoAncho.addEventListener('input', disparar);
campoAltura.addEventListener('input', disparar);
campoColor.addEventListener('change', disparar);
ckCabezal.addEventListener('change', disparar);
ckPitillo.addEventListener('change', () => { actualizarSelectsManual(); disparar(); });
ckConector.addEventListener('change', disparar);
selectTipoTubo.addEventListener('change', disparar);

let extraIndex = 0;

function opcionesCatalogo() {
    let html = '<option value="libre">✏ Insumo fuera del catálogo (texto libre)</option>';
    catalogoInsumos.forEach(ins => {
        html += `<option value="${ins.id}">${ins.nombre}${ins.tieneMedida ? ' (por medida)' : ' (por unidad)'}</option>`;
    });
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
            <input type="number" step="0.01" min="0" name="extraCantidad[${idx}]" class="form-control form-control-sm campo-cantidad-insumo-venta" placeholder="Cantidad">
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
    const sel = filaCampos.querySelector('.select-extra-insumo');
    sel.addEventListener('change', function () { toggleLibre(this); });
    if (valSelect) sel.value = valSelect;
    if (valLibre) filaCampos.querySelector('input[name^="extraNombreLibre"]').value = valLibre;
    if (valCant) filaCampos.querySelector('input[name^="extraCantidad"]').value = valCant;
    toggleLibre(sel);
    actualizarInfoExtra(sel);
}

document.addEventListener('DOMContentLoaded', () => {
    actualizarSelectsManual();
    consultarPrevisualizacion();

    if (typeof extrasExistentes !== 'undefined' && extrasExistentes.length) {
        extrasExistentes.forEach(ex => {
            agregarFilaExtra('libre', ex.fuenteDescripcion, ex.metrosUsados);
        });
    }
});