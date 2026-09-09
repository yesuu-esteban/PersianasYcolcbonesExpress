(function () {
    'use strict';

    function getToken() {
        return localStorage.getItem('authToken');
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
        const separador = url.includes('?') ? '&' : '?';
        return url + separador + 'token=' + encodeURIComponent(token);
    }

    // Intercepción de clics en enlaces <a>
    document.addEventListener('click', function (e) {
        const link = e.target.closest('a[href]');
        if (!link) return;
        const href = link.getAttribute('href');
        if (!href || href.startsWith('#') || href.startsWith('javascript:')) return;
        if (!esUrlInterna(href)) return;

        e.preventDefault();
        window.location.href = agregarTokenAUrl(href);
    });

    // Intercepción de envíos de formularios para inyectar token como input hidden
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