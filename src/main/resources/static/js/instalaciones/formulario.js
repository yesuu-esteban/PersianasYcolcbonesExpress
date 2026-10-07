/* Pantalla: templates/instalaciones/formulario.html */

(function () {
    const selPedido = document.getElementById('pedidoTiendaId');   // null si no hay pedidos en bodega
    const selInst1 = document.getElementById('instaladorId');
    const selInst2 = document.getElementById('instalador2Id');
    const btnGuardar = document.getElementById('btnGuardar');
    const ficha = document.getElementById('fichaPedido');
    const fichaVacia = document.getElementById('fichaVacia');
    const lblTitulo = document.getElementById('lblTitulo');
    const lblDireccion = document.getElementById('lblDireccion');
    const inputTitulo = document.getElementById('titulo');

    function tipoActual() {
        const r = document.querySelector('input[name="tipo"]:checked');
        return r ? r.value : 'INSTALACION';
    }

    function texto(id, valor) {
        document.getElementById(id).textContent = (valor && valor.trim()) ? valor : '—';
    }

    function pintarFicha(op) {
        texto('fTitulo', '#' + op.value + ' · ' + (op.dataset.cliente || ''));
        texto('fCliente', op.dataset.cliente);
        texto('fCedula', op.dataset.cedula);
        texto('fTelefono', op.dataset.telefono);
        texto('fDireccion', op.dataset.direccion);
        texto('fEntrega', op.dataset.entrega);
        texto('fVendedor', op.dataset.vendedor);
        texto('fDescripcion', op.dataset.descripcion);

        const lista = document.getElementById('fProductos');
        lista.innerHTML = '';
        const productos = (op.dataset.productos || '').split('|').filter(x => x.trim());
        if (productos.length === 0) {
            const li = document.createElement('li');
            li.textContent = 'Sin productos registrados.';
            lista.appendChild(li);
        } else {
            productos.forEach(p => {
                const li = document.createElement('li');
                li.textContent = p;
                lista.appendChild(li);
            });
        }
    }

    function actualizar() {
        const tipo = tipoActual();
        const esInstalacion = tipo === 'INSTALACION';

        document.querySelectorAll('[data-tipos]').forEach(el => {
            el.hidden = !el.dataset.tipos.split(' ').includes(tipo);
        });

        const esOtro = tipo === 'OTRO';
        lblTitulo.textContent = esOtro ? '¿De qué se trata?' : 'Asunto (opcional)';
        inputTitulo.placeholder = esOtro ? 'Ej: recoger material en la fábrica' : 'Ej: revisar motor de la persiana de la sala';
        lblDireccion.textContent = esOtro ? 'Lugar (opcional)' : 'Dirección';

        // Instalación: el pedido es obligatorio; sin pedidos en bodega no se puede guardar.
        if (selPedido) selPedido.required = esInstalacion;
        btnGuardar.disabled = esInstalacion && !selPedido;

        const op = selPedido ? selPedido.options[selPedido.selectedIndex] : null;
        const conPedido = esInstalacion && op && op.value !== '';
        ficha.hidden = !conPedido;
        fichaVacia.hidden = !(esInstalacion && selPedido && !conPedido);
        if (conPedido) pintarFicha(op);
    }

    // No dejar elegir la misma persona como primer y segundo instalador.
    function sincronizarInstaladores() {
        Array.from(selInst2.options).forEach(o => {
            o.disabled = o.value !== '' && o.value === selInst1.value;
        });
        if (selInst2.value !== '' && selInst2.value === selInst1.value) selInst2.value = '';
    }

    document.querySelectorAll('input[name="tipo"]').forEach(r => r.addEventListener('change', actualizar));
    if (selPedido) selPedido.addEventListener('change', actualizar);
    selInst1.addEventListener('change', sincronizarInstaladores);
    actualizar();
    sincronizarInstaladores();
})();