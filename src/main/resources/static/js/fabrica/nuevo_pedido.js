/* Pantalla: templates/fabrica/nuevo_pedido.html */

let filaIndex = 1;

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
    let html = '<option value="auto">⚡ Automático (rollo)</option>';
    if (!todos.length) return '<option value="auto">– Sin rollos de ese color –</option>';
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

function actualizarSelectsManualFila(fila) {
    const color      = fila.querySelector('.campo-color').value;
    const ancho      = parseFloat(fila.querySelector('.campo-ancho').value) || 0;
    const alto       = parseFloat(fila.querySelector('.campo-alto').value)  || 0;
    const cabezal    = fila.querySelector('.campo-cabezal').checked;
    const usaPitillo = fila.querySelector('.campo-pitillo').checked;

    const tipoTuboOverride = fila.querySelector('.select-tipo-tubo').value;
    let nombreTubo;
    if (tipoTuboOverride && tipoTuboOverride !== 'auto') {
        nombreTubo = 'Tubo ' + tipoTuboOverride;
    } else {
        const esTuboGrande = ancho > 2.50 || alto > 2.50;
        nombreTubo = esTuboGrande ? 'Tubo R24' : 'Tubo R16';
    }

    const descTela = cabezal ? 0.035 : 0.03;
    const anchoCorte = ancho > 0 ? ancho - descTela : 0;
    const altoCorte  = alto  > 0 ? alto  + 0.20    : 0;

    const retazoVal = fila.querySelector('.select-retazo').value;
    const rolloVal  = fila.querySelector('.select-rollo').value;

    fila.querySelector('.select-retazo').innerHTML  = opcionesRetazo(color, anchoCorte, altoCorte);
    fila.querySelector('.select-rollo').innerHTML   = opcionesRollo(color);

    if (retazoVal !== 'auto') fila.querySelector('.select-retazo').value = retazoVal;
    if (rolloVal !== 'auto') fila.querySelector('.select-rollo').value = rolloVal;

    fila.querySelector('.select-tubo').innerHTML    = opcionesPieza(nombreTubo);
    fila.querySelector('.select-pesa').innerHTML    = opcionesPieza('Pesa');
    fila.querySelector('.select-cuerda').innerHTML  = opcionesPieza('Cuerda');
    fila.querySelector('.select-pitillo').innerHTML = opcionesPieza('Pitillo');

    const filaCabezal = fila.querySelector('.campo-cabezal-manual');
    if (filaCabezal) {
        filaCabezal.style.display = cabezal ? '' : 'none';
        fila.querySelector('.select-cabezal').innerHTML = cabezal ? opcionesPieza('Cabezal') : '<option value="auto">⚡ Automático</option>';
    }

    const filaPitillo = fila.querySelector('.campo-pitillo-manual');
    if (filaPitillo) filaPitillo.style.display = usaPitillo ? '' : 'none';
}

function toggleManual(btn) {
    const panel = btn.closest('.fila-producto').querySelector('.seccion-manual');
    const vis   = panel.style.display !== 'none';
    panel.style.display = vis ? 'none' : '';
    btn.textContent = vis ? '⚙ Selección manual de material' : '✕ Cerrar selección manual';
}

function engancharEventosFila(fila) {
    const disparar = () => {
        clearTimeout(fila._t);
        fila._t = setTimeout(() => {
            actualizarSelectsManualFila(fila);
            programarPrevisualizacionLote();
        }, 420);
    };
    const cantidad = fila.querySelector('.campo-cantidad');
    if (cantidad) cantidad.addEventListener('input', programarPrevisualizacionLote);
    const desc = fila.querySelector('[name="descripciones"]');
    if (desc) desc.addEventListener('change', programarPrevisualizacionLote);
    ['.select-tubo', '.select-cabezal', '.select-pesa', '.select-cuerda', '.select-pitillo', '.select-tipo-control'].forEach(sel => {
        const s = fila.querySelector(sel);
        if (s) s.addEventListener('change', programarPrevisualizacionLote);
    });
    fila.querySelector('.campo-ancho').addEventListener('input', disparar);
    fila.querySelector('.campo-alto').addEventListener('input', disparar);
    fila.querySelector('.campo-color').addEventListener('change', disparar);
    fila.querySelector('.campo-cabezal').addEventListener('change', disparar);
    fila.querySelector('.campo-pitillo').addEventListener('change', () => { actualizarSelectsManualFila(fila); disparar(); });
    fila.querySelector('.campo-conector').addEventListener('change', disparar);
    fila.querySelector('.select-tipo-tubo').addEventListener('change', () => { actualizarSelectsManualFila(fila); disparar(); });

    fila.querySelector('.select-retazo').addEventListener('change', function() {
        if (this.value !== 'auto') fila.querySelector('.select-rollo').value = 'auto';
        programarPrevisualizacionLote();
    });
    fila.querySelector('.select-rollo').addEventListener('change', function() {
        if (this.value !== 'auto') fila.querySelector('.select-retazo').value = 'auto';
        programarPrevisualizacionLote();
    });
}

