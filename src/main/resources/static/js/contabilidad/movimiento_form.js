/* Pantalla: templates/contabilidad/movimiento_form.html
   Muestra los campos según el tipo (ingreso, egreso o traslado).
   Categoría en dos pasos: primero la categoría (solo las que sirven para ese tipo)
   y después lo que hay dentro de ella (subcategoría). */

(function () {
    const form = document.getElementById('formMovimiento');
    const radios = form.querySelectorAll('input[name="tipo"]');
    const selectCat = document.getElementById('categoriaId');
    const selectSub = document.getElementById('subcategoriaId');
    const bloqueCategoria = document.getElementById('bloqueCategoria');
    const bloqueSubcategoria = document.getElementById('bloqueSubcategoria');
    const bloqueDestino = document.getElementById('bloqueDestino');
    const selectDestino = document.getElementById('cuentaDestinoId');
    const etiquetaCuenta = document.getElementById('etiquetaCuenta');
    const etiquetaTercero = document.getElementById('etiquetaTercero');
    const valor = document.getElementById('valor');

    // Copias de todas las opciones, para volver a armar las listas cada vez que algo cambia
    const categorias = Array.from(selectCat.querySelectorAll('option')).filter(o => o.value).map(o => o.cloneNode(true));
    const subcategorias = Array.from(selectSub.querySelectorAll('option[data-categoria]')).map(o => o.cloneNode(true));
    const textoVacioSub = selectSub.options[0].textContent;

    function tipoActual() {
        const marcado = form.querySelector('input[name="tipo"]:checked');
        return marcado ? marcado.value : 'EGRESO';
    }

    /** Las subcategorías de una categoría. */
    function subsDe(catId) {
        return subcategorias.filter(o => o.dataset.categoria === catId);
    }

    /** Deja en la lista de categorías solo las del tipo (ingresos o egresos) que tienen algo adentro. */
    function armarCategorias(tipo) {
        const elegida = selectCat.value;
        const esIngreso = tipo === 'INGRESO';
        while (selectCat.options.length > 1) selectCat.remove(1);
        categorias.forEach(o => {
            if ((o.dataset.ingreso === 'true') === esIngreso && subsDe(o.value).length > 0) {
                selectCat.appendChild(o.cloneNode(true));
            }
        });
        const sigue = Array.from(selectCat.options).some(o => o.value === elegida);
        selectCat.value = sigue ? elegida : '';
        armarSubcategorias();
    }

    /** Deja en la segunda lista solo lo que hay dentro de la categoría elegida. */
    function armarSubcategorias() {
        const elegida = selectSub.value;
        const catId = selectCat.value;
        while (selectSub.options.length > 1) selectSub.remove(1);
        const opciones = catId ? subsDe(catId) : [];
        opciones.forEach(o => selectSub.appendChild(o.cloneNode(true)));
        selectSub.options[0].textContent = catId ? 'Elige…' : textoVacioSub;
        selectSub.disabled = !catId;
        const sigue = opciones.some(o => o.value === elegida);
        // Si la categoría tiene una sola opción, queda elegida de una vez
        selectSub.value = sigue ? elegida : (opciones.length === 1 ? opciones[0].value : '');
    }

    function aplicarTipo() {
        const tipo = tipoActual();
        const esTraslado = tipo === 'TRASLADO';
        bloqueCategoria.hidden = esTraslado;
        bloqueSubcategoria.hidden = esTraslado;
        selectCat.required = !esTraslado;
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
    selectCat.addEventListener('change', () => {
        armarSubcategorias();
        if (!selectSub.value) selectSub.focus();
    });
    valor.addEventListener('blur', formatearValor);
    aplicarTipo();
    if (valor.value) formatearValor();
})();