/* Pantalla: templates/almacen/listado.html */

function toggleDescripcion(id, boton) {
    const el = document.getElementById('desc-' + id);
    el.classList.toggle('expandido');
    boton.textContent = el.classList.contains('expandido') ? 'Ver menos' : 'Ver más';
    actualizarBotonDescripcion(el);
}

function actualizarBotonDescripcion(el) {
    const boton = el.nextElementSibling;
    if (!boton || !boton.classList.contains('btn-desc-toggle')) return;
    if (el.classList.contains('expandido')) {
        boton.style.display = 'inline-block';
        return;
    }
    const desborda = el.scrollHeight > el.clientHeight + 1;
    boton.style.display = desborda ? 'inline-block' : 'none';
}

function actualizarTodosLosBotonesDescripcion() {
    document.querySelectorAll('.desc-texto').forEach(actualizarBotonDescripcion);
}

document.addEventListener('DOMContentLoaded', actualizarTodosLosBotonesDescripcion);
window.addEventListener('resize', actualizarTodosLosBotonesDescripcion);

const modalEliminarTienda = document.getElementById('modalEliminarTienda');
modalEliminarTienda.addEventListener('show.bs.modal', function (event) {
    const boton = event.relatedTarget;
    const id = boton.getAttribute('data-id');
    const cliente = boton.getAttribute('data-cliente');
    document.getElementById('modalEliminarTiendaCliente').textContent = cliente;
    document.getElementById('formEliminarTienda').action = '/almacen/eliminar/' + id;
});