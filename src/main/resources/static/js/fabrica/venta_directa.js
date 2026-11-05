/* Pantalla: templates/fabrica/nuevo_pedido.html → pestaña "💵 Venta Directa".
   Usa lo que deja la plantilla: rollosDisponibles, retazosDisponibles, piezasDisponibles,
   catalogoInsumos y preciosVenta (clave → valor guardado).
   Y de nuevo_pedido.js: esc(), tablaPasos(), renumerarFilas(), opcionesPieza().

   - Tela: se vende por ANCHO × ALTO. El alto va a lo ancho del rollo (igual que en las
     persianas); si el alto pasa de 3.00 m se corta girado.
   - Cada línea trae el precio estándar ya escrito. Si lo cambian, se marca, y al
     guardar queda como el nuevo estándar (si la casilla está marcada).
   - Mientras escriben, se consulta al servidor qué se corta de dónde y cómo queda el inventario. */

(function () {
    const form = document.getElementById('formVentaDirecta');
    if (!form) return;

    const PRECIOS = (typeof preciosVenta !== 'undefined' && preciosVenta) ? preciosVenta : {};
    const fmt = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
    const plata = n => '$' + fmt.format(Math.round(n || 0));
    const num = v => {
        const t = String(v == null ? '' : v).replace(',', '.').trim();
        const n = parseFloat(t);
        return isNaN(n) ? 0 : n;
    };
    const dos = n => (Math.round(n * 100) / 100).toFixed(2);

    let idxTela = 0;
    let idxInsumo = 0;

    // ═══ Precios estándar ═══

    function anchoRollo(alto) {
        if (alto <= 1.8301) return 1.83;
        if (alto <= 2.5001) return 2.50;
        return 3.00;
    }

    /** El alto que va a lo ancho del rollo (si no cabe en 3.00 se gira). */
    function altoEnRollo(ancho, alto) {
        return alto > 3.0001 && ancho <= 3.0001 ? ancho : alto;
    }

    function precioEstandar(clave) {
        const v = PRECIOS[clave];
        return v == null ? null : Number(v);
    }

    function claveTela(fila) {
        const ancho = num(fila.querySelector('.vt-ancho').value);
        const alto = num(fila.querySelector('.vt-alto').value);
        if (!alto) return null;
        return 'TELA_' + anchoRollo(altoEnRollo(ancho, alto)).toFixed(2);
    }

    /** Pone el precio estándar si el usuario no lo ha cambiado a mano. Muestra la ayuda. */
    function ponerPrecio(input, ayuda, clave, etiqueta) {
        const est = clave ? precioEstandar(clave) : null;
        input.dataset.estandar = est == null ? '' : String(est);
        input.dataset.sinClave = clave ? '' : '1';
        if (input.dataset.tocado !== '1') {
            input.value = est == null ? '' : String(est);
        }
        pintarAyudaPrecio(input, ayuda, etiqueta);
    }

    /** Cambia el color de la ayuda sin borrarle sus otras clases. */
    function marcar(ayuda, estado) {
        ayuda.classList.remove('sin', 'nuevo', 'cambiado');
        if (estado) ayuda.classList.add(estado);
    }

    function pintarAyudaPrecio(input, ayuda, etiqueta) {
        if (!ayuda) return;
        // Línea "otro" (no se guarda precio) o tela sin medidas todavía: sin ayuda
        if (input.dataset.libre === '1' || input.dataset.sinClave === '1') {
            marcar(ayuda, '');
            ayuda.textContent = '';
            return;
        }
        const est = input.dataset.estandar === '' ? null : Number(input.dataset.estandar);
        const val = input.value.trim() === '' ? null : num(input.value);
        const guardar = document.getElementById('chkGuardarPrecios');
        if (est == null && val == null) {
            marcar(ayuda, 'sin');
            ayuda.textContent = 'Sin precio estándar' + (etiqueta ? ' para ' + etiqueta : '') + ': escríbelo y quedará guardado.';
        } else if (est == null) {
            marcar(ayuda, 'nuevo');
            ayuda.textContent = '💾 Precio nuevo' + (guardar && guardar.checked ? ': quedará como estándar.' : '.');
        } else if (val != null && Math.abs(val - est) > 0.004) {
            marcar(ayuda, 'cambiado');
            ayuda.textContent = '✏ Cambiado (estándar ' + plata(est) + ')' + (guardar && guardar.checked ? ': quedará como nuevo estándar.' : '.');
        } else {
            marcar(ayuda, '');
            ayuda.textContent = 'Precio estándar' + (etiqueta ? ' · ' + etiqueta : '');
        }
    }

    // ═══ Tela ═══

    function coloresConTela() {
        const set = new Set();
        rollosDisponibles.forEach(r => { if (r.color && r.largoRestante > 0.001) set.add(r.color); });
        retazosDisponibles.forEach(r => { if (r.color) set.add(r.color); });
        return [...set].sort();
    }

    function opcionesFuente(color, ancho, alto) {
        const c = (color || '').toLowerCase();
        let html = '<option value="auto">⚡ Automático (retazo si hay, si no el rollo más gastado)</option>';
        if (!ancho || !alto) return html;
        const enRollo = altoEnRollo(ancho, alto);
        const largo = enRollo === alto ? ancho : alto;

        const retazos = retazosDisponibles.filter(r => r.color && r.color.toLowerCase() === c &&
            ((r.ancho >= ancho - 0.001 && r.alto >= alto - 0.001) || (r.ancho >= alto - 0.001 && r.alto >= ancho - 0.001)));
        if (retazos.length) {
            html += '<optgroup label="✂ Retazos que sirven">';
            retazos.forEach(r => { html += `<option value="retazo-${r.id}">Retazo #${r.id} · ${dos(r.ancho)} × ${dos(r.alto)} m</option>`; });
            html += '</optgroup>';
        }
        const rollos = rollosDisponibles.filter(r => r.color && r.color.toLowerCase() === c &&
            r.ancho >= enRollo - 0.001 && r.largoRestante >= largo - 0.001);
        if (rollos.length) {
            html += '<optgroup label="🧵 Rollos que alcanzan">';
            rollos.forEach(r => { html += `<option value="rollo-${r.id}">Rollo #${r.id} · ${dos(r.ancho)} m de ancho · quedan ${dos(r.largoRestante)} m</option>`; });
            html += '</optgroup>';
        }
        return html;
    }

    function refrescarTela(fila) {
        const color = fila.querySelector('.vt-color').value;
        const ancho = num(fila.querySelector('.vt-ancho').value);
        const alto = num(fila.querySelector('.vt-alto').value);

        const sel = fila.querySelector('.vt-fuente');
        const antes = sel.value;
        sel.innerHTML = opcionesFuente(color, ancho, alto);
        if ([...sel.options].some(o => o.value === antes)) sel.value = antes;

        const m2 = ancho * alto;
        fila.querySelector('.vt-m2').textContent = m2 > 0 ? dos(m2) + ' m²' : '—';

        const info = fila.querySelector('.vt-info');
        if (ancho && alto) {
            const enRollo = altoEnRollo(ancho, alto);
            const rollo = anchoRollo(enRollo);
            const girado = enRollo !== alto;
            info.textContent = (alto > 3.0001 && ancho > 3.0001)
                ? '⚠ No cabe en ningún rollo (el más ancho es de 3.00 m).'
                : 'Va en rollo de ' + rollo.toFixed(2) + ' m' + (girado ? ' (girado: el alto pasa de 3.00 m)' : '')
                  + ' · se descuentan ' + dos(girado ? alto : ancho) + ' m de largo.';
        } else {
            info.textContent = 'Escribe ancho y alto en metros.';
        }

        const clave = claveTela(fila);
        ponerPrecio(fila.querySelector('.vt-precio'), fila.querySelector('.vt-ayuda'), clave,
            clave ? 'tela en rollo de ' + clave.replace('TELA_', '') + ' m (por m²)' : '');
        totalizar();
    }

    function agregarFilaTela() {
        const i = idxTela++;
        const colores = coloresConTela();
        const opciones = colores.length
            ? colores.map(c => `<option value="${esc(c)}">${esc(c)}</option>`).join('')
            : '<option value="">– Sin telas en inventario –</option>';
        const html = `
        <div class="fila-tela-venta linea-venta" data-indice="${i}">
            <div class="row g-2 align-items-end">
                <div class="col-6 col-md-2">
                    <label class="form-label mb-0 small">Color</label>
                    <select name="ventaTelaColor[${i}]" class="form-select form-select-sm vt-color">${opciones}</select>
                </div>
                <div class="col-3 col-md-1">
                    <label class="form-label mb-0 small">Ancho (m)</label>
                    <input type="number" step="0.01" min="0" name="ventaTelaAnchoCorte[${i}]" class="form-control form-control-sm vt-ancho" placeholder="1.50">
                </div>
                <div class="col-3 col-md-1">
                    <label class="form-label mb-0 small">Alto (m)</label>
                    <input type="number" step="0.01" min="0" name="ventaTelaAltoCorte[${i}]" class="form-control form-control-sm vt-alto" placeholder="2.00">
                </div>
                <div class="col-3 col-md-1 text-center">
                    <label class="form-label mb-0 small">Área</label>
                    <div class="vt-m2 fw-semibold small pt-1">—</div>
                </div>
                <div class="col-9 col-md-3">
                    <label class="form-label mb-0 small">¿De dónde se corta?</label>
                    <select name="ventaTelaFuente[${i}]" class="form-select form-select-sm vt-fuente"></select>
                </div>
                <div class="col-6 col-md-2">
                    <label class="form-label mb-0 small">Precio m²</label>
                    <div class="input-group input-group-sm">
                        <span class="input-group-text">$</span>
                        <input type="number" step="1" min="0" name="ventaTelaPrecioMetro[${i}]" class="form-control vt-precio">
                    </div>
                </div>
                <div class="col-4 col-md-1 text-end">
                    <label class="form-label mb-0 small">Subtotal</label>
                    <div class="subtotal-linea fw-bold small pt-1">$0</div>
                </div>
                <div class="col-2 col-md-1 text-end">
                    <button type="button" class="btn btn-sm btn-outline-danger btn-quitar" title="Quitar">✕</button>
                </div>
            </div>
            <div class="d-flex flex-wrap gap-3 mt-1">
                <small class="vt-info text-muted"></small>
                <small class="vt-ayuda ayuda-precio"></small>
            </div>
        </div>`;
        document.getElementById('contenedorTelaVenta').insertAdjacentHTML('beforeend', html);
        const fila = document.querySelector(`#contenedorTelaVenta .fila-tela-venta[data-indice="${i}"]`);
        fila.querySelectorAll('.vt-color, .vt-ancho, .vt-alto').forEach(el => {
            el.addEventListener('input', () => refrescarTela(fila));
            el.addEventListener('change', () => refrescarTela(fila));
        });
        engancharPrecio(fila.querySelector('.vt-precio'), fila.querySelector('.vt-ayuda'));
        fila.querySelector('.btn-quitar').addEventListener('click', () => { fila.remove(); totalizar(); programarPrevia(); });
        refrescarTela(fila);
    }

    // ═══ Insumos ═══

    function opcionesInsumos() {
        let html = '<option value="">– Escoge el insumo –</option>';
        catalogoInsumos.forEach(ins => {
            html += `<option value="${ins.id}">${esc(ins.nombre)}${ins.tieneMedida ? ' (por metro)' : ' (por unidad)'}</option>`;
        });
        return html + '<option value="libre">✏ Otro (fuera del catálogo, no descuenta)</option>';
    }

    function refrescarInsumo(fila) {
        const sel = fila.querySelector('.vi-insumo');
        const libre = fila.querySelector('.vi-libre');
        const cant = fila.querySelector('.vi-cantidad');
        const info = fila.querySelector('.vi-info');
        const cajaPieza = fila.querySelector('.vi-caja-pieza');
        const selPieza = fila.querySelector('.vi-pieza');
        const precio = fila.querySelector('.vi-precio');
        const etiquetaPrecio = fila.querySelector('.vi-etq-precio');

        libre.style.display = sel.value === 'libre' ? '' : 'none';
        cajaPieza.style.display = 'none';

        if (sel.value === 'libre') {
            cant.step = '1';
            cant.placeholder = 'Cantidad';
            etiquetaPrecio.textContent = 'Precio c/u';
            info.className = 'vi-info info-stock-insumo';
            info.textContent = '✏ Fuera del catálogo: no se descuenta del inventario, solo queda en la venta.';
            precio.dataset.estandar = '';
            precio.dataset.libre = '1';
            pintarAyudaPrecio(precio, fila.querySelector('.vi-ayuda'), '');
            totalizar();
            return;
        }
        precio.dataset.libre = '';
        const insumo = catalogoInsumos.find(x => String(x.id) === String(sel.value));
        if (!insumo) {
            info.textContent = '';
            fila.querySelector('.vi-ayuda').textContent = '';
            totalizar();
            return;
        }

        if (insumo.tieneMedida) {
            const piezas = piezasDisponibles.filter(p => {
                const n = p.insumoNombre || (p.insumo && p.insumo.nombre) || '';
                return n.toLowerCase() === insumo.nombre.toLowerCase() && p.largoRestante > 0.001;
            });
            const total = piezas.reduce((s, p) => s + p.largoRestante, 0);
            const mayor = piezas.reduce((m, p) => Math.max(m, p.largoRestante), 0);
            cant.step = '0.01';
            cant.placeholder = 'Metros';
            etiquetaPrecio.textContent = 'Precio metro';
            cajaPieza.style.display = '';
            const antes = selPieza.value;
            selPieza.innerHTML = opcionesPieza(insumo.nombre);
            if ([...selPieza.options].some(o => o.value === antes)) selPieza.value = antes;
            const pide = num(cant.value);
            if (!piezas.length) {
                info.className = 'vi-info info-stock-insumo sin-stock';
                info.textContent = '⚠ No hay piezas de "' + insumo.nombre + '".';
            } else {
                info.className = 'vi-info info-stock-insumo ' + (pide > total + 0.001 ? 'sin-stock' : 'ok');
                info.textContent = '📏 Hay ' + dos(total) + ' m en ' + piezas.length + ' pieza(s); la más larga tiene ' + dos(mayor) + ' m.'
                    + (pide > mayor + 0.001 && pide <= total + 0.001 ? ' Ninguna alcanza sola: se tomará de varias.' : '')
                    + (pide > total + 0.001 ? ' ⚠ No alcanza.' : '');
            }
        } else {
            const stock = insumo.stockUnidades != null ? insumo.stockUnidades : 0;
            cant.step = '1';
            cant.placeholder = 'Unidades';
            etiquetaPrecio.textContent = 'Precio c/u';
            const pide = num(cant.value);
            info.className = 'vi-info info-stock-insumo ' + (pide > stock || stock <= 0 ? 'sin-stock' : 'ok');
            info.textContent = '🔢 Hay ' + stock + ' unidad(es).' + (pide > stock ? ' ⚠ No alcanza.' : '');
        }
        ponerPrecio(precio, fila.querySelector('.vi-ayuda'), 'INSUMO_' + insumo.id,
            insumo.nombre + (insumo.tieneMedida ? ' (por metro)' : ' (por unidad)'));
        totalizar();
    }

    function agregarFilaInsumo() {
        const i = idxInsumo++;
        const html = `
        <div class="fila-insumo-venta linea-venta" data-indice="${i}">
            <div class="row g-2 align-items-end">
                <div class="col-12 col-md-3">
                    <label class="form-label mb-0 small">Insumo</label>
                    <select name="ventaInsumoId[${i}]" class="form-select form-select-sm vi-insumo">${opcionesInsumos()}</select>
                </div>
                <div class="col-12 col-md-2 vi-libre" style="display:none">
                    <label class="form-label mb-0 small">¿Qué es?</label>
                    <input type="text" name="ventaInsumoNombreLibre[${i}]" class="form-control form-control-sm" placeholder="Nombre">
                </div>
                <div class="col-4 col-md-1">
                    <label class="form-label mb-0 small">Cantidad</label>
                    <input type="number" step="0.01" min="0" name="ventaInsumoCantidad[${i}]" class="form-control form-control-sm vi-cantidad" placeholder="Cantidad">
                </div>
                <div class="col-8 col-md-2">
                    <label class="form-label mb-0 small vi-etq-precio">Precio</label>
                    <div class="input-group input-group-sm">
                        <span class="input-group-text">$</span>
                        <input type="number" step="1" min="0" name="ventaInsumoPrecio[${i}]" class="form-control vi-precio">
                    </div>
                </div>
                <div class="col-8 col-md-3 vi-caja-pieza" style="display:none">
                    <label class="form-label mb-0 small">🔩 ¿De qué pieza?</label>
                    <select name="ventaInsumoPieza[${i}]" class="form-select form-select-sm vi-pieza"><option value="auto">⚡ Automático</option></select>
                </div>
                <div class="col-2 col-md-1 text-end">
                    <label class="form-label mb-0 small">Subtotal</label>
                    <div class="subtotal-linea fw-bold small pt-1">$0</div>
                </div>
                <div class="col-2 col-md-1 text-end">
                    <button type="button" class="btn btn-sm btn-outline-danger btn-quitar" title="Quitar">✕</button>
                </div>
            </div>
            <div class="d-flex flex-wrap gap-3 mt-1">
                <small class="vi-info info-stock-insumo"></small>
                <small class="vi-ayuda ayuda-precio"></small>
            </div>
        </div>`;
        document.getElementById('contenedorInsumoVenta').insertAdjacentHTML('beforeend', html);
        const fila = document.querySelector(`#contenedorInsumoVenta .fila-insumo-venta[data-indice="${i}"]`);
        const sel = fila.querySelector('.vi-insumo');
        sel.addEventListener('change', () => {
            // Al cambiar de insumo, el precio vuelve a ser el estándar del nuevo
            fila.querySelector('.vi-precio').dataset.tocado = '';
            refrescarInsumo(fila);
        });
        fila.querySelector('.vi-cantidad').addEventListener('input', () => refrescarInsumo(fila));
        engancharPrecio(fila.querySelector('.vi-precio'), fila.querySelector('.vi-ayuda'));
        fila.querySelector('.btn-quitar').addEventListener('click', () => { fila.remove(); totalizar(); programarPrevia(); });
        refrescarInsumo(fila);
    }

    // ═══ Precio y total ═══

    function engancharPrecio(input, ayuda) {
        input.addEventListener('input', () => {
            input.dataset.tocado = '1';
            pintarAyudaPrecio(input, ayuda, '');
            totalizar();
        });
    }

    function subtotal(fila) {
        const precio = num(fila.querySelector('.vt-precio, .vi-precio').value);
        if (fila.classList.contains('fila-tela-venta')) {
            return Math.round(num(fila.querySelector('.vt-ancho').value) * num(fila.querySelector('.vt-alto').value) * 1000) / 1000 * precio;
        }
        return num(fila.querySelector('.vi-cantidad').value) * precio;
    }

    function totalizar() {
        let total = 0;
        form.querySelectorAll('.linea-venta').forEach(fila => {
            const s = Math.round(subtotal(fila));
            fila.querySelector('.subtotal-linea').textContent = plata(s);
            total += s;
        });
        document.getElementById('totalVentaDirecta').textContent = plata(total);
    }

    // ═══ Vista previa del inventario ═══

    let temporizador = null;
    let consulta = 0;

    function programarPrevia() {
        clearTimeout(temporizador);
        temporizador = setTimeout(consultarPrevia, 450);
    }

    function renumerar() {
        renumerarFilas(Array.from(form.querySelectorAll('.fila-tela-venta')));
        renumerarFilas(Array.from(form.querySelectorAll('.fila-insumo-venta')));
    }

    async function consultarPrevia() {
        const caja = document.getElementById('previaVenta');
        renumerar();
        const hayTela = [...form.querySelectorAll('.fila-tela-venta')].some(f => num(f.querySelector('.vt-ancho').value) > 0 && num(f.querySelector('.vt-alto').value) > 0);
        const hayInsumo = [...form.querySelectorAll('.fila-insumo-venta')].some(f => {
            const v = f.querySelector('.vi-insumo').value;
            return v && v !== 'libre' && num(f.querySelector('.vi-cantidad').value) > 0;
        });
        if (!hayTela && !hayInsumo) { caja.style.display = 'none'; return; }

        const datos = new URLSearchParams(new FormData(form));
        const token = localStorage.getItem('authToken');
        const mia = ++consulta;
        caja.style.display = '';
        caja.className = 'previsualizacion mt-3';
        caja.innerHTML = '<span class="cargando">🔍 Revisando el inventario...</span>';
        try {
            const resp = await fetch('/taller/previsualizar-venta-directa' + (token ? '?token=' + encodeURIComponent(token) : ''), {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: datos.toString()
            });
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const d = await resp.json();
            if (mia !== consulta) return;
            pintarPrevia(caja, d);
        } catch (e) {
            if (mia !== consulta) return;
            caja.className = 'previsualizacion mt-3 falta';
            caja.textContent = 'No se pudo revisar el inventario ahora. Igual al guardar se revisa todo.';
        }
    }

    function pintarPrevia(caja, d) {
        let html = d.disponible
            ? '<strong>✓ Hay material para toda la venta</strong>'
            : '<strong>⚠ Falta material: así no se puede guardar</strong>';
        (d.lineas || []).forEach(l => {
            html += `<div class="mt-2"><div class="fw-semibold small">${l.disponible ? '✓' : '⚠'} ${esc(l.titulo)}</div>`;
            if (l.pasos && l.pasos.length) html += tablaPasos(l.pasos);
            if (l.faltantes && l.faltantes.length) {
                html += '<div style="color:#842029;font-size:0.82rem">' + l.faltantes.map(esc).join('<br>') + '</div>';
            }
            html += '</div>';
        });
        if (d.resumen && d.resumen.length) {
            html += '<div class="mt-2 pt-2" style="border-top:1px dashed #b9d9c4"><strong>📦 Así queda el inventario</strong>'
                + '<ul style="margin:4px 0 0;padding-left:18px;font-size:0.82rem">'
                + d.resumen.map(r => '<li>' + esc(r) + '</li>').join('') + '</ul></div>';
        }
        caja.className = 'previsualizacion mt-3 ' + (d.disponible ? 'ok' : 'falta');
        caja.innerHTML = html;
    }

    // ═══ Arranque ═══

    document.getElementById('btnAgregarTelaVenta').addEventListener('click', agregarFilaTela);
    document.getElementById('btnAgregarInsumoVenta').addEventListener('click', agregarFilaInsumo);
    form.addEventListener('input', programarPrevia);
    form.addEventListener('change', programarPrevia);
    const chk = document.getElementById('chkGuardarPrecios');
    if (chk) chk.addEventListener('change', () => {
        form.querySelectorAll('.linea-venta').forEach(f => {
            const input = f.querySelector('.vt-precio, .vi-precio');
            pintarAyudaPrecio(input, f.querySelector('.vt-ayuda, .vi-ayuda'), '');
        });
    });
    form.addEventListener('submit', e => {
        renumerar();
        if (!form.querySelector('.linea-venta')) {
            e.preventDefault();
            const caja = document.getElementById('previaVenta');
            caja.style.display = '';
            caja.className = 'previsualizacion mt-3 falta';
            caja.textContent = 'Agrega al menos una tela o un insumo.';
        }
    });

    agregarFilaTela();
})();