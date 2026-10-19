/* Pantalla: templates/contabilidad/por_pagar.html
   - Pesos con puntos de mil (1500000 → 1.500.000).
   - En cada pago: primero la categoría y después lo que hay dentro de ella.
   - Avisa si el abono es mayor que lo que falta. */

(function () {
    const formato = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
    const digitos = texto => String(texto || '').replace(/\D/g, '');

    document.querySelectorAll('.campo-pesos').forEach(campo => {
        campo.addEventListener('blur', () => {
            const d = digitos(campo.value);
            campo.value = d ? formato.format(Number(d)) : '';
        });
    });

    document.querySelectorAll('.form-pago').forEach(form => {
        const selCat = form.querySelector('.sel-categoria');
        const selSub = form.querySelector('.sel-subcategoria');
        const monto = form.querySelector('input[name="monto"]');
        const todas = Array.from(selSub.querySelectorAll('option[data-categoria]')).map(o => o.cloneNode(true));
        const textoVacio = selSub.options[0].textContent;

        // Las categorías sin nada adentro no se muestran
        Array.from(selCat.options).forEach(o => {
            if (o.value && !todas.some(s => s.dataset.categoria === o.value)) o.remove();
        });

        function armar() {
            const catId = selCat.value;
            while (selSub.options.length > 1) selSub.remove(1);
            const opciones = catId ? todas.filter(o => o.dataset.categoria === catId) : [];
            opciones.forEach(o => selSub.appendChild(o.cloneNode(true)));
            selSub.options[0].textContent = catId ? '2. Elige…' : textoVacio;
            selSub.disabled = !catId;
            selSub.value = opciones.length === 1 ? opciones[0].value : '';
        }
        selCat.addEventListener('change', armar);
        armar();

        form.addEventListener('submit', e => {
            const maximo = Number(monto.dataset.maximo || 0);
            const valor = Number(digitos(monto.value));
            monto.setCustomValidity('');
            if (!valor) {
                monto.setCustomValidity('Escribe cuánto vas a pagar.');
            } else if (maximo && valor > maximo) {
                monto.setCustomValidity('Solo faltan $' + formato.format(maximo) + '.');
            }
            if (!monto.checkValidity()) {
                e.preventDefault();
                monto.reportValidity();
            }
        });
        monto.addEventListener('input', () => monto.setCustomValidity(''));
    });
})();