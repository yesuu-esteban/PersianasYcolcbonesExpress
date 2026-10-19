/* Pantalla: templates/contabilidad/reportes.html
   - Muestra u oculta las categorías según el tipo de resumen (general o "solo lo que yo escoja").
   - Marcar una categoría marca todo lo que tiene adentro; si solo hay algunas marcadas, queda a medias.
   - Buscar categorías, marcar todo / quitar todo y el contador de lo escogido.
   - Atajos de fechas (este mes, mes pasado…).
   - Antes de enviar: revisa que haya algo escogido y que las fechas tengan sentido. */

(function () {
    const form = document.getElementById('formReporte');
    if (!form) return;
    const bloque = document.getElementById('bloquePersonalizado');
    const contador = document.getElementById('contadorSubs');
    const aviso = document.getElementById('avisoForm');
    const pasoNombre = document.getElementById('pasoNombre');
    const desde = document.getElementById('desde');
    const hasta = document.getElementById('hasta');
    const buscar = document.getElementById('buscarCategoria');

    const sinTildes = t => String(t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

    function esPersonalizado() {
        const r = form.querySelector('input[name="modo"]:checked');
        return !!r && r.value === 'personalizado';
    }

    /** General: se esconden las categorías y no se envían (así el enlace queda limpio). */
    function aplicarModo() {
        const personalizado = esPersonalizado();
        bloque.hidden = !personalizado;
        bloque.querySelectorAll('input[name="sub"], input[name="cuenta"]').forEach(i => { i.disabled = !personalizado; });
        form.querySelectorAll('.opcion-modo').forEach(l => l.classList.toggle('activa', l.querySelector('input').checked));
        if (pasoNombre) pasoNombre.textContent = (personalizado ? '4' : '3') + '. Nombre del resumen (opcional)';
        ocultarAviso();
    }

    function subsDe(cat) {
        return Array.from(bloque.querySelectorAll('.chk-sub[data-cat="' + cat + '"]'));
    }

    /** La casilla de la categoría: marcada si están todas, a medias si están algunas. */
    function actualizarCategoria(cat) {
        const chk = bloque.querySelector('.chk-cat[data-cat="' + cat + '"]');
        if (!chk) return;
        const subs = subsDe(cat);
        const marcadas = subs.filter(s => s.checked).length;
        chk.checked = marcadas > 0 && marcadas === subs.length;
        chk.indeterminate = marcadas > 0 && marcadas < subs.length;
        chk.closest('.cat-card').classList.toggle('con-algo', marcadas > 0);
    }

    function contar() {
        const n = bloque.querySelectorAll('.chk-sub:checked').length;
        contador.textContent = n === 0 ? 'Nada escogido todavía'
            : (n === 1 ? '1 subcategoría escogida' : n + ' subcategorías escogidas');
        if (n > 0) ocultarAviso();
    }

    bloque.querySelectorAll('.chk-cat').forEach(chk => {
        chk.addEventListener('change', () => {
            subsDe(chk.dataset.cat).forEach(s => { s.checked = chk.checked; });
            actualizarCategoria(chk.dataset.cat);
            contar();
        });
        actualizarCategoria(chk.dataset.cat);
    });
    bloque.querySelectorAll('.chk-sub').forEach(s => s.addEventListener('change', () => {
        actualizarCategoria(s.dataset.cat);
        contar();
    }));

    // Marcar / quitar todo (solo lo que se ve si se está buscando)
    function marcarVisibles(valor) {
        bloque.querySelectorAll('.cat-card').forEach(card => {
            if (card.hidden) return;
            card.querySelectorAll('.chk-sub').forEach(s => { s.checked = valor; });
            const chk = card.querySelector('.chk-cat');
            if (chk) actualizarCategoria(chk.dataset.cat);
        });
        contar();
    }
    document.getElementById('marcarTodo').addEventListener('click', () => marcarVisibles(true));
    document.getElementById('quitarTodo').addEventListener('click', () => marcarVisibles(false));

    // Buscar: deja ver las categorías cuyo nombre o subcategorías tienen lo escrito
    if (buscar) {
        buscar.addEventListener('input', () => {
            const q = sinTildes(buscar.value.trim());
            bloque.querySelectorAll('.cat-card').forEach(card => {
                card.hidden = q !== '' && !sinTildes(card.textContent).includes(q);
            });
            bloque.querySelectorAll('.grupo-cats').forEach(g => {
                g.hidden = !Array.from(g.querySelectorAll('.cat-card')).some(c => !c.hidden);
            });
        });
    }

    // Atajos de fechas
    const atajos = Array.from(form.querySelectorAll('.atajo'));
    function marcarAtajo() {
        atajos.forEach(b => b.classList.toggle('activo', b.dataset.desde === desde.value && b.dataset.hasta === hasta.value));
    }
    atajos.forEach(b => b.addEventListener('click', () => {
        const min = desde.getAttribute('min');
        desde.value = min && b.dataset.desde < min ? min : b.dataset.desde;
        hasta.value = b.dataset.hasta;
        marcarAtajo();
        ocultarAviso();
    }));
    desde.addEventListener('change', marcarAtajo);
    hasta.addEventListener('change', marcarAtajo);

    // Revisar antes de enviar (sirve para "Ver", "Excel" y "PDF")
    function mostrarAviso(texto) {
        aviso.textContent = texto;
        aviso.hidden = false;
    }
    function ocultarAviso() {
        aviso.hidden = true;
        aviso.textContent = '';
    }
    form.addEventListener('submit', e => {
        if (!desde.value || !hasta.value) {
            e.preventDefault();
            mostrarAviso('Escoge desde qué día y hasta qué día.');
        } else if (desde.value > hasta.value) {
            e.preventDefault();
            mostrarAviso('La fecha "desde" no puede ser después de la fecha "hasta".');
        } else if (esPersonalizado() && bloque.querySelectorAll('.chk-sub:checked').length === 0) {
            e.preventDefault();
            mostrarAviso('Escoge al menos una categoría o subcategoría.');
            bloque.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    });

    form.querySelectorAll('input[name="modo"]').forEach(r => r.addEventListener('change', aplicarModo));
    aplicarModo();
    contar();
    marcarAtajo();

    // Si ya hay un reporte, se baja hasta él
    const reporte = document.getElementById('reporte');
    if (reporte) reporte.scrollIntoView({ behavior: 'smooth', block: 'start' });
})();