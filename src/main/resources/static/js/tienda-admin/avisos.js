/* Aviso de pedidos nuevos de la tienda virtual (pagados y todavía en "Nuevo").
   Lo usan: templates/inicio/portal.html (aviso en la tarjeta "Tienda virtual") y el menú de
   templates/tienda-admin/* (numerito en "Pedidos").
   Pregunta al servidor al abrir la página y luego cada minuto, sin recargarla. */

(function () {
    'use strict';

    const CADA_MS = 60000;
    const avisosPortal = document.querySelectorAll('[data-pedidos-tienda-nuevos]');
    const contadoresMenu = document.querySelectorAll('[data-contador-nuevos]');
    if (!avisosPortal.length && !contadoresMenu.length) return;

    const tituloPagina = document.title;

    function token() {
        try { return localStorage.getItem('authToken'); } catch (e) { return null; }
    }

    function frase(n) {
        return n === 1 ? '1 pedido nuevo de la tienda virtual' : n + ' pedidos nuevos de la tienda virtual';
    }

    function pintar(datos) {
        const n = Number(datos.cantidad) || 0;
        document.title = n > 0 ? '(' + n + ') ' + tituloPagina : tituloPagina;

        avisosPortal.forEach(function (el) {
            el.textContent = '🔔 ' + frase(n);
            el.hidden = n === 0;
        });
        contadoresMenu.forEach(function (el) {
            el.textContent = n > 99 ? '99+' : String(n);
            el.hidden = n === 0;
            el.setAttribute('aria-label', frase(n));
        });
    }

    function consultar() {
        const t = token();
        const url = '/tienda-admin/api/pedidos-nuevos' + (t ? '?token=' + encodeURIComponent(t) : '');
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