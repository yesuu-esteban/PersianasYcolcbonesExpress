/* Pantalla: templates/contabilidad/ventas.html
   - Buscar y filtrar ventas (las que deben, pagadas, sin facturar, sin instalar).
   - 💵 Pago: abre el cuadro para registrar lo que pagó el cliente y muestra en vivo cuánto queda debiendo.
   - ✏ Datos: abre el cuadro de factura, IVA, retención, costo, gasto… y recalcula en vivo
     subtotal, IVA, RTF, total, por cobrar y utilidad (el mismo cálculo que hace el servidor).
   Los datos de cada venta vienen en la constante VENTAS (la pone la plantilla). */

(function () {
    const filas = Array.isArray(window.VENTAS) ? window.VENTAS : (typeof VENTAS !== 'undefined' ? VENTAS : []);
    const porId = {};
    filas.forEach(v => { porId[v.id] = v; });

    const fmt = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
    const plata = n => (n < 0 ? '-$' : '$') + fmt.format(Math.abs(Math.round(n)));
    const num = v => Number(v) || 0;
    /** "$ 1.250.000" → 1250000 */
    const leerPlata = t => num(String(t || '').replace(/\D/g, ''));
    /** "2,5 %" → 2.5 (vacío → porDefecto) */
    const leerPct = (t, porDefecto) => {
        const s = String(t || '').replace('%', '').replace(',', '.').trim();
        return s === '' ? porDefecto : (parseFloat(s) || 0);
    };

    // ═══ Buscar y filtrar ═══
    const buscar = document.getElementById('buscarVenta');
    const filtro = document.getElementById('filtroVenta');
    const contador = document.getElementById('contadorVentas');
    const sinTildes = t => String(t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

    function aplicarFiltro() {
        const q = sinTildes(buscar ? buscar.value.trim() : '');
        const f = filtro ? filtro.value : 'todas';
        let visibles = 0;
        document.querySelectorAll('tr.fila-venta').forEach(tr => {
            let ver = q === '' || sinTildes(tr.textContent).includes(q);
            if (ver && f === 'deben') ver = tr.dataset.debe === 'true';
            if (ver && f === 'pagadas') ver = tr.dataset.debe !== 'true';
            if (ver && f === 'sin-factura') ver = tr.dataset.factura === 'PENDIENTE';
            if (ver && f === 'sin-instalar') ver = tr.dataset.instalacion === 'NO_INSTALADO';
            tr.hidden = !ver;
            if (ver) visibles++;
        });
        if (contador) contador.textContent = visibles + ' de ' + filas.length + ' venta(s)';
    }
    if (buscar) buscar.addEventListener('input', aplicarFiltro);
    if (filtro) filtro.addEventListener('change', aplicarFiltro);

    // ═══ Cuadros (dialog) ═══
    document.querySelectorAll('dialog .btn-cerrar').forEach(b => b.addEventListener('click', () => b.closest('dialog').close()));
    // Cerrar al hacer clic fuera del cuadro
    document.querySelectorAll('dialog').forEach(d => d.addEventListener('click', e => { if (e.target === d) d.close(); }));

    /** Mientras escriben un valor en pesos, se le ponen los puntos de miles. */
    function formatearMientrasEscribe(input, despues) {
        input.addEventListener('input', () => {
            const n = leerPlata(input.value);
            input.value = input.value.trim() === '' ? '' : '$ ' + fmt.format(n);
            if (despues) despues();
        });
    }

    // ═══ 💵 Registrar pago ═══
    const dlgPago = document.getElementById('dlgPago');
    const formPago = document.getElementById('formPago');
    const pagoValor = document.getElementById('pagoValor');
    const pagoQueda = document.getElementById('pagoQueda');
    let ventaPago = null;

    function actualizarQueda() {
        if (!ventaPago) return;
        const debe = num(ventaPago.saldo);
        const paga = leerPlata(pagoValor.value);
        if (paga <= 0) {
            pagoQueda.className = 'queda-debiendo';
            pagoQueda.textContent = 'Escribe cuánto pagó.';
        } else if (paga > debe) {
            pagoQueda.className = 'queda-debiendo mal';
            pagoQueda.textContent = '⚠ Es más de lo que debe (' + plata(debe) + ').';
        } else if (paga === debe) {
            pagoQueda.className = 'queda-debiendo bien';
            pagoQueda.textContent = '✓ Con este pago queda a paz y salvo.';
        } else {
            pagoQueda.className = 'queda-debiendo';
            pagoQueda.innerHTML = 'Después de este pago queda debiendo <strong>' + plata(debe - paga) + '</strong>.';
        }
    }
    if (pagoValor) formatearMientrasEscribe(pagoValor, actualizarQueda);

    const pagoTodo = document.getElementById('pagoTodo');
    if (pagoTodo) pagoTodo.addEventListener('click', () => {
        if (!ventaPago) return;
        pagoValor.value = '$ ' + fmt.format(num(ventaPago.saldo));
        actualizarQueda();
    });

    if (formPago) formPago.addEventListener('submit', e => {
        if (!ventaPago) { e.preventDefault(); return; }
        const paga = leerPlata(pagoValor.value);
        if (paga <= 0 || paga > num(ventaPago.saldo)) {
            e.preventDefault();
            actualizarQueda();
            pagoValor.focus();
        }
    });

    document.querySelectorAll('.btn-pago').forEach(b => b.addEventListener('click', () => {
        const v = porId[b.dataset.id];
        if (!v || !dlgPago) return;
        ventaPago = v;
        formPago.action = '/contabilidad/ventas/' + v.id + '/pago';
        document.getElementById('pagoCliente').textContent = 'Venta #' + v.id + ' · ' + v.cliente + (v.factura ? ' · Factura ' + v.factura : '');
        document.getElementById('pagoTotal').textContent = plata(num(v.neto));
        document.getElementById('pagoNotaRtf').textContent = num(v.retencion) > 0 ? 'ya sin la RTF de ' + plata(num(v.retencion)) : '';
        document.getElementById('pagoAbonado').textContent = plata(num(v.abonado));
        document.getElementById('pagoDebe').textContent = plata(num(v.saldo));
        pagoValor.value = '';
        actualizarQueda();
        dlgPago.showModal();
        pagoValor.focus();
    }));

    // ═══ ✏ Datos de la venta ═══
    const dlgDatos = document.getElementById('dlgDatos');
    const formDatos = document.getElementById('formDatos');
    const c = id => document.getElementById(id);
    let ventaDatos = null;

    /** El mismo cálculo del servidor (VentasAlmacenServicio.calcular). */
    function recalcular() {
        if (!ventaDatos) return;
        const total = num(ventaDatos.precio);
        const llevaIva = c('dLlevaIva').checked;
        const pctIva = leerPct(c('dPctIva').value, 19);
        let subtotal = total, iva = 0;
        if (llevaIva && pctIva > 0) {
            subtotal = Math.round(total / (1 + pctIva / 100));
            iva = total - subtotal;
        }
        const ret = Math.round(subtotal * leerPct(c('dPctRet').value, 0) / 100);
        const costoTxt = c('dCosto').value.trim();
        const costo = costoTxt === '' ? num(ventaDatos.costoFabrica) : leerPlata(costoTxt);
        const gasto = leerPlata(c('dGasto').value);
        const saldo = total - ret - num(ventaDatos.abonado);

        c('cSubtotal').textContent = plata(subtotal);
        c('cIva').textContent = plata(iva);
        c('cRet').textContent = plata(ret);
        c('cTotal').textContent = plata(total);
        c('cSaldo').textContent = saldo < 0 ? plata(-saldo) + ' a favor' : (saldo === 0 ? '✓ Paz y salvo' : plata(saldo));
        c('cUtilidad').textContent = plata(total - costo - gasto);
        c('dPctIva').disabled = !llevaIva;
    }

    if (formDatos) {
        formDatos.querySelectorAll('input, select').forEach(el => {
            el.addEventListener('input', recalcular);
            el.addEventListener('change', recalcular);
        });
        formatearMientrasEscribe(c('dCosto'), recalcular);
        formatearMientrasEscribe(c('dGasto'), recalcular);
        // Si escriben un número de factura, se sugiere "Facturada" y que lleva IVA
        c('dFactura').addEventListener('change', () => {
            if (c('dFactura').value.trim() !== '' && c('dEstadoFactura').value === 'PENDIENTE') {
                c('dEstadoFactura').value = 'FACTURADA';
                c('dLlevaIva').checked = true;
                recalcular();
            }
        });
        // El % de IVA deshabilitado no viaja: se manda igual para no perderlo
        formDatos.addEventListener('submit', () => { c('dPctIva').disabled = false; });
    }

    document.querySelectorAll('.btn-datos').forEach(b => b.addEventListener('click', () => {
        const v = porId[b.dataset.id];
        if (!v || !dlgDatos) return;
        ventaDatos = v;
        formDatos.action = '/contabilidad/ventas/' + v.id + '/datos';
        c('datosCliente').textContent = 'Venta #' + v.id + ' · ' + v.cliente + ' · Precio ' + plata(num(v.precio))
            + ' · Abonado ' + plata(num(v.abonado));
        c('dFactura').value = v.factura || '';
        c('dEstadoFactura').value = v.estadoFactura || 'PENDIENTE';
        c('dLlevaIva').checked = !!v.llevaIva;
        c('dPctIva').value = String(num(v.porcentajeIva) || 19).replace('.', ',');
        c('dPctRet').value = num(v.porcentajeRetencion) > 0 ? String(num(v.porcentajeRetencion)).replace('.', ',') : '';
        c('dProveedor').value = v.proveedor || '';
        c('dProveedor').placeholder = v.proveedorPedido ? 'Fabricante del pedido: ' + v.proveedorPedido : 'Ej: SAFRA, P Y C';
        c('dCosto').value = v.costoManual ? '$ ' + fmt.format(num(v.costo)) : '';
        c('dCosto').placeholder = 'De fábrica: ' + plata(num(v.costoFabrica));
        c('dCostoAyuda').textContent = 'Vacío = costo de fábrica (' + plata(num(v.costoFabrica)) + ')';
        c('dGasto').value = num(v.gasto) > 0 ? '$ ' + fmt.format(num(v.gasto)) : '';
        c('dInstalacion').value = v.estadoInstalacion || 'NO_INSTALADO';
        c('dPunto').value = v.puntoVenta || '';
        c('dInstalo').value = v.quienInstalo || '';
        recalcular();
        dlgDatos.showModal();
        c('dFactura').focus();
    }));

    // ═══ Si se acaba de guardar algo, se baja hasta esa venta ═══
    const resaltada = document.querySelector('tr.fila-venta.resaltada');
    if (resaltada) resaltada.scrollIntoView({ behavior: 'smooth', block: 'center' });

    aplicarFiltro();
})();