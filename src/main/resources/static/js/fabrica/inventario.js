/* Pantalla: templates/fabrica/inventario.html */

const MAPA_COLORES = {
    'blanco': '#ffffff', 'negro': '#1a1a1a', 'rojo': '#d32f2f', 'azul': '#1976d2',
    'verde': '#388e3c', 'amarillo': '#fbc02d', 'naranja': '#f57c00', 'morado': '#7b1fa2',
    'rosado': '#ec407a', 'rosa': '#ec407a', 'gris': '#9e9e9e', 'beige': '#e8dcc8',
    'café': '#6d4c41', 'marron': '#6d4c41', 'celeste': '#4fc3f7', 'turquesa': '#26a69a',
    'crema': '#fff3d6', 'vino': '#7b1e1e', 'dorado': '#cda434', 'plateado': '#c0c0c0'
};
function colorSwatch(nombreColor) {
    const key = (nombreColor || '').toLowerCase().trim();
    return MAPA_COLORES[key] || '#cccccc';
}
function escapeHtml(str) {
    return (str ?? '').toString()
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function calcularAlturaGrupo(body) {
    body.style.maxHeight = (body.scrollHeight + 2) + 'px';
}

function engancharAcordeon(header, body, abiertoPorDefecto) {
    if (abiertoPorDefecto) {
        header.classList.add('abierto');
        body.classList.add('abierto');
        calcularAlturaGrupo(body);
    }
    header.addEventListener('click', () => {
        const seVaAAbrir = !body.classList.contains('abierto');
        header.classList.toggle('abierto');
        body.classList.toggle('abierto');
        if (seVaAAbrir) {
            calcularAlturaGrupo(body);
        } else {
            body.style.maxHeight = '0px';
        }
    });
}

(function initPanelAlertas() {
    const header = document.getElementById('panelAlertasHeader');
    const body = document.getElementById('panelAlertasBody');
    if (header && body) {
        engancharAcordeon(header, body, true);
    }

    const DIAS_SILENCIO = 7;
    const MS_SILENCIO = DIAS_SILENCIO * 24 * 60 * 60 * 1000;
    const PREFIJO_STORAGE = 'inventario_alerta_silenciada_';

    function claveStorage(key) {
        let hash = 0;
        for (let i = 0; i < key.length; i++) {
            hash = ((hash << 5) - hash + key.charCodeAt(i)) | 0;
        }
        return PREFIJO_STORAGE + hash;
    }

    function estaSilenciada(key) {
        try {
            const raw = localStorage.getItem(claveStorage(key));
            if (!raw) return false;
            const ts = parseInt(raw, 10);
            if (isNaN(ts)) return false;
            if (Date.now() - ts >= MS_SILENCIO) {
                localStorage.removeItem(claveStorage(key));
                return false;
            }
            return true;
        } catch (e) {
            return false;
        }
    }

    function silenciar(key) {
        try { localStorage.setItem(claveStorage(key), String(Date.now())); } catch (e) {}
    }

    function quitarTodosLosSilencios(keys) {
        keys.forEach(k => {
            try { localStorage.removeItem(claveStorage(k)); } catch (e) {}
        });
    }

    const lista = document.getElementById('listaAlertas');
    const todosLosItems = lista ? Array.from(lista.querySelectorAll('.item-alerta')) : [];
    const sinAlertasMensaje = document.getElementById('sinAlertasMensaje');
    const badgeConteo = document.getElementById('badgeConteoAlertas');
    const infoSilenciadas = document.getElementById('alertasSilenciadasInfo');
    const textoSilenciadas = document.getElementById('alertasSilenciadasTexto');
    const btnMostrarSilenciadas = document.getElementById('btnMostrarSilenciadas');
    const panelBody = document.getElementById('panelAlertasBody');

    function actualizarVisibilidad() {
        let visibles = 0;
        let silenciadas = 0;
        const keysSilenciadas = [];

        todosLosItems.forEach(li => {
            const key = li.dataset.alertaKey || '';
            if (estaSilenciada(key)) {
                li.classList.add('oculto-por-filtro');
                silenciadas++;
                keysSilenciadas.push(key);
            } else {
                li.classList.remove('oculto-por-filtro');
                visibles++;
            }
        });

        if (badgeConteo) {
            if (visibles > 0) {
                badgeConteo.textContent = visibles;
                badgeConteo.style.display = '';
            } else {
                badgeConteo.style.display = 'none';
            }
        }

        if (sinAlertasMensaje) {
            sinAlertasMensaje.style.display = (visibles === 0 && silenciadas === 0) ? '' : 'none';
        }

        if (infoSilenciadas) {
            if (silenciadas > 0) {
                infoSilenciadas.style.display = '';
                textoSilenciadas.textContent = silenciadas === 1
                    ? '1 alerta silenciada —'
                    : `${silenciadas} alertas silenciadas —`;
                infoSilenciadas.dataset.keys = JSON.stringify(keysSilenciadas);
            } else {
                infoSilenciadas.style.display = 'none';
            }
        }

        if (panelBody && panelBody.classList.contains('abierto')) {
            calcularAlturaGrupo(panelBody);
        }
    }

    todosLosItems.forEach(li => {
        const btn = li.querySelector('.btn-silenciar-alerta');
        if (btn) {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const key = li.dataset.alertaKey || '';
                silenciar(key);
                actualizarVisibilidad();
            });
        }

        li.addEventListener('click', (e) => {
            if (e.target.closest('.btn-silenciar-alerta')) return;
            const categoria = li.dataset.categoria || '';
            const titulo = li.dataset.titulo || '';
            irAMaterialDesdeAlerta(categoria, titulo);
        });
    });

    function irAMaterialDesdeAlerta(categoria, titulo) {
        if (categoria === 'TELA') {
            new bootstrap.Tab(document.getElementById('tab-btn-rollos')).show();
            document.getElementById('filtroRolloColor').value = '';
            const partes = titulo.replace('Tela ', '').split(' ');
            const colorPosible = partes.slice(0, -1).join(' ');
            const selColor = document.getElementById('filtroRolloColor');
            if (Array.from(selColor.options).some(o => o.value === colorPosible)) {
                selColor.value = colorPosible;
            }
            selColor.dispatchEvent(new Event('change'));
        } else {
            new bootstrap.Tab(document.getElementById('tab-btn-insumos')).show();
            document.getElementById('filtroInsumoNombre').value = titulo;
            document.getElementById('filtroInsumoNombre').dispatchEvent(new Event('input'));
        }
    }

    if (btnMostrarSilenciadas) {
        btnMostrarSilenciadas.addEventListener('click', (e) => {
            e.preventDefault();
            const keys = JSON.parse(infoSilenciadas.dataset.keys || '[]');
            quitarTodosLosSilencios(keys);
            actualizarVisibilidad();
        });
    }

    actualizarVisibilidad();
})();

