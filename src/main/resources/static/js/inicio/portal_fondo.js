/* Pantalla: templates/inicio/portal.html
   Fondo del portal con fotos que se van cambiando con una transición (fundido + leve acercamiento).
   - Las fotos están en /images/portal/ (fondo1.jpg … fondo6.jpg). Para agregar más,
     ponga el nombre en la lista FOTOS (ej. '/images/portal/fondo7.jpg').
   - Si alguna foto no existe, simplemente se salta.
   - En la consola del navegador (F12) sale cuántas fotos cargaron, para revisar. */
(function () {
    const FOTOS = [
        '/images/portal/fondo1.jpg',
        '/images/portal/fondo2.jpg',
        '/images/portal/fondo3.jpg',
        '/images/portal/fondo4.jpg',
        '/images/portal/fondo5.jpg',
        '/images/portal/fondo6.jpg'
    ];
    const SEGUNDOS_POR_FOTO = 3;   // cada cuánto cambia la foto
    const MS_TRANSICION = 1200;    // lo que dura el fundido (igual que en portal.css)

    const caja = document.getElementById('fondoPortal');
    if (!caja) return;

    const capas = [];              // solo las fotos que sí cargaron, en orden
    let actual = -1;
    let reloj = null;

    function mostrar(indice) {
        const anterior = actual >= 0 ? capas[actual] : null;
        actual = indice;
        const nueva = capas[actual];
        nueva.classList.remove('activa', 'saliendo');
        void nueva.offsetWidth;           // reinicia el acercamiento de la que entra
        nueva.classList.add('activa');
        if (anterior && anterior !== nueva) {
            anterior.classList.add('saliendo');
            setTimeout(() => anterior.classList.remove('activa', 'saliendo'), MS_TRANSICION);
        }
    }

    function siguiente() {
        if (capas.length < 2) return;
        mostrar((actual + 1) % capas.length);
    }

    function arrancar() {
        if (!reloj) reloj = setInterval(siguiente, SEGUNDOS_POR_FOTO * 1000);
    }
    function parar() {
        clearInterval(reloj);
        reloj = null;
    }

    let pendientes = FOTOS.length;
    const listas = new Array(FOTOS.length).fill(null);

    function terminar() {
        listas.forEach(d => { if (d) capas.push(d); });   // en el orden de FOTOS
        capas.forEach(d => caja.appendChild(d));
        console.log('[Fondo portal] fotos cargadas: ' + capas.length + ' de ' + FOTOS.length);
        if (!capas.length) return;
        document.body.classList.add('con-fondo');
        mostrar(0);
        arrancar();
    }

    FOTOS.forEach((url, i) => {
        const img = new Image();
        img.onload = () => {
            const d = document.createElement('div');
            d.className = 'fondo-capa';
            d.style.backgroundImage = 'url("' + url + '")';
            listas[i] = d;
            if (--pendientes === 0) terminar();
        };
        img.onerror = () => {
            console.warn('[Fondo portal] no se encontró ' + url);
            if (--pendientes === 0) terminar();
        };
        img.src = url;
    });

    // No gastar batería cuando la pestaña no se está viendo
    document.addEventListener('visibilitychange', () => {
        if (!capas.length) return;
        document.hidden ? parar() : arrancar();
    });
})();