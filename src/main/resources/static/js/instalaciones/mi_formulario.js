/* Pantalla: templates/instalaciones/mi_formulario.html */

(function () {
    const lblTitulo = document.getElementById('lblTitulo');
    const lblDireccion = document.getElementById('lblDireccion');
    const inputTitulo = document.getElementById('titulo');

    function actualizar() {
        const r = document.querySelector('input[name="tipo"]:checked');
        const tipo = r ? r.value : 'COTIZACION';
        document.querySelectorAll('[data-tipos]').forEach(el => {
            el.hidden = !el.dataset.tipos.split(' ').includes(tipo);
        });
        const esOtro = tipo === 'OTRO';
        lblTitulo.textContent = esOtro ? '¿De qué se trata?' : 'Asunto (opcional)';
        inputTitulo.placeholder = esOtro ? 'Ej: cita médica, recoger material' : 'Ej: instalar 2 persianas en la sala';
        lblDireccion.textContent = esOtro ? 'Lugar (opcional)' : 'Dirección';
    }

    document.querySelectorAll('input[name="tipo"]').forEach(r => r.addEventListener('change', actualizar));
    actualizar();
})();