(function initPanelHistorial() {
    const header = document.getElementById('panelHistorialHeader');
    const body = document.getElementById('panelHistorialBody');
    if (header && body) {
        engancharAcordeon(header, body, false);
    }
})();

(function initRollos() {
    const filas = Array.from(document.querySelectorAll('#datosRollosRaw tbody tr'));
    const contenedor = document.getElementById('gruposRollosContainer');
    if (!contenedor) return;

    const rollos = filas.map(tr => ({
        id: tr.dataset.id,
        color: tr.dataset.color,
        ancho: tr.dataset.ancho,
        agotado: tr.dataset.agotado === 'true',
        retazo: tr.dataset.retazo === 'true',
        area: tr.dataset.area,
        largoRestante: tr.dataset.largoRestante,
        largoInicial: tr.dataset.largoInicial,
        fecha: tr.dataset.fecha,
        urlEliminar: tr.dataset.urlEliminar,
        urlCorregir: tr.dataset.urlCorregir
    }));

    const selAncho = document.getElementById('filtroRolloAncho');
    const anchosUnicos = Array.from(new Set(rollos.map(r => r.ancho))).sort((a, b) => parseFloat(a) - parseFloat(b));
    anchosUnicos.forEach(a => {
        const opt = document.createElement('option');
        opt.value = a;
        opt.textContent = a + ' m';
        selAncho.appendChild(opt);
    });

    const grupos = {};
    rollos.forEach(r => {
        const key = r.color + '||' + r.ancho;
        if (!grupos[key]) grupos[key] = { color: r.color, ancho: r.ancho, items: [] };
        grupos[key].items.push(r);
    });

    const clavesOrdenadas = Object.keys(grupos).sort((a, b) => {
        const ga = grupos[a], gb = grupos[b];
        return ga.color.localeCompare(gb.color) || (parseFloat(ga.ancho) - parseFloat(gb.ancho));
    });

    function estadoDe(r) {
        if (r.agotado) return 'agotado';
        if (r.retazo) return 'retazo';
        return 'disponible';
    }

    function render() {
        const fColor = document.getElementById('filtroRolloColor').value;
        const fAncho = document.getElementById('filtroRolloAncho').value;
        const fEstado = document.getElementById('filtroRolloEstado').value;

        contenedor.innerHTML = '';
        let totalVisible = 0;
        let grupoCount = 0;

        clavesOrdenadas.forEach(key => {
            const g = grupos[key];
            if (fColor && g.color !== fColor) return;
            if (fAncho && g.ancho !== fAncho) return;

            const itemsFiltrados = g.items.filter(r => !fEstado || estadoDe(r) === fEstado);
            if (itemsFiltrados.length === 0) return;

            grupoCount++;
            totalVisible += itemsFiltrados.length;

            const disponibles = itemsFiltrados.filter(r => estadoDe(r) === 'disponible').length;
            const retazos = itemsFiltrados.filter(r => estadoDe(r) === 'retazo').length;
            const agotados = itemsFiltrados.filter(r => estadoDe(r) === 'agotado').length;
            const areaTotal = itemsFiltrados.reduce((sum, r) => sum + (parseFloat(r.area.replace(',', '.')) || 0), 0);

            const div = document.createElement('div');
            div.className = 'grupo-rollo';

            const badges = [];
            if (disponibles) badges.push(`<span class="badge bg-success">${disponibles} disponible(s)</span>`);
            if (retazos) badges.push(`<span class="badge badge-retazo">${retazos} retazo(s)</span>`);
            if (agotados) badges.push(`<span class="badge bg-danger">${agotados} agotado(s)</span>`);

            let filasHtml = '';
            itemsFiltrados.forEach(r => {
                const estClase = r.agotado ? 'fila-agotado' : (r.retazo ? 'fila-retazo' : '');
                let estBadge = '<span class="badge bg-success">Disponible</span>';
                if (r.agotado) estBadge = '<span class="badge bg-danger">Agotado</span>';
                else if (r.retazo) estBadge = '<span class="badge badge-retazo">Retazo</span>';

                filasHtml += `
                    <tr class="${estClase}">
                        <td><span class="id-pill">#${escapeHtml(r.id)}</span></td>
                        <td>${escapeHtml(r.area)} m²<br><small class="text-muted">${escapeHtml(r.largoRestante)} / ${escapeHtml(r.largoInicial)} m lineales</small></td>
                        <td>${estBadge}</td>
                        <td><small>${escapeHtml(r.fecha)}</small></td>
                        <td class="d-flex gap-1 flex-wrap">
                            <form action="${escapeHtml(r.urlCorregir)}" method="post" class="d-flex gap-1 align-items-center">
                                <input type="number" step="0.01" min="0" max="${escapeHtml(r.largoInicial)}"
                                       name="largoRestante" value="${escapeHtml(r.largoRestante)}"
                                       class="form-control form-control-sm" style="width:80px;" required>
                                <button type="submit" class="btn btn-sm btn-outline-primary" title="Corregir largo restante">💾</button>
                                <button type="submit" class="btn btn-sm btn-outline-warning btn-agotar-rollo" title="Marcar agotado (0 m)">⛔</button>
                            </form>
                            <form action="${escapeHtml(r.urlEliminar)}" method="post" class="d-inline"
                                  onsubmit="return confirm('¿Eliminar este rollo del inventario?');">
                                <button type="submit" class="btn btn-sm btn-outline-danger">🗑️</button>
                            </form>
                        </td>
                    </tr>`;
            });

            div.innerHTML = `
                <div class="grupo-header" role="button">
                    <div class="grupo-titulo">
                        <span class="chevron">▶</span>
                        <span class="swatch-color" style="background:${colorSwatch(g.color)};"></span>
                        <span>${escapeHtml(g.color)} — ${escapeHtml(g.ancho)} m de ancho</span>
                        <span class="grupo-meta">(${itemsFiltrados.length} rollo${itemsFiltrados.length !== 1 ? 's' : ''} · ${areaTotal.toFixed(2)} m² en total)</span>
                    </div>
                    <div class="grupo-resumen-badges d-flex gap-1">${badges.join(' ')}</div>
                </div>
                <div class="grupo-body">
                    <table class="table table-hover table-small mb-0">
                        <thead class="table-light">
                            <tr><th>#</th><th>Área restante</th><th>Estado</th><th>Ingreso</th><th>Acciones</th></tr>
                        </thead>
                        <tbody>${filasHtml}</tbody>
                    </table>
                </div>`;

            const header = div.querySelector('.grupo-header');
            const body = div.querySelector('.grupo-body');
            engancharAcordeon(header, body, false);

            contenedor.appendChild(div);
        });

        document.getElementById('contadorRollo').textContent =
            grupoCount === 0
                ? 'Ningún rollo coincide con los filtros seleccionados.'
                : `Mostrando ${grupoCount} grupo(s) — ${totalVisible} rollo(s) en total.`;
    }

    ['filtroRolloColor', 'filtroRolloAncho', 'filtroRolloEstado'].forEach(id => {
        document.getElementById(id).addEventListener('change', render);
    });
    document.getElementById('btnLimpiarFiltroRollo').addEventListener('click', () => {
        document.getElementById('filtroRolloColor').value = '';
        document.getElementById('filtroRolloAncho').value = '';
        document.getElementById('filtroRolloEstado').value = '';
        render();
    });
    document.getElementById('btnExpandirTodosRollo').addEventListener('click', () => {
        document.querySelectorAll('#gruposRollosContainer .grupo-header').forEach(h => h.classList.add('abierto'));
        document.querySelectorAll('#gruposRollosContainer .grupo-body').forEach(b => {
            b.classList.add('abierto');
            calcularAlturaGrupo(b);
        });
    });
    document.getElementById('btnColapsarTodosRollo').addEventListener('click', () => {
        document.querySelectorAll('#gruposRollosContainer .grupo-header').forEach(h => h.classList.remove('abierto'));
        document.querySelectorAll('#gruposRollosContainer .grupo-body').forEach(b => {
            b.classList.remove('abierto');
            b.style.maxHeight = '0px';
        });
    });

    contenedor.addEventListener('click', (e) => {
        const btn = e.target.closest('.btn-agotar-rollo');
        if (!btn) return;
        e.preventDefault();
        if (!window.confirm('¿Marcar este rollo como agotado (largo restante = 0)?')) return;
        const form = btn.closest('form');
        const input = form.querySelector('input[name="largoRestante"]');
        if (input) input.value = '0';
        form.submit();
    });

    render();
})();

