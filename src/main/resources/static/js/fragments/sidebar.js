/* Fragmento: templates/fragments/sidebar.html
   El menú de la izquierda está oculto para que la página use todo el ancho.
   Se abre con el botón ☰ y se cierra con ✕, tocando fuera del menú o con la tecla Esc. */

(function () {
    const menu = document.getElementById('appSidebar');
    const boton = document.getElementById('btnMenu');
    const fondo = document.querySelector('.app-sidebar-fondo');
    if (!menu || !boton) return;

    function abrir() {
        menu.classList.add('abierto');
        if (fondo) fondo.hidden = false;
        boton.setAttribute('aria-expanded', 'true');
        const primero = menu.querySelector('nav a');
        if (primero) primero.focus();
    }

    function cerrar() {
        if (!menu.classList.contains('abierto')) return;
        menu.classList.remove('abierto');
        if (fondo) fondo.hidden = true;
        boton.setAttribute('aria-expanded', 'false');
        boton.focus();
    }

    boton.addEventListener('click', () => {
        if (menu.classList.contains('abierto')) cerrar(); else abrir();
    });
    document.querySelectorAll('[data-cerrar-sidebar]').forEach(el => el.addEventListener('click', cerrar));
    document.addEventListener('keydown', e => { if (e.key === 'Escape') cerrar(); });
})();