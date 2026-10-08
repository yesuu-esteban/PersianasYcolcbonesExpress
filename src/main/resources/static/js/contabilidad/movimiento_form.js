/* Pantalla: templates/contabilidad/movimiento_form.html
   Muestra los campos según el tipo (ingreso, egreso o traslado) y deja en la lista
   de categorías solo las que sirven para ese tipo. */

(function () {
    const form = document.getElementById('formMovimiento');
    const radios = form.querySelectorAll('input[name="tipo"]');
    const selectSub = document.getElementById('subcategoriaId');
    const bloqueCategoria = document.getElementById('bloqueCategoria');
    const bloqueDestino = document.getElementById('bloqueDestino');
    const selectDestino = document.getElementById('cuentaDestinoId');
    const etiquetaCuenta = document.getElementById('etiquetaCuenta');
    const etiquetaTercero = document.getElementById('etiquetaTercero');
    const valor = document.getElementById('valor');

    // Copia de todas las categorías, para volver a armar la lista cada vez que cambia el tipo
    const grupos = Array.from(selectSub.querySelectorAll('optgroup')).map(g => g.cloneNode(true));

    function tipoActual() {
        const marcado = form.querySelector('input[name="tipo"]:checked');
        return marcado ? marcado.value : 'EGRESO';
    }

    function armarCategorias(tipo) {
        const elegida = selectSub.value;
        selectSub.querySelectorAll('optgroup').forEach(g => g.remove());
        const esIngreso = tipo === 'INGRESO';
        grupos.forEach(g => {
            if ((g.dataset.ingreso === 'true') === esIngreso && g.children.length > 0) {
                selectSub.appendChild(g.cloneNode(true));
            }
        });
        const sigue = Array.from(selectSub.options).some(o => o.value === elegida);
        selectSub.value = sigue ? elegida : '';
    }

    function aplicarTipo() {
        const tipo = tipoActual();
        const esTraslado = tipo === 'TRASLADO';
        bloqueCategoria.hidden = esTraslado;
        selectSub.required = !esTraslado;
        bloqueDestino.hidden = !esTraslado;
        selectDestino.required = esTraslado;
        etiquetaCuenta.textContent = esTraslado ? 'Sale de' : (tipo === 'INGRESO' ? 'Cuenta donde entró' : 'Cuenta de donde salió');
        etiquetaTercero.textContent = tipo === 'INGRESO' ? 'De quién (cliente)' : (esTraslado ? 'Quién lo hizo' : 'A quién se le pagó');
        if (!esTraslado) armarCategorias(tipo);
    }

    // 1500000 → 1.500.000 al salir del campo
    function formatearValor() {
        const digitos = valor.value.replace(/\D/g, '');
        valor.value = digitos ? Number(digitos).toLocaleString('es-CO') : '';
    }

    radios.forEach(r => r.addEventListener('change', aplicarTipo));
    valor.addEventListener('blur', formatearValor);
    aplicarTipo();
    if (valor.value) formatearValor();
})();