(function initRetazos() {
    const filas = Array.from(document.querySelectorAll('#datosRetazosRaw tbody tr'));
    const contenedor = document.getElementById('gruposRetazosContainer');
    if (!contenedor) return;

    const retazos = filas.map(tr => ({
        id: tr.dataset.id,
        color: tr.dataset.color,
        ancho: tr.dataset.ancho,
        alto: tr.dataset.alto,
        area: tr.dataset.area,
        agotado: tr.dataset.agotado === 'true',
        fecha: tr.dataset.fecha,
        urlEliminar: tr.dataset.urlEliminar
    }));

    if (retazos.length === 0) return;

    const grupos = {};
    retazos.forEach(r => {
        if (!grupos[r.color]) grupos[r.color] = { color: r.color, items: [] };
        grupos[r.color].items.push(r);
    });

    const clavesOrdenadas = Object.keys(grupos).sort((a, b) => grupos[a].color.localeCompare(grupos[b].color));

    function render() {
        const fColor = document.getElementById('filtroRetazoColor').value;
        const fEstado = document.getElementById('filtroRetazoEstado').value;

        contenedor.innerHTML = '';
        let totalVisible = 0;
        let grupoCount = 0;

        clavesOrdenadas.forEach(key => {
            const g = grupos[key];
            if (fColor && g.color !== fColor) return;

            const itemsFiltrados = g.items.filter(r => {
                const estado = r.agotado ? 'agotado' : 'disponible';
                return !fEstado || estado === fEstado;
            });
            if (itemsFiltrados.length === 0) return;

            grupoCount++;
            totalVisible += itemsFiltrados.length;

            const disponibles = itemsFiltrados.filter(r => !r.agotado).length;
            const agotados = itemsFiltrados.filter(r => r.agotado).length;
            const areaTotal = itemsFiltrados.reduce((sum, r) => sum + (parseFloat(r.area.replace(',', '.')) || 0), 0);

            const div = document.createElement('div');
            div.className = 'grupo-rollo';

            const badges = [];
            if (disponibles) badges.push(`<span class="badge badge-retazo">${disponibles} disponible(s)</span>`);
            if (agotados) badges.push(`<span class="badge bg-danger">${agotados} agotado(s)</span>`);

            let filasHtml = '';
            itemsFiltrados.forEach(r => {
                const estClase = r.agotado ? 'fila-agotado' : 'fila-retazo';
                const estBadge = r.agotado
                    ? '<span class="badge bg-danger">Agotado</span>'
                    : '<span class="badge badge-retazo">Disponible</span>';

                filasHtml += `
                    <tr class="${estClase}">
                        <td><span class="id-pill">#${escapeHtml(r.id)}</span></td>
                        <td>${escapeHtml(r.ancho)} m</td>
                        <td><strong>${escapeHtml(r.alto)} m</strong></td>
                        <td>${escapeHtml(r.area)} m²</td>
                        <td>${estBadge}</td>
                        <td><small>${escapeHtml(r.fecha)}</small></td>
                        <td>
                            <form action="${escapeHtml(r.urlEliminar)}" method="post" class="d-inline"
                                  onsubmit="return confirm('¿Eliminar este retazo?');">
                                <button type="submit" class="btn btn-sm btn-outline-danger">🗑️</button>
                            </form>
                        </td>
                    </tr>`;
            });

            div.innerHTML = `
                <div class="grupo-header" role="button">
                    <div class="grupo-titulo">
                        <span class="chevron">▶</span>
                        <span class="swatch-color" style="background:${colorSwatch(g.color)};"></span>
                        <span>${escapeHtml(g.color)}</span>
                        <span class="grupo-meta">(${itemsFiltrados.length} retazo${itemsFiltrados.length !== 1 ? 's' : ''} · ${areaTotal.toFixed(2)} m² en total)</span>
                    </div>
                    <div class="grupo-resumen-badges d-flex gap-1">${badges.join(' ')}</div>
                </div>
                <div class="grupo-body">
                    <table class="table table-hover table-small mb-0">
                        <thead class="table-light">
                            <tr><th>#</th><th>Ancho</th><th>Alto disponible</th><th>Área</th><th>Estado</th><th>Ingreso</th><th>Acciones</th></tr>
                        </thead>
                        <tbody>${filasHtml}</tbody>
                    </table>
                </div>`;

            const header = div.querySelector('.grupo-header');
            const body = div.querySelector('.grupo-body');
            engancharAcordeon(header, body, false);

            contenedor.appendChild(div);
        });

        document.getElementById('contadorRetazo').textContent =
            grupoCount === 0
                ? 'Ningún retazo coincide con los filtros seleccionados.'
                : `Mostrando ${grupoCount} grupo(s) — ${totalVisible} retazo(s) en total.`;
    }

    document.getElementById('filtroRetazoColor').addEventListener('change', render);
    document.getElementById('filtroRetazoEstado').addEventListener('change', render);
    document.getElementById('btnLimpiarFiltroRetazo').addEventListener('click', () => {
        document.getElementById('filtroRetazoColor').value = '';
        document.getElementById('filtroRetazoEstado').value = '';
        render();
    });
    document.getElementById('btnExpandirTodosRetazo').addEventListener('click', () => {
        document.querySelectorAll('#gruposRetazosContainer .grupo-header').forEach(h => h.classList.add('abierto'));
        document.querySelectorAll('#gruposRetazosContainer .grupo-body').forEach(b => {
            b.classList.add('abierto');
            calcularAlturaGrupo(b);
        });
    });
    document.getElementById('btnColapsarTodosRetazo').addEventListener('click', () => {
        document.querySelectorAll('#gruposRetazosContainer .grupo-header').forEach(h => h.classList.remove('abierto'));
        document.querySelectorAll('#gruposRetazosContainer .grupo-body').forEach(b => {
            b.classList.remove('abierto');
            b.style.maxHeight = '0px';
        });
    });

    render();
})();

