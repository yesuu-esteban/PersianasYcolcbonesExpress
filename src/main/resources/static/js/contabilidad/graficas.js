/* Pantalla: templates/contabilidad/graficas.html
   Dibuja con ECharts los tres pasteles, las velas japonesas y los ingresos contra gastos.
   Los números llegan del servidor en window.DATOS_GRAFICAS (ver GraficasServicio.java). */

(function () {
    'use strict';

    const D = window.DATOS_GRAFICAS || {};

    if (typeof echarts === 'undefined') {
        const aviso = document.getElementById('avisoSinLibreria');
        if (aviso) aviso.hidden = false;
        return;
    }

    // ── Colores (mismo estilo oscuro del resto de Contabilidad) ──
    const FONDO = '#2a1b42';                 // fondo de las cajas: separa las tajadas
    const TEXTO = '#f4f0fb';
    const TEXTO_SUAVE = '#cbb8e0';
    const LINEA = 'rgba(255,255,255,0.08)';
    const VERDE = '#51cf66';                 // sube / ingresos (igual que en Movimientos)
    const ROJO = '#ff8a80';                  // baja / gastos
    const DORADO = '#ffc107';                // utilidad (igual que "Utilidad neta" en el Resumen)
    // Seis colores probados para que se distingan entre sí (también con daltonismo) sobre el fondo oscuro.
    const TAJADAS = ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#9085e9'];
    const OTROS = '#8a8296';                 // la tajada "Otros" siempre en gris

    const FUENTE = { fontFamily: 'Poppins, sans-serif', color: TEXTO };

    // ── Plata ──
    const pesos = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
    function plata(v) {
        const n = Number(v) || 0;
        return (n < 0 ? '-$' : '$') + pesos.format(Math.abs(n));
    }
    /** Para los ejes: $1,2 M · $350 mil · $800 */
    function plataCorta(v) {
        const n = Math.abs(Number(v) || 0), s = v < 0 ? '-$' : '$';
        if (n >= 1e6) return s + (n / 1e6).toLocaleString('es-CO', { maximumFractionDigits: 1 }) + ' M';
        if (n >= 1e3) return s + Math.round(n / 1e3).toLocaleString('es-CO') + ' mil';
        return s + n;
    }
    function escapar(t) {
        return String(t == null ? '' : t).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    const graficas = [];
    function iniciar(id) {
        const el = document.getElementById(id);
        if (!el) return null;
        const g = echarts.init(el, null, { renderer: 'canvas' });
        graficas.push(g);
        return g;
    }
    function sinDatos(id, texto) {
        const el = document.getElementById(id);
        if (!el) return;
        el.classList.add('sin-datos');
        el.style.height = 'auto';
        el.textContent = texto;
    }

    const TOOLTIP_BASE = {
        backgroundColor: '#1e1330',
        borderColor: 'rgba(255,255,255,0.15)',
        textStyle: { color: TEXTO, fontFamily: 'Poppins, sans-serif', fontSize: 12 },
        extraCssText: 'border-radius:10px; box-shadow:0 6px 20px rgba(0,0,0,.45);'
    };

    // ═══════════════════════════════════════════════════════════════
    // PASTELES
    // ═══════════════════════════════════════════════════════════════

    function pastel(id, idTotal, tajadas, textoTotal, textoVacio) {
        tajadas = Array.isArray(tajadas) ? tajadas : [];
        const total = tajadas.reduce((s, t) => s + (Number(t.valor) || 0), 0);
        const elTotal = document.getElementById(idTotal);
        if (elTotal) elTotal.textContent = textoTotal + ': ' + plata(total);
        if (!tajadas.length || total <= 0) { sinDatos(id, textoVacio); return; }

        const g = iniciar(id);
        if (!g) return;
        let color = 0;
        const datos = tajadas.map(t => ({
            name: t.nombre,
            value: Number(t.valor) || 0,
            detalle: t.detalle || [],
            itemStyle: { color: t.nombre === 'Otros' ? OTROS : TAJADAS[color++ % TAJADAS.length] }
        }));

        g.setOption({
            textStyle: FUENTE,
            tooltip: Object.assign({}, TOOLTIP_BASE, {
                trigger: 'item',
                formatter: p => {
                    let html = '<b>' + escapar(p.name) + '</b><br>' + plata(p.value) + ' · ' + p.percent + ' %';
                    const det = (p.data && p.data.detalle) || [];
                    if (det.length) {
                        html += '<div style="margin-top:6px;color:' + TEXTO_SUAVE + '">';
                        det.slice(0, 6).forEach(d => { html += escapar(d.nombre) + ': ' + plata(d.valor) + '<br>'; });
                        if (det.length > 6) html += '… y ' + (det.length - 6) + ' más';
                        html += '</div>';
                    }
                    return html;
                }
            }),
            legend: {
                // Máximo 6 nombres: se ven todos de una vez (en 2 o 3 renglones), sin flechas de página
                type: 'plain', bottom: 0, left: 'center', width: '94%', icon: 'circle', itemWidth: 10, itemHeight: 10, itemGap: 12,
                textStyle: { color: TEXTO_SUAVE, fontSize: 11, width: 130, overflow: 'truncate' },
                tooltip: { show: true }
            },
            series: [{
                type: 'pie',
                radius: ['40%', '62%'],
                center: ['50%', '38%'],
                avoidLabelOverlap: true,
                itemStyle: { borderColor: FONDO, borderWidth: 2, borderRadius: 4 },
                label: { show: false },
                labelLine: { show: false },
                emphasis: { scale: true, scaleSize: 6, label: { show: false } },
                data: datos
            }],
            // Total en el centro del anillo
            graphic: [{
                type: 'text', left: 'center', top: Math.round(g.getHeight() * 0.38) - 10,
                style: { text: plataCorta(total), fill: TEXTO, font: '700 15px Poppins, sans-serif', textAlign: 'center' }
            }]
        });
    }

    pastel('pastelGastos', 'totalGastos', D.gastos, 'Gastos del mes', 'No hay gastos anotados en este mes.');
    pastel('pastelIngresos', 'totalIngresos', D.ingresos, 'Ingresos del mes', 'No hay ingresos anotados en este mes.');
    pastel('pastelCuentas', 'totalCuentas', D.cuentas, 'Plata en las cuentas', 'Ninguna cuenta tiene saldo a favor.');

    // Cuentas en rojo (no caben en un pastel): se avisan debajo
    const enRojo = Array.isArray(D.cuentasEnRojo) ? D.cuentasEnRojo : [];
    if (enRojo.length) {
        const p = document.getElementById('cuentasEnRojo');
        p.textContent = 'En rojo (no salen en el pastel): ' + enRojo.map(c => c.nombre + ' ' + plata(c.valor)).join(' · ');
        p.hidden = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // VELAS JAPONESAS
    // ═══════════════════════════════════════════════════════════════

    (function velas() {
        const v = D.velas || {};
        const etiquetas = v.etiquetas || [], datos = v.datos || [], detalle = v.detalle || [];
        if (!datos.length) { sinDatos('velas', 'Todavía no hay días para mostrar en este mes.'); return; }

        const g = iniciar('velas');
        if (!g) return;
        g.setOption({
            textStyle: FUENTE,
            grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
            tooltip: Object.assign({}, TOOLTIP_BASE, {
                trigger: 'axis',
                axisPointer: { type: 'cross', lineStyle: { color: 'rgba(255,255,255,0.35)' }, crossStyle: { color: 'rgba(255,255,255,0.35)' },
                               label: { backgroundColor: '#3b2a59', color: TEXTO, formatter: p => p.axisDimension === 'y' ? plataCorta(p.value) : p.value } },
                formatter: ps => {
                    const p = ps.find(x => x.seriesType === 'candlestick') || ps[0];
                    const i = p.dataIndex, d = datos[i] || [], det = detalle[i] || {};
                    const abre = d[0], cierra = d[1], bajo = d[2], alto = d[3];
                    const sube = cierra >= abre;
                    const periodo = det.desde === det.hasta ? det.desde : det.desde + ' al ' + det.hasta;
                    return '<b>' + escapar(periodo) + '</b> · <span style="color:' + (sube ? VERDE : ROJO) + '">'
                        + (cierra === abre ? 'sin cambio' : (sube ? '▲ subió ' : '▼ bajó ') + plata(Math.abs(cierra - abre))) + '</span><br>'
                        + 'Empezó con: ' + plata(abre) + '<br>'
                        + 'Terminó con: <b>' + plata(cierra) + '</b><br>'
                        + 'Lo más alto: ' + plata(alto) + '<br>'
                        + 'Lo más bajo: ' + plata(bajo) + '<br>'
                        + '<span style="color:' + TEXTO_SUAVE + '">Entró ' + plata(det.entradas) + ' · salió ' + plata(det.salidas)
                        + ' · ' + (det.movimientos || 0) + ' movimiento(s)</span>';
                }
            }),
            xAxis: {
                type: 'category', data: etiquetas, boundaryGap: true,
                axisLine: { lineStyle: { color: 'rgba(255,255,255,0.2)' } },
                axisTick: { show: false },
                axisLabel: { color: TEXTO_SUAVE, fontSize: 11, hideOverlap: true }
            },
            yAxis: {
                type: 'value', scale: true,
                splitLine: { lineStyle: { color: LINEA } },
                axisLabel: { color: TEXTO_SUAVE, fontSize: 11, formatter: plataCorta }
            },
            series: [{
                type: 'candlestick',
                name: 'Plata total',
                data: datos,
                barMaxWidth: 18,
                itemStyle: { color: VERDE, color0: ROJO, borderColor: VERDE, borderColor0: ROJO, borderWidth: 1.5 }
            }]
        });
    })();

    // ═══════════════════════════════════════════════════════════════
    // INGRESOS CONTRA GASTOS (12 MESES)
    // ═══════════════════════════════════════════════════════════════

    (function meses() {
        const m = D.meses || {};
        const etiquetas = m.etiquetas || [], ing = m.ingresos || [], gas = m.gastos || [], util = m.utilidad || [];

        // Tabla con los números (para leerlos exactos)
        const cuerpo = document.getElementById('tablaMeses');
        if (cuerpo) {
            cuerpo.innerHTML = etiquetas.map((e, i) =>
                '<tr><td>' + escapar(e) + '</td><td class="num">' + plata(ing[i]) + '</td><td class="num">' + plata(gas[i])
                + '</td><td class="num fw-bold" style="color:' + (util[i] < 0 ? ROJO : TEXTO) + '">' + plata(util[i]) + '</td></tr>').reverse().join('');
        }

        const hayAlgo = ing.some(x => x) || gas.some(x => x);
        if (!hayAlgo) { sinDatos('ingresosGastos', 'Todavía no hay ingresos ni gastos anotados en estos 12 meses.'); return; }

        const g = iniciar('ingresosGastos');
        if (!g) return;
        g.setOption({
            textStyle: FUENTE,
            grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
            legend: {
                top: 0, left: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 8,
                textStyle: { color: TEXTO_SUAVE, fontSize: 12 },
                data: ['Ingresos', 'Gastos', 'Utilidad']
            },
            tooltip: Object.assign({}, TOOLTIP_BASE, {
                trigger: 'axis',
                axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(255,255,255,0.05)' } },
                formatter: ps => {
                    const i = ps[0].dataIndex;
                    return '<b>' + escapar(etiquetas[i]) + '</b><br>'
                        + '<span style="color:' + VERDE + '">●</span> Ingresos: ' + plata(ing[i]) + '<br>'
                        + '<span style="color:' + ROJO + '">●</span> Gastos: ' + plata(gas[i]) + '<br>'
                        + '<span style="color:' + DORADO + '">●</span> Utilidad: <b>' + plata(util[i]) + '</b>';
                }
            }),
            xAxis: {
                type: 'category', data: etiquetas,
                axisLine: { lineStyle: { color: 'rgba(255,255,255,0.2)' } },
                axisTick: { show: false },
                axisLabel: { color: TEXTO_SUAVE, fontSize: 11, hideOverlap: true }
            },
            yAxis: {
                type: 'value',
                splitLine: { lineStyle: { color: LINEA } },
                axisLabel: { color: TEXTO_SUAVE, fontSize: 11, formatter: plataCorta }
            },
            series: [
                { name: 'Ingresos', type: 'bar', data: ing, barMaxWidth: 16, barGap: '15%',
                  itemStyle: { color: VERDE, borderRadius: [4, 4, 0, 0] } },
                { name: 'Gastos', type: 'bar', data: gas, barMaxWidth: 16,
                  itemStyle: { color: ROJO, borderRadius: [4, 4, 0, 0] } },
                { name: 'Utilidad', type: 'line', data: util, smooth: false, symbol: 'circle', symbolSize: 8,
                  lineStyle: { color: DORADO, width: 2 }, itemStyle: { color: DORADO, borderColor: FONDO, borderWidth: 2 },
                  markLine: { silent: true, symbol: 'none', label: { show: false },
                              lineStyle: { color: 'rgba(255,255,255,0.25)', type: 'solid', width: 1 }, data: [{ yAxis: 0 }] } }
            ]
        });
    })();

    // Las gráficas se acomodan si cambia el tamaño de la ventana (o se gira el celular)
    let espera;
    window.addEventListener('resize', () => {
        clearTimeout(espera);
        espera = setTimeout(() => graficas.forEach(g => g.resize()), 120);
    });
})();