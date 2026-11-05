/* Pantalla: templates/fabrica/precios.html (Precios fabricación).
   Trae la lista de insumos del inventario y muestra los que todavía no tienen
   precio de fabricación. Al tocar uno se llena el formulario "+ Agregar nuevo ítem de precio".
   La lista viene de /inventario/precios-venta/insumos-inventario. */
(function () {
    const form = document.getElementById('formNuevoPrecio');
    if (!form) return;
    const caja = document.getElementById('insumosSinPrecio');
    const chips = document.getElementById('chipsSinPrecio');
    const cuantos = document.getElementById('cuantosSinPrecio');
    const todos = document.getElementById('todosConPrecio');
    const lista = document.getElementById('listaInsumosInventario');
    const inputNombre = form.querySelector('[name="nombre"]');
    const selFormula = form.querySelector('[name="tipoCalculo"]');
    const inputPrecio = form.querySelector('[name="precioUnitario"]');

    const limpio = t => String(t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase();
    const esc = t => String(t == null ? '' : t).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    /** Nombres que ya tienen precio (los de la tabla de arriba). */
    function nombresConPrecio() {
        const set = new Set();
        document.querySelectorAll('form[action*="/actualizar"] [name="nombre"]').forEach(i => set.add(limpio(i.value)));
        return set;
    }

    function escoger(ins) {
        inputNombre.value = ins.nombre;
        // Por unidad → "Cantidad de piezas usadas"; por metro → "Largo (m)" si existe
        const opcion = ins.tieneMedida ? 'POR_LARGO' : 'POR_CANTIDAD';
        if ([...selFormula.options].some(o => o.value === opcion)) selFormula.value = opcion;
        document.getElementById('tarjetaAgregarPrecio').scrollIntoView({ behavior: 'smooth', block: 'center' });
        inputPrecio.focus();
    }

    function pintar(insumos) {
        const conPrecio = nombresConPrecio();
        const faltan = insumos.filter(i => !conPrecio.has(limpio(i.nombre)));
        lista.innerHTML = faltan.map(i => `<option value="${esc(i.nombre)}">`).join('');
        if (!insumos.length) return;
        if (!faltan.length) {
            caja.style.display = 'none';
            todos.style.display = '';
            return;
        }
        todos.style.display = 'none';
        caja.style.display = '';
        cuantos.textContent = faltan.length;
        chips.innerHTML = '';
        faltan.forEach(ins => {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'btn btn-sm btn-outline-secondary chip-insumo';
            b.textContent = ins.nombre + (ins.tieneMedida ? ' · por metro' : ' · por unidad');
            b.addEventListener('click', () => escoger(ins));
            chips.appendChild(b);
        });
    }

    const token = localStorage.getItem('authToken');
    fetch('/inventario/precios-venta/insumos-inventario' + (token ? '?token=' + encodeURIComponent(token) : ''))
        .then(r => r.ok ? r.json() : [])
        .then(pintar)
        .catch(() => { /* sin lista: igual se puede escribir el nombre a mano */ });
})();