(function initInsumos() {
    const filasInsumo = Array.from(document.querySelectorAll('#datosInsumosRaw tbody tr'));
    const filasPieza = Array.from(document.querySelectorAll('#datosPiezasRaw tbody tr'));
    const contenedor = document.getElementById('gruposInsumosContainer');
    if (!contenedor || filasInsumo.length === 0) return;

    const insumos = filasInsumo.map(tr => ({
        id: tr.dataset.id,
        nombre: tr.dataset.nombre,
        tieneMedida: tr.dataset.tieneMedida === 'true',
        descripcion: tr.dataset.descripcion,
        stockUnidades: tr.dataset.stockUnidades,
        numPiezas: tr.dataset.numPiezas,
        urlCargar: tr.dataset.urlCargar
    }));

    const piezasPorInsumo = {};
    filasPieza.forEach(tr => {
        const insId = tr.dataset.insumoId;
        if (!piezasPorInsumo[insId]) piezasPorInsumo[insId] = [];
        piezasPorInsumo[insId].push({
            id: tr.dataset.id,
            agotada: tr.dataset.agotada === 'true',
            largoRestante: tr.dataset.largoRestante,
            largoInicial: tr.dataset.largoInicial,
            fecha: tr.dataset.fecha,
            urlEliminar: tr.dataset.urlEliminar
        });
    });

    function nivelStockDe(ins) {
        if (ins.tieneMedida) {
            const piezas = piezasPorInsumo[ins.id] || [];
            return piezas
                .filter(p => !p.agotada)
                .reduce((sum, p) => sum + (parseFloat(p.largoRestante.replace(',', '.')) || 0), 0);
        }
        return parseFloat(ins.stockUnidades) || 0;
    }

    function render() {
        const fTipo = document.getElementById('filtroInsumoTipo').value;
        const fNombre = document.getElementById('filtroInsumoNombre').value.toLowerCase().trim();
        const fOrden = document.getElementById('ordenInsumo').value;

        let insumosOrdenados = insumos.slice();
        if (fOrden === 'stock_bajo') {
            insumosOrdenados.sort((a, b) => nivelStockDe(a) - nivelStockDe(b));
        } else {
            insumosOrdenados.sort((a, b) => a.nombre.localeCompare(b.nombre));
        }

        contenedor.innerHTML = '';
        let visibles = 0;

        insumosOrdenados.forEach(ins => {
            const tipoMatch = !fTipo || (fTipo === 'medida' && ins.tieneMedida) || (fTipo === 'unidad' && !ins.tieneMedida);
            const nombreMatch = !fNombre || ins.nombre.toLowerCase().includes(fNombre);
            if (!tipoMatch || !nombreMatch) return;

            visibles++;
            const piezas = piezasPorInsumo[ins.id] || [];

            const div = document.createElement('div');
            div.className = 'grupo-insumo';
            div.id = 'insumo-' + ins.id;

            const badgeTipo = ins.tieneMedida
                ? '<span class="badge badge-tipo-medida">Por medida</span>'
                : '<span class="badge badge-tipo-unidad">Por unidad</span>';

            let stockTexto = ins.tieneMedida
                ? `${piezas.length} pieza(s)`
                : `${ins.stockUnidades} unidad(es)`;

            let cuerpoInterno = '';
            if (ins.tieneMedida) {
                const disponibles = piezas.filter(p => !p.agotada).length;
                const agotadas = piezas.filter(p => p.agotada).length;

                if (disponibles === 0 && piezas.length > 0) div.classList.add('stock-agotado');
                else if (disponibles <= 2) div.classList.add('stock-critico');

                let filasPiezasHtml = '';
                piezas.forEach(p => {
                    const estClase = p.agotada ? 'fila-agotado' : '';
                    const estBadge = p.agotada
                        ? '<span class="badge bg-danger">Agotada</span>'
                        : '<span class="badge bg-success">Disponible</span>';
                    filasPiezasHtml += `
                        <tr class="${estClase}">
                            <td><span class="id-pill">#${escapeHtml(p.id)}</span></td>
                            <td><strong>${escapeHtml(p.largoRestante)} / ${escapeHtml(p.largoInicial)} m</strong></td>
                            <td>${estBadge}</td>
                            <td><small>${escapeHtml(p.fecha)}</small></td>
                            <td>
                                <form action="${escapeHtml(p.urlEliminar)}" method="post" class="d-inline"
                                      onsubmit="return confirm('¿Eliminar esta pieza del inventario?');">
                                    <button type="submit" class="btn btn-sm btn-outline-danger">🗑️</button>
                                </form>
                            </td>
                        </tr>`;
                });

                if (piezas.length === 0) {
                    filasPiezasHtml = `<tr><td colspan="5" class="text-center text-muted py-2">Sin piezas registradas todavía.</td></tr>`;
                }

                cuerpoInterno = `
                    <div class="grupo-body">
                        <table class="table table-hover table-small mb-0">
                            <thead class="table-light">
                                <tr><th>#</th><th>Largo restante</th><th>Estado</th><th>Ingreso</th><th>Acciones</th></tr>
                            </thead>
                            <tbody>${filasPiezasHtml}</tbody>
                        </table>
                    </div>`;

                div.dataset.disponibles = disponibles;
                div.dataset.agotadas = agotadas;
            } else {
                const stockNum = parseFloat(ins.stockUnidades) || 0;
                if (stockNum === 0) div.classList.add('stock-agotado');
                else if (stockNum <= 5) div.classList.add('stock-critico');

                cuerpoInterno = `
                    <div class="grupo-body">
                        <div class="p-3">
                            <p class="mb-1"><strong>Stock actual:</strong> ${escapeHtml(ins.stockUnidades)} unidad(es)</p>
                            <p class="mb-0 text-muted"><small>${escapeHtml(ins.descripcion || 'Sin descripción.')}</small></p>
                        </div>
                    </div>`;
            }

            div.innerHTML = `
                <div class="grupo-header" role="button">
                    <div class="grupo-titulo">
                        <span class="chevron">▶</span>
                        <span class="id-pill">#${escapeHtml(ins.id)}</span>
                        <span>${escapeHtml(ins.nombre)}</span>
                        ${badgeTipo}
                        <span class="grupo-meta">(${stockTexto})</span>
                    </div>
                    <a href="${escapeHtml(ins.urlCargar)}" class="btn btn-sm btn-outline-primary" onclick="event.stopPropagation();">+ Cargar stock</a>
                </div>
                ${cuerpoInterno}`;

            const header = div.querySelector('.grupo-header');
            const body = div.querySelector('.grupo-body');
            engancharAcordeon(header, body, false);

            contenedor.appendChild(div);
        });

        document.getElementById('contadorInsumo').textContent =
            visibles === 0
                ? 'Ningún insumo coincide con los filtros seleccionados.'
                : `Mostrando ${visibles} insumo(s).`;
    }

    document.getElementById('filtroInsumoTipo').addEventListener('change', render);
    document.getElementById('filtroInsumoNombre').addEventListener('input', render);
    document.getElementById('ordenInsumo').addEventListener('change', render);
    document.getElementById('btnLimpiarFiltroInsumo').addEventListener('click', () => {
        document.getElementById('filtroInsumoTipo').value = '';
        document.getElementById('filtroInsumoNombre').value = '';
        document.getElementById('ordenInsumo').value = 'nombre';
        render();
    });
    document.getElementById('btnExpandirTodosInsumo').addEventListener('click', () => {
        document.querySelectorAll('#gruposInsumosContainer .grupo-header').forEach(h => h.classList.add('abierto'));
        document.querySelectorAll('#gruposInsumosContainer .grupo-body').forEach(b => {
            b.classList.add('abierto');
            calcularAlturaGrupo(b);
        });
    });
    document.getElementById('btnColapsarTodosInsumo').addEventListener('click', () => {
        document.querySelectorAll('#gruposInsumosContainer .grupo-header').forEach(h => h.classList.remove('abierto'));
        document.querySelectorAll('#gruposInsumosContainer .grupo-body').forEach(b => {
            b.classList.remove('abierto');
            b.style.maxHeight = '0px';
        });
    });

    render();
})();