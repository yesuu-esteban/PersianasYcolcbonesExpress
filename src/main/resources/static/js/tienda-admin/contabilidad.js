/* Pantallas: templates/tienda-admin/contabilidad_movimientos.html y contabilidad_cuentas.html
   - Al elegir Gasto o Ingreso, la lista de categorías muestra solo las de ese tipo.
   - Los campos de plata se ven con puntos de miles mientras se escribe (150000 → 150.000). */

(function () {
    'use strict';

    // ── Categorías según el tipo ──
    const form = document.querySelector('.form-movimiento');
    if (form) {
        const categoria = form.querySelector('[data-categoria]');
        const etiquetaCuenta = form.querySelector('[data-etiqueta-cuenta]');
        const radios = form.querySelectorAll('input[type="radio"][name="tipo"]');

        function tipoElegido() {
            const r = form.querySelector('input[type="radio"][name="tipo"]:checked');
            return r ? r.value : 'EGRESO';
        }

        function actualizar() {
            const tipo = tipoElegido();
            if (categoria) {
                categoria.querySelectorAll('optgroup').forEach(function (g) {
                    const deEsteTipo = Array.prototype.some.call(g.querySelectorAll('option'), function (o) {
                        return o.dataset.tipo === tipo;
                    });
                    g.hidden = !deEsteTipo;
                    g.disabled = !deEsteTipo;
                });
                const elegida = categoria.selectedOptions[0];
                if (elegida && elegida.dataset.tipo && elegida.dataset.tipo !== tipo) categoria.value = '';
            }
            if (etiquetaCuenta) etiquetaCuenta.textContent = tipo === 'INGRESO' ? 'Entró a la cuenta' : 'Salió de la cuenta';
        }

        radios.forEach(function (r) { r.addEventListener('change', actualizar); });
        actualizar();
    }

    // ── Puntos de miles en los campos de plata ──
    function conPuntos(texto) {
        const negativo = String(texto).trim().startsWith('-');
        const digitos = String(texto).replace(/\D/g, '').replace(/^0+(?=\d)/, '');
        if (!digitos) return negativo ? '-' : '';
        return (negativo ? '-' : '') + digitos.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    }

    document.querySelectorAll('[data-plata]').forEach(function (input) {
        input.addEventListener('input', function () {
            const antes = input.value;
            const despues = conPuntos(antes);
            if (antes === despues) return;
            // Mantener el cursor en el mismo lugar contando solo los dígitos
            const pos = input.selectionStart || 0;
            const digitosAntes = antes.slice(0, pos).replace(/\D/g, '').length;
            input.value = despues;
            let i = 0, vistos = 0;
            while (i < despues.length && vistos < digitosAntes) {
                if (/\d/.test(despues[i])) vistos++;
                i++;
            }
            try { input.setSelectionRange(i, i); } catch (e) { /* algunos tipos de campo no lo permiten */ }
        });
    });
})();