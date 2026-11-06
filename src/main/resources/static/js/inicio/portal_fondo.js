/* Pantalla: templates/inicio/portal.html
   Fondo del portal con imágenes que se van cambiando suave (fundido + un leve acercamiento).
   - Las fotos están en /images/portal/. Para poner las suyas, reemplace fondo1.jpg … fondo6.jpg
     o agregue más nombres a la lista FOTOS (ej. '/images/portal/fondo7.jpg').
   - Si alguna foto no existe, simplemente se salta.
   - Si el celular/PC pide "reducir movimiento", se deja una sola foto fija. */
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
    const MS_TRANSICION = 1200;     // lo que dura el fundido (igual que en portal.css)

    const caja = document.getElementById('fondoPortal');
    if (!caja) return;
    const sinMovimiento = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    /** Carga cada foto; devuelve solo las que sí existen, en el mismo orden. */
    function cargar(url) {
        return new Promise(resolve => {
            const img = new Image();
            img.onload = () => resolve(url);
            img.onerror = () => resolve(null);
            img.src = url;
        });
    }

    Promise.all(FOTOS.map(cargar)).then(lista => {
        const fotos = lista.filter(Boolean);
        if (!fotos.length) return;          // sin fotos: queda el fondo oscuro de siempre

        const capas = fotos.map(url => {
            const d = document.createElement('div');
            d.className = 'fondo-capa';
            d.style.backgroundImage = 'url("' + url + '")';
            caja.appendChild(d);
            return d;
        });

        let actual = 0;
        capas[0].classList.add('activa');
        document.body.classList.add('con-fondo');
        if (capas.length < 2 || sinMovimiento) return;

        let reloj = null;
        function siguiente() {
            const antes = capas[actual];
            actual = (actual + 1) % capas.length;
            const nueva = capas[actual];
            // reinicia el acercamiento de la que entra
            nueva.classList.remove('activa');
            void nueva.offsetWidth;
            nueva.classList.add('activa');
            antes.classList.add('saliendo');
            setTimeout(() => antes.classList.remove('activa', 'saliendo'), MS_TRANSICION);
        }
        function arrancar() { if (!reloj) reloj = setInterval(siguiente, SEGUNDOS_POR_FOTO * 1000); }
        function parar() { clearInterval(reloj); reloj = null; }

        // No gastar batería cuando la pestaña no se está viendo
        document.addEventListener('visibilitychange', () => document.hidden ? parar() : arrancar());
        arrancar();
    });
})();