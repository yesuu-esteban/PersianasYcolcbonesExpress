/* Aviso de pedidos nuevos de la tienda virtual.
   Lo usan: templates/almacen/listado.html (cuadro con la lista) y templates/inicio/portal.html (aviso en la tarjeta de Almacén).
   Pregunta al servidor al abrir la página y luego cada minuto, sin recargarla. */

(function () {
    'use strict';

    const CADA_MS = 60000;
    const caja = document.getElementById('avisoTiendaNuevos');                        // listado de Almacén
    const contadores = document.querySelectorAll('[data-pedidos-tienda-nuevos]');     // portal
    if (!caja && !contadores.length) return;

    const tituloPagina = document.title;

    function token() {
        try { return localStorage.getItem('authToken'); } catch (e) { return null; }
    }

    function frase(n) {
        return n === 1 ? '1 pedido nuevo de la tienda virtual' : n + ' pedidos nuevos de la tienda virtual';
    }

    function pesos(valor) {
        return '$' + Number(valor || 0).toLocaleString('es-CO', { maximumFractionDigits: 0 });
    }

    function elemento(etiqueta, clase, texto) {
        const el = document.createElement(etiqueta);
        if (clase) el.className = clase;
        if (texto != null) el.textContent = texto;   // textContent: lo que escribió el cliente nunca se interpreta como HTML
        return el;
    }

    function pintar(datos) {
        const n = Number(datos.cantidad) || 0;
        const pedidos = Array.isArray(datos.pedidos) ? datos.pedidos : [];

        document.title = n > 0 ? '(' + n + ') ' + tituloPagina : tituloPagina;

        contadores.forEach(function (el) {
            el.textContent = '🔔 ' + frase(n);
            el.hidden = n === 0;
        });

        if (!caja) return;
        caja.hidden = n === 0;
        if (n === 0) return;

        document.getElementById('avisoTiendaTitulo').textContent = '🔔 ' + frase(n);

        const lista = document.getElementById('avisoTiendaLista');
        lista.textContent = '';
        pedidos.forEach(function (p) {
            const li = elemento('li');
            li.appendChild(elemento('span', 'quien', p.cliente || 'Cliente sin nombre'));
            li.appendChild(elemento('span', 'dato', p.fecha || ''));
            li.appendChild(elemento('span', 'dato', pesos(p.total)));

            const filtro = p.cedula ? 'cedula=' + encodeURIComponent(p.cedula) : 'nombre=' + encodeURIComponent(p.cliente || '');
            const enlace = elemento('a', 'btn btn-sm btn-outline-light', 'Ver en la lista');
            enlace.href = '/almacen/listado?' + filtro;
            li.appendChild(enlace);
            lista.appendChild(li);
        });
        if (n > pedidos.length) {
            lista.appendChild(elemento('li', 'mas', 'y ' + (n - pedidos.length) + ' más'));
        }
    }

    function consultar() {
        const t = token();
        const url = '/almacen/api/pedidos-tienda-nuevos' + (t ? '?token=' + encodeURIComponent(t) : '');
        fetch(url, { credentials: 'same-origin', cache: 'no-store', headers: { 'Accept': 'application/json' } })
            .then(function (r) {
                const esJson = (r.headers.get('content-type') || '').indexOf('json') !== -1;
                return r.ok && esJson ? r.json() : null;
            })
            .then(function (datos) { if (datos) pintar(datos); })
            .catch(function () { /* sin conexión: se intenta de nuevo en el siguiente minuto */ });
    }

    consultar();
    setInterval(function () { if (!document.hidden) consultar(); }, CADA_MS);
    document.addEventListener('visibilitychange', function () { if (!document.hidden) consultar(); });
})();