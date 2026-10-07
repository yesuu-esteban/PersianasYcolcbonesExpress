/* Pantalla: templates/fabrica/cargar_insumo.html */

(function () {
    const form = document.getElementById('formEliminarInsumo');
    if (!form) return;

    form.addEventListener('submit', function (e) {
        const nombre = form.dataset.nombre || 'este insumo';
        const tieneMedida = form.dataset.tieneMedida === 'true';
        const stock = parseInt(form.dataset.stock || '0', 10);

        let mensaje = `¿Eliminar "${nombre}" del inventario? Esta acción no se puede deshacer.`;

        if (tieneMedida) {
            mensaje = `⚠ "${nombre}" puede tener piezas registradas.\n` +
                      `Si lo eliminas, también se borrarán TODAS sus piezas asociadas.\n\n` +
                      `¿Deseas continuar?`;
        } else if (stock > 0) {
            mensaje = `⚠ "${nombre}" todavía tiene ${stock} unidad(es) en stock.\n` +
                      `Si lo eliminas, se perderá ese registro de stock.\n\n` +
                      `¿Deseas continuar?`;
        }

        if (!window.confirm(mensaje)) {
            e.preventDefault();
        }
    });
})();

(function () {
    document.querySelectorAll('.btn-marcar-agotada').forEach(function (btn) {
        btn.addEventListener('click', function (e) {
            const form = btn.closest('form');
            const input = form.querySelector('input[name="largoRestante"]');
            if (!window.confirm('¿Marcar esta pieza como agotada (largo restante = 0)?')) {
                e.preventDefault();
                return;
            }
            if (input) input.value = '0';
        });
    });
})();