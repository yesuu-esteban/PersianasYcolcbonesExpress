/* Pantalla: templates/fabrica/pedidos.html */

document.addEventListener('click', (e) => {
    const btn = e.target.closest('.btn-toggle-detalle');
    if (btn) {
        const fila = document.getElementById(btn.dataset.target);
        if (fila) {
            fila.classList.toggle('abierta');
            btn.classList.toggle('abierto');
            btn.textContent = fila.classList.contains('abierta') ? '▾' : '▸';
        }
        return;
    }
    const btnEliminar = e.target.closest('.btn-eliminar-pedido');
    if (btnEliminar) {
        document.getElementById('modalEliminarDesc').textContent = btnEliminar.dataset.desc;
        document.getElementById('formEliminar').action = btnEliminar.dataset.url;
        new bootstrap.Modal(document.getElementById('modalEliminar')).show();
    }
});