function opcionesTipoControl() {
    const lista = (typeof opcionesControl !== 'undefined' && opcionesControl) ? opcionesControl : [];
    return '<option value="auto">⚡ Automático (según medida)</option>'
        + lista.map(c => `<option value="${c}">${c === 'Control R24' ? 'Control R24 (paquete completo)' : c}</option>`).join('');
}

function htmlFila(idx) {
    const opcionesColor = listaColores.map(c => `<option value="${c}">${c}</option>`).join('');
    const primerColor   = listaColores[0] || '';
    return `
    <div class="fila-producto" data-indice="${idx}">
        <div class="row align-items-end g-2 mb-1">
            <div class="col-md-3">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Descripción</label>
                <input type="text" name="descripciones" class="form-control form-control-sm" placeholder="Descripción" required>
            </div>
            <div class="col-md-1">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Cant.</label>
                <input type="number" name="cantidades" class="form-control form-control-sm campo-cantidad" value="1" min="1" required>
            </div>
            <div class="col-md-1">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Ancho</label>
                <input type="number" step="0.001" name="anchos" class="form-control form-control-sm campo-ancho" placeholder="m" required>
            </div>
            <div class="col-md-1">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Alto</label>
                <input type="number" step="0.001" name="alturas" class="form-control form-control-sm campo-alto" placeholder="m" required>
            </div>
            <div class="col-md-2">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Color</label>
                <select name="colores" class="form-select form-select-sm campo-color">${opcionesColor}</select>
            </div>
            <div class="col-md-2">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Mando</label>
                <select name="mandos" class="form-select form-select-sm">
                    <option value="Mando Derecho">Mando Derecho</option>
                    <option value="Mando Izquierdo">Mando Izquierdo</option>
                </select>
            </div>
            <div class="col-md-1 text-center">
                <label class="form-label fw-bold mb-1 d-block" style="font-size:0.82rem">Cabezal</label>
                <input type="checkbox" name="cabezales[${idx}]" value="true" class="form-check-input fs-5 campo-cabezal">
            </div>
            <div class="col-md-1 text-end">
                <button type="button" class="btn btn-outline-danger btn-sm mt-3"
                        onclick="this.closest('.fila-producto').remove()">✕</button>
            </div>
        </div>
        <div class="flags-row ms-1">
            <label>
                <input type="checkbox" name="usaPitilloPesa[${idx}]" value="true" class="form-check-input campo-pitillo" checked>
                🪡 Pitillo
            </label>
            <label>
                <input type="checkbox" name="usaConectorTope[${idx}]" value="true" class="form-check-input campo-conector" checked>
                🔗 Conector / Tope
            </label>
            <button type="button" class="toggle-manual" onclick="toggleManual(this)">⚙ Selección manual de material</button>
        </div>
        <div class="previsualizacion mt-2">
            <span class="cargando">Completa ancho, alto y color para ver el material necesario.</span>
        </div>
        <div class="seccion-manual" style="display:none;">
            <div class="fw-bold mb-2" style="font-size:0.82rem">⚙ Selección manual <small class="text-muted">(deja "Automático" para que el sistema elija)</small></div>
            <div class="row g-2">
                <div class="col-md-6 col-lg-4">
                    <label class="form-label">🧵 Tela (rollo o retazo)</label>
                    <select name="retazoManual[${idx}]" class="form-select form-select-sm select-retazo">
                        ${opcionesRetazo(primerColor, 0, 0)}
                    </select>
                    <select name="rolloManual[${idx}]" class="form-select form-select-sm select-rollo mt-1">
                        ${opcionesRollo(primerColor)}
                    </select>
                </div>
                <div class="col-md-6 col-lg-4">
                    <label class="form-label">📏 Tipo de tubo</label>
                    <select name="tipoTuboManual[${idx}]" class="form-select form-select-sm select-tipo-tubo">
                        <option value="auto">⚡ Automático (según medida)</option>
                        <option value="R8">Tubo R8</option>
                        <option value="R16">Tubo R16</option>
                        <option value="R24">Tubo R24</option>
                    </select>
                </div>
                <div class="col-md-6 col-lg-4">
                    <label class="form-label">🎛 Tipo de control</label>
                    <select name="tipoControlManual[${idx}]" class="form-select form-select-sm select-tipo-control">
                        ${opcionesTipoControl()}
                    </select>
                </div>
                <div class="col-md-6 col-lg-4">
                    <label class="form-label">🔩 Pieza de tubo</label>
                    <select name="tuboManual[${idx}]" class="form-select form-select-sm select-tubo">
                        <option value="auto">⚡ Automático</option>
                    </select>
                </div>
                <div class="col-md-6 col-lg-4 campo-cabezal-manual" style="display:none;">
                    <label class="form-label">🪟 Pieza de cabezal</label>
                    <select name="cabezalManual[${idx}]" class="form-select form-select-sm select-cabezal">
                        <option value="auto">⚡ Automático</option>
                    </select>
                </div>
                <div class="col-md-6 col-lg-4">
                    <label class="form-label">⚖ Pieza de pesa</label>
                    <select name="pesaManual[${idx}]" class="form-select form-select-sm select-pesa">
                        <option value="auto">⚡ Automático</option>
                    </select>
                </div>
                <div class="col-md-6 col-lg-4">
                    <label class="form-label">🧶 Pieza de cuerda</label>
                    <select name="cuerdaManual[${idx}]" class="form-select form-select-sm select-cuerda">
                        <option value="auto">⚡ Automático</option>
                    </select>
                </div>
                <div class="col-md-6 col-lg-4 campo-pitillo-manual">
                    <label class="form-label">🪡 Pieza de pitillo</label>
                    <select name="pitilloManual[${idx}]" class="form-select form-select-sm select-pitillo">
                        <option value="auto">⚡ Automático</option>
                    </select>
                </div>
            </div>
        </div>
    </div>`;
}

function agregarFila() {
    const contenedor = document.getElementById('contenedor-productos');
    contenedor.insertAdjacentHTML('beforeend', htmlFila(filaIndex));
    const nueva = contenedor.lastElementChild;
    filaIndex++;
    engancharEventosFila(nueva);
    actualizarSelectsManualFila(nueva);
}

// ═══════════════════════════════════════════════════════════════════════
// VISTA PREVIA DE TODA LA ORDEN
// Se le pregunta al servidor por TODAS las líneas juntas (y cada unidad de
// cada línea), así se ve cómo se va rebajando el mismo tubo, rollo o pesa
// de un producto al siguiente. Es el mismo cálculo que se usa al guardar.
// ═══════════════════════════════════════════════════════════════════════

let temporizadorLote = null;
let consultaLoteActual = 0;

/** Se deja con este nombre porque otras partes del archivo lo llaman. */
function consultarPrevisualizacion() {
    programarPrevisualizacionLote();
}

function programarPrevisualizacionLote() {
    clearTimeout(temporizadorLote);
    temporizadorLote = setTimeout(consultarPrevisualizacionLote, 380);
}

function filasProducto() {
    return Array.from(document.querySelectorAll('#contenedor-productos .fila-producto'));
}

function valorManual(fila, selector) {
    const s = fila.querySelector(selector);
    return s && s.value && s.value !== 'auto' ? s.value : 'auto';
}

function datosFila(fila) {
    const desc = fila.querySelector('[name="descripciones"]');
    const cant = parseInt((fila.querySelector('.campo-cantidad') || {}).value, 10);
    return {
        descripcion: desc ? desc.value : '',
        cantidad: isNaN(cant) || cant < 1 ? 1 : cant,
        ancho: parseFloat(fila.querySelector('.campo-ancho').value) || 0,
        altura: parseFloat(fila.querySelector('.campo-alto').value) || 0,
        color: fila.querySelector('.campo-color').value,
        usaCabezal: fila.querySelector('.campo-cabezal').checked,
        usaPitilloPesa: fila.querySelector('.campo-pitillo').checked,
        usaConectorTope: fila.querySelector('.campo-conector').checked,
        tipoTuboManual: valorManual(fila, '.select-tipo-tubo'),
        tipoControlManual: valorManual(fila, '.select-tipo-control'),
        retazoManual: valorManual(fila, '.select-retazo'),
        rolloManual: valorManual(fila, '.select-rollo'),
        tuboManual: valorManual(fila, '.select-tubo'),
        cabezalManual: valorManual(fila, '.select-cabezal'),
        pesaManual: valorManual(fila, '.select-pesa'),
        cuerdaManual: valorManual(fila, '.select-cuerda'),
        pitilloManual: valorManual(fila, '.select-pitillo')
    };
}

async function consultarPrevisualizacionLote() {
    const filas = filasProducto();
    if (!filas.length) { pintarResumenLote(null); return; }

    const lineas = filas.map(datosFila);
    filas.forEach((fila, i) => {
        const caja = fila.querySelector('.previsualizacion');
        if (!lineas[i].ancho || !lineas[i].altura) {
            caja.className = 'previsualizacion mt-2';
            caja.innerHTML = '<span class="cargando">Completa ancho, alto y color para ver el material necesario.</span>';
        } else if (!caja.innerHTML.includes('<table')) {
            caja.className = 'previsualizacion mt-2';
            caja.innerHTML = '<span class="cargando">🔍 Consultando inventario...</span>';
        }
    });
    if (!lineas.some(l => l.ancho && l.altura)) { pintarResumenLote(null); return; }

    const miConsulta = ++consultaLoteActual;
    try {
        const params = new URLSearchParams();
        const token = localStorage.getItem('authToken');
        if (token) params.set('token', token);
        const resp = await fetch('/taller/previsualizar-lote' + (token ? '?' + params.toString() : ''), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(lineas)
        });
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const d = await resp.json();
        if (miConsulta !== consultaLoteActual) return; // llegó una respuesta vieja

        filas.forEach((fila, i) => {
            if (!lineas[i].ancho || !lineas[i].altura) return;
            pintarLinea(fila, d.lineas[i]);
        });
        pintarResumenLote(d);
    } catch (e) {
        console.error('Error consultando la vista previa de la orden:', e);
        filas.forEach((fila, i) => {
            if (!lineas[i].ancho || !lineas[i].altura) return;
            const caja = fila.querySelector('.previsualizacion');
            caja.className = 'previsualizacion mt-2 falta';
            caja.innerHTML = '<span>No se pudo consultar el inventario.</span>';
        });
    }
}

function esc(t) {
    return String(t == null ? '' : t).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function tablaPasos(pasos) {
    let html = '<table style="width:100%;border-collapse:collapse;margin-top:4px">';
    pasos.forEach(p => {
        let info = esc(p.texto);
        if (p.manual) info = '⭐ ' + info;
        if (p.descartado) info = '<span style="color:#8a6d00">' + info + '</span>';
        if (p.nota) info += '<br><small style="color:#8a6d00">⚠ ' + esc(p.nota) + '</small>';
        html += tr(esc(p.material), esc(p.medida), info);
    });
    return html + '</table>';
}

function pintarLinea(fila, linea) {
    const caja = fila.querySelector('.previsualizacion');
    if (!linea) return;
    const varias = linea.copias.length > 1;
    let html = linea.disponible
        ? '<strong>✓ Material disponible' + (varias ? ' para las ' + linea.copias.length + ' unidades' : '') + '</strong>'
        : '<strong>⚠ Falta material</strong>';

    linea.copias.forEach((c, j) => {
        const faltan = c.faltantes && c.faltantes.length
            ? '<div style="color:#842029;margin-top:4px">' + c.faltantes.map(esc).join('<br>') + '</div>' : '';
        if (varias) {
            const abierto = linea.copias.length <= 3 || !c.disponible ? ' open' : '';
            html += `<details${abierto} style="margin-top:6px;border-top:1px dashed #b9d9c4;padding-top:4px">
                <summary style="cursor:pointer;font-weight:600;font-size:0.82rem">${esc(c.etiqueta)} ${c.disponible ? '✓' : '⚠'}</summary>
                ${tablaPasos(c.pasos)}${faltan}</details>`;
        } else {
            html += tablaPasos(c.pasos) + faltan;
        }
    });
    caja.className = 'previsualizacion mt-2 ' + (linea.disponible ? 'ok' : 'falta');
    caja.innerHTML = html;
}

/** Recuadro debajo de los productos: lo que gasta TODA la orden, pieza por pieza. */
function pintarResumenLote(d) {
    let caja = document.getElementById('resumenLote');
    const contenedor = document.getElementById('contenedor-productos');
    if (!caja && contenedor) {
        caja = document.createElement('div');
        caja.id = 'resumenLote';
        caja.className = 'previsualizacion mt-2';
        contenedor.insertAdjacentElement('afterend', caja);
    }
    if (!caja) return;
    if (!d || !d.resumen || !d.resumen.length) { caja.style.display = 'none'; return; }
    caja.style.display = '';
    caja.className = 'previsualizacion mt-2 ' + (d.disponible ? 'ok' : 'falta');
    caja.innerHTML = '<strong>📦 Lo que gasta toda la orden</strong>'
        + '<div style="font-size:0.8rem;color:#555">Cada producto corta de lo que dejó el anterior; siempre se usa primero el pedazo más pequeño que alcance.</div>'
        + '<ul style="margin:6px 0 0;padding-left:18px;font-size:0.82rem">'
        + d.resumen.map(r => '<li>' + esc(r) + '</li>').join('') + '</ul>';
}

// ═══════════════════════════════════════════════════════════════════════
// ANTES DE ENVIAR: renumerar las filas
// Si se borra una fila del medio, los nombres como "cabezales[2]" quedan
// corridos y el servidor le aplicaría a un producto los datos de otro. Aquí
// se vuelven a numerar 0, 1, 2… en el orden en que se ven, y las casillas
// sin marcar se mandan como "false" (si no, el servidor las toma por marcadas).
// ═══════════════════════════════════════════════════════════════════════

function renumerarFilas(filas) {
    filas.forEach((fila, i) => {
        fila.querySelectorAll('[name]').forEach(el => {
            const m = el.name.match(/^([A-Za-z]+)\[\d+\]$/);
            if (m) el.name = m[1] + '[' + i + ']';
        });
    });
}

function mandarCasillasApagadas(filas, selectores) {
    filas.forEach(fila => {
        fila.querySelectorAll('input.falso-auto').forEach(x => x.remove());
        selectores.forEach(sel => {
            const chk = fila.querySelector(sel);
            if (chk && !chk.checked) {
                const oculto = document.createElement('input');
                oculto.type = 'hidden';
                oculto.className = 'falso-auto';
                oculto.name = chk.name;
                oculto.value = 'false';
                fila.appendChild(oculto);
            }
        });
    });
}

/** Extras: cada fila .fila-extra tiene su fila de info hermana con el mismo data-indice. */
function renumerarExtras(form) {
    const filas = Array.from(form.querySelectorAll('.fila-extra'));
    const infos = Array.from(form.querySelectorAll('.fila-extra-info'));
    const usadas = new Set();
    filas.forEach((fila, i) => {
        const viejo = fila.getAttribute('data-indice');
        const info = infos.find(x => x.getAttribute('data-indice') === viejo);
        [fila, info].forEach(bloque => {
            if (!bloque) return;
            bloque.querySelectorAll('[name]').forEach(el => {
                const m = el.name.match(/^([A-Za-z]+)\[\d+\]$/);
                if (m) el.name = m[1] + '[' + i + ']';
            });
        });
        if (info) usadas.add(info);
    });
    // Filas de info que quedaron huérfanas (se borró su fila): no se envían
    infos.forEach(info => {
        if (!usadas.has(info)) info.querySelectorAll('[name]').forEach(el => { el.disabled = true; });
    });
}

function prepararFormularioAntesDeEnviar(form) {
    if (!form) return;
    form.addEventListener('submit', () => {
        const productos = Array.from(form.querySelectorAll('.fila-producto'));
        if (productos.length) {
            renumerarFilas(productos);
            mandarCasillasApagadas(productos, ['.campo-cabezal', '.campo-pitillo', '.campo-conector']);
        }
        const rieles = Array.from(form.querySelectorAll('.fila-riel'));
        if (rieles.length) renumerarFilas(rieles);
        const telasVenta = Array.from(form.querySelectorAll('.fila-tela-venta'));
        if (telasVenta.length) renumerarFilas(telasVenta);
        renumerarExtras(form);
    });
}

function tr(mat, med, info) {
    return `<tr style="border-top:1px solid #d6efdd">
        <td style="padding:2px 4px;font-weight:600;white-space:nowrap">${mat}</td>
        <td style="padding:2px 6px;color:#555;white-space:nowrap">${med}</td>
        <td style="padding:2px 4px;color:#1a6b3a">${info}</td>
    </tr>`;
}

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
            <div class="col-md-5 campo-pieza-insumo" style="display:none;">                    <label class="form-label mb-1" style="font-size:0.76rem">🔩 ¿De qué pieza específica se corta?</label>
            <select name="extraPiezaId[${idx}]" class="form-select form-select-sm select-pieza-insumo">
                <option value="auto">⚡ Automático (sugerido)</option>
            </select>
        </div>
    </div>`;
}

function actualizarInfoExtra(select) {
    actualizarInfoInsumoVenta(select);
}

function toggleLibre(select) {
    const fila = select.closest('.fila-extra');
    const campoLibre = fila.querySelector('.campo-libre-extra');
    campoLibre.style.display = select.value === 'libre' ? '' : 'none';
}

function agregarFilaExtra() {
    const cont = document.getElementById('contenedorExtras');
    cont.insertAdjacentHTML('beforeend', filaExtraHtml(extraIndex++));
}

let idxTelaVenta = 0;
let idxInsumoVenta = 0;

function coloresDisponiblesVenta() {
    const colores = [...new Set(
        rollosDisponibles.filter(r => r.largoRestante > 0.001).map(r => r.color)
    )];
    return colores.sort();
}

function anchosDisponiblesVenta(color) {
    const anchos = [...new Set(
        rollosDisponibles
            .filter(r => r.color && r.color.toLowerCase() === (color || '').toLowerCase() && r.largoRestante > 0.001)
            .map(r => r.ancho)
    )];
    return anchos.sort((a, b) => a - b);
}

function opcionesRolloVenta(color, ancho) {
    const candidatos = rollosDisponibles.filter(r =>
        r.color && r.color.toLowerCase() === (color || '').toLowerCase() &&
        Math.abs(r.ancho - parseFloat(ancho)) < 0.001 &&
        r.largoRestante > 0.001
    );
    let html = '<option value="auto">⚡ Automático (el rollo con menos sobrante)</option>';
    candidatos.forEach(r => {
        html += `<option value="${r.id}">Rollo #${r.id} · ${r.largoRestante.toFixed(2)} m restantes</option>`;
    });
    return html;
}

function refrescarFilaTelaVenta(fila) {
    const color  = fila.querySelector('.select-color-venta').value;
    const anchos = anchosDisponiblesVenta(color);
    const selAncho = fila.querySelector('.select-ancho-venta');
    const anchoPrevio = selAncho.value;
    selAncho.innerHTML = anchos.length
        ? anchos.map(a => `<option value="${a}">${a.toFixed(2)} m</option>`).join('')
        : '<option value="">– Sin anchos disponibles –</option>';
    if (anchos.some(a => String(a) === anchoPrevio)) selAncho.value = anchoPrevio;

    const selRollo = fila.querySelector('.select-rollo-venta');
    selRollo.innerHTML = opcionesRolloVenta(color, selAncho.value);
}

function agregarFilaTelaVenta() {
    const i = idxTelaVenta++;
    const colores = coloresDisponiblesVenta();
    const opcionesColor = colores.length
        ? colores.map(c => `<option value="${c}">${c}</option>`).join('')
        : '<option value="">– Sin telas en inventario –</option>';

    const html = `
        <div class="row g-2 mb-2 align-items-center fila-tela-venta" data-indice="${i}">
            <div class="col-md-3">
                <select name="ventaTelaColor[${i}]" class="form-select form-select-sm select-color-venta">${opcionesColor}</select>
            </div>
            <div class="col-md-2">
                <select name="ventaTelaAncho[${i}]" class="form-select form-select-sm select-ancho-venta"></select>
            </div>
            <div class="col-md-2">
                <input type="number" step="0.01" min="0" name="ventaTelaMetros[${i}]" class="form-control form-control-sm" placeholder="Metros" required>
            </div>
            <div class="col-md-4">
                <select name="ventaTelaRolloId[${i}]" class="form-select form-select-sm select-rollo-venta"></select>
            </div>
            <div class="col-md-1">
                <button type="button" class="btn btn-sm btn-outline-danger" onclick="this.closest('.fila-tela-venta').remove()">✕</button>
            </div>
        </div>`;
    document.getElementById('contenedorTelaVenta').insertAdjacentHTML('beforeend', html);

    const fila = document.querySelector(`.fila-tela-venta[data-indice="${i}"]`);
    refrescarFilaTelaVenta(fila);
    fila.querySelector('.select-color-venta').addEventListener('change', () => refrescarFilaTelaVenta(fila));
    fila.querySelector('.select-ancho-venta').addEventListener('change', () => {
        fila.querySelector('.select-rollo-venta').innerHTML =
            opcionesRolloVenta(fila.querySelector('.select-color-venta').value, fila.querySelector('.select-ancho-venta').value);
    });
}

function filaInsumoVentaHtml(idx) {
    return `
    <div class="row g-2 align-items-center mb-1 fila-extra" data-indice="${idx}">
        <div class="col-md-5">
            <select name="extraInsumoId[${idx}]" class="form-select form-select-sm select-extra-insumo" onchange="actualizarInfoInsumoVenta(this)">
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

function actualizarInfoInsumoVenta(select) {
    const filaCampos = select.closest('.fila-extra');
    const filaInfo = filaCampos.nextElementSibling;
    const campoLibre = filaCampos.querySelector('.campo-libre-extra');
    const campoCantidad = filaCampos.querySelector('.campo-cantidad-insumo-venta');
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

function agregarFilaInsumoVenta() {
    const cont = document.getElementById('contenedorInsumoVenta');
    cont.insertAdjacentHTML('beforeend', filaInsumoVentaHtml(idxInsumoVenta++));
    const filaCampos = cont.querySelector(`.fila-extra[data-indice="${idxInsumoVenta - 1}"]`);
    const select = filaCampos.querySelector('.select-extra-insumo');
    actualizarInfoInsumoVenta(select);
}

let filaRielIndex = 1;
let extraIndexRiel = 0;

async function calcularRielPreviewFila(fila) {
    const caja = fila.querySelector('.previsualizacion-riel');
    const ancho = parseFloat(fila.querySelector('.campo-ancho-riel').value);
    const alto = parseFloat(fila.querySelector('.campo-alto-riel').value) || 0;
    const usaPolea = fila.querySelector('.campo-usa-polea-riel').checked;
    const selectApertura = fila.querySelector('.campo-lado-apertura-riel');
    const ladoApertura = selectApertura ? selectApertura.value : 'Izquierda';
    const haciaExtremos = ladoApertura === 'Hacia los extremos';
    const selectBaston = fila.querySelector('.campo-baston-riel select');
    const bastonElegido = selectBaston ? selectBaston.value : 'Bastón 0.80';

    if (!ancho || ancho <= 0) {
        caja.className = 'previsualizacion-riel previsualizacion';
        caja.innerHTML = '<span class="cargando">Completa el ancho para ver el corte estimado.</span>';
        return;
    }

    caja.className = 'previsualizacion-riel previsualizacion';
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
            caja.className = 'previsualizacion-riel previsualizacion ok';
            caja.innerHTML = html;
        } else {
            caja.className = 'previsualizacion-riel previsualizacion falta';
            caja.innerHTML = '<strong>⚠ Falta material:</strong><br>' + d.faltantes.join('<br>');
        }
    } catch (e) {
        console.error('Error consultando previsualización de riel:', e);
        caja.className = 'previsualizacion-riel previsualizacion falta';
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

function engancharEventosFilaRiel(fila) {
    const anchoInput = fila.querySelector('.campo-ancho-riel');
    const altoInput = fila.querySelector('.campo-alto-riel');
    const poleaCheck = fila.querySelector('.campo-usa-polea-riel');
    const filaBaston = fila.querySelector('.campo-baston-riel');
    const selectApertura = fila.querySelector('.campo-lado-apertura-riel');
    const selectBaston = fila.querySelector('.campo-baston-riel select');

    const disparar = () => {
        clearTimeout(fila._trRiel);
        fila._trRiel = setTimeout(() => calcularRielPreviewFila(fila), 420);
    };

    anchoInput.addEventListener('input', disparar);
    altoInput.addEventListener('input', disparar);
    poleaCheck.addEventListener('change', () => {
        disparar();
        filaBaston.style.display = poleaCheck.checked ? 'none' : '';
    });
    selectApertura.addEventListener('change', disparar);
    if (selectBaston) selectBaston.addEventListener('change', disparar);

    filaBaston.style.display = poleaCheck.checked ? 'none' : '';
}

function htmlFilaRiel(idx) {
    return `
    <div class="fila-riel" data-indice="${idx}">
        <div class="row align-items-end g-2 mb-1">
            <div class="col-md-3">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Descripción</label>
                <input type="text" name="descripciones" class="form-control form-control-sm" placeholder="Ej: riel sala principal">
            </div>
            <div class="col-md-1">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Cant.</label>
                <input type="number" name="cantidades" class="form-control form-control-sm campo-cantidad-riel" value="1" min="1" required>
            </div>
            <div class="col-md-2">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Ancho (m)</label>
                <input type="number" step="0.001" name="anchos" class="form-control form-control-sm campo-ancho-riel" required>
            </div>
            <div class="col-md-2">
                <label class="form-label fw-bold mb-1" style="font-size:0.82rem">Alto (m)</label>
                <input type="number" step="0.001" name="alturas" class="form-control form-control-sm campo-alto-riel" placeholder="opcional">
            </div>
            <div class="col-md-2 text-center">
                <label class="form-label fw-bold mb-1 d-block" style="font-size:0.82rem">Lleva polea</label>
                <input type="checkbox" name="usaPolea[${idx}]" value="true" class="form-check-input fs-5 campo-usa-polea-riel">
            </div>
            <div class="col-md-2 text-end">
                <button type="button" class="btn btn-outline-danger btn-sm mt-3"
                        onclick="this.closest('.fila-riel').remove()">✕</button>
            </div>
        </div>

        <div class="row g-2 mt-2">
            <div class="col-md-6 campo-baston-riel" style="display:none;">
                <label class="form-label">Bastón</label>
                <select name="bastonElegido[${idx}]" class="form-select form-select-sm">
                    <option value="Bastón 0.80">Bastón 0.80</option>
                    <option value="Bastón 1.20">Bastón 1.20</option>
                    <option value="Bastón 1.50">Bastón 1.50</option>
                </select>
            </div>
            <div class="col-md-6">
                <label class="form-label">Abre hacia</label>
                <select name="ladoApertura[${idx}]" class="form-select form-select-sm campo-lado-apertura-riel">
                    <option value="Izquierda">Izquierda</option>
                    <option value="Derecha">Derecha</option>
                    <option value="Hacia los extremos">Hacia los extremos</option>
                </select>
            </div>
        </div>

        <div class="previsualizacion-riel previsualizacion mt-2">
            <span class="cargando">Completa el ancho para ver el corte estimado.</span>
        </div>
    </div>`;
}

function agregarFilaRiel() {
    const contenedor = document.getElementById('contenedor-rieles');
    contenedor.insertAdjacentHTML('beforeend', htmlFilaRiel(filaRielIndex));
    const nueva = contenedor.lastElementChild;
    filaRielIndex++;
    engancharEventosFilaRiel(nueva);
}

function agregarFilaExtraRiel() {
    const cont = document.getElementById('contenedorExtrasRiel');
    const idx = extraIndexRiel++;
    cont.insertAdjacentHTML('beforeend', filaExtraHtml(idx));
    const filaCampos = cont.querySelector(`.fila-extra[data-indice="${idx}"]`);
    const select = filaCampos.querySelector('.select-extra-insumo');
    select.addEventListener('change', function () { toggleLibre(this); });
    actualizarInfoExtra(select);
}

document.addEventListener('DOMContentLoaded', () => {
    const fila0 = document.querySelector('.fila-producto[data-indice="0"]');
    if (fila0) {
        engancharEventosFila(fila0);
        actualizarSelectsManualFila(fila0);
    }

    // Si se agrega o se quita un producto, se recalcula toda la orden
    const contenedorProductos = document.getElementById('contenedor-productos');
    if (contenedorProductos) {
        new MutationObserver(programarPrevisualizacionLote).observe(contenedorProductos, { childList: true });
    }

    // Antes de enviar cada formulario se renumeran las filas
    document.querySelectorAll('form').forEach(prepararFormularioAntesDeEnviar);
    agregarFilaTelaVenta();

    const filaRiel0 = document.querySelector('.fila-riel[data-indice="0"]');
    if (filaRiel0) {
        engancharEventosFilaRiel(filaRiel0);
    }
});