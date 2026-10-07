/*
 * token-nav.js  —  lo cargan todas las pantallas internas (no la tienda virtual).
 *
 * Al iniciar sesión el token queda guardado en el navegador (localStorage, clave "authToken").
 * Este archivo se encarga de que ese token viaje en cada cambio de pantalla:
 *   - a los enlaces internos les agrega  ?token=...
 *   - a los formularios les agrega un campo oculto "token"
 *
 * Un enlace con la clase "no-token" se deja tal cual.
 */
(function () {
    'use strict';

    function getToken() {
        try { return localStorage.getItem('authToken'); } catch (e) { return null; }
    }

    function esUrlInterna(url) {
        try {
            const u = new URL(url, window.location.origin);
            return u.origin === window.location.origin;
        } catch (e) {
            return false;
        }
    }

    function agregarTokenAUrl(url) {
        const token = getToken();
        if (!token || url.includes('token=')) return url;
        const partes = url.split('#');                       // el #ancla siempre va al final
        const separador = partes[0].includes('?') ? '&' : '?';
        partes[0] += separador + 'token=' + encodeURIComponent(token);
        return partes.join('#');
    }

    // Enlaces <a>: se le agrega el token al enlace justo antes de que el navegador lo abra.
    // Como NO se cancela el clic, el navegador hace lo de siempre: respeta target="_blank",
    // Ctrl + clic, el clic con la rueda del ratón y los onclick="return confirm(...)".
    function prepararEnlace(e) {
        const link = e.target && e.target.closest ? e.target.closest('a[href]') : null;
        if (!link || link.classList.contains('no-token')) return;
        const href = link.getAttribute('href');
        if (!href || href.startsWith('#') || href.startsWith('javascript:')) return;
        if (!esUrlInterna(href)) return;
        link.setAttribute('href', agregarTokenAUrl(href));
    }
    document.addEventListener('click', prepararEnlace, true);
    document.addEventListener('auxclick', prepararEnlace, true);      // clic con la rueda
    document.addEventListener('contextmenu', prepararEnlace, true);   // "Abrir en pestaña nueva"

    // Formularios: se inyecta el token como campo oculto antes de enviar.
    document.addEventListener('submit', function (e) {
        const form = e.target;
        if (!(form instanceof HTMLFormElement)) return;
        const token = getToken();
        if (!token) return;

        let inputToken = form.querySelector('input[name="token"]');
        if (!inputToken) {
            inputToken = document.createElement('input');
            inputToken.type = 'hidden';
            inputToken.name = 'token';
            form.appendChild(inputToken);
        }
        inputToken.value = token;
    }, true);
})();