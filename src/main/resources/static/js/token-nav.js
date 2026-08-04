    (function () {
        function getToken() {
            return sessionStorage.getItem('authToken');
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

        document.addEventListener('click', function (e) {
            const link = e.target.closest('a[href]');
            if (!link) return;
            const href = link.getAttribute('href');
            if (!href || href.startsWith('#') || href.startsWith('javascript:')) return;
            if (!esUrlInterna(href)) return;

            e.preventDefault();
            window.location.href = agregarTokenAUrl(href);
        });

        document.addEventListener('submit', function (e) {
            const form = e.target;
            if (!(form instanceof HTMLFormElement)) return;
            const token = getToken();
            if (!token) return;

            if (!form.querySelector('input[name="token"]')) {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'token';
                input.value = token;
                form.appendChild(input);
            }
        }, true);
    })();