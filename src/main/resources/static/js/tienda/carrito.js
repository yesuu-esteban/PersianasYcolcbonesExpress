/* Pantalla: templates/tienda/carrito.html */

(function () {
    const elLineas = document.getElementById('lineas');

    async function cargar() {
        const items = Carrito.leer();
        document.getElementById('cargando').hidden = true;
        if (items.length === 0) {
            document.getElementById('vacio').hidden = false;
            document.getElementById('contenido').hidden = true;
            return;
        }
        let datos;
        try {
            const r = await fetch('/tienda/api/carrito', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(items)
            });
            datos = await r.json();
        } catch (e) {
            elLineas.innerHTML = '<p class="aviso aviso-error">No pudimos cargar los precios. Revisa tu conexión y recarga la página.</p>';
            document.getElementById('contenido').hidden = false;
            return;
        }
        pintar(datos);
    }

    function pintar(datos) {
        document.getElementById('vacio').hidden = true;
        document.getElementById('contenido').hidden = false;
        elLineas.innerHTML = datos.lineas.map(l => `
            <article class="linea">
                <div class="miniatura ${l.imagenId ? '' : 'sin-foto'}">${l.imagenId ? `<img src="/tienda/img/${l.imagenId}" alt="">` : ''}</div>
                <div>
                    <h2>${l.slug ? `<a href="/tienda/producto/${encodeURIComponent(l.slug)}">${escaparHtml(l.producto)}</a>` : escaparHtml(l.producto)}</h2>
                    <p class="detalle">${escaparHtml(l.detalle)}</p>
                    ${l.ok ? '' : `<p class="problema">${escaparHtml(l.mensaje)}</p>`}
                    <div class="controles">
                        <div class="cantidad" role="group" aria-label="Cantidad">
                            <button type="button" data-accion="menos" data-i="${l.indice}" aria-label="Quitar una">−</button>
                            <span>${l.cantidad}</span>
                            <button type="button" data-accion="mas" data-i="${l.indice}" aria-label="Agregar una">+</button>
                        </div>
                        <button type="button" class="quitar" data-accion="quitar" data-i="${l.indice}">Quitar</button>
                    </div>
                </div>
                <div class="subtotal">${l.ok ? pesos(l.subtotal) : ''}</div>
            </article>`).join('');

        const unidades = datos.lineas.reduce((s, l) => s + (l.cantidad || 0), 0);
        document.getElementById('cantidadTotal').textContent = unidades;
        document.getElementById('total').textContent = pesos(datos.total);
        document.getElementById('avisoProblemas').hidden = datos.todoOk;
        const btnPagar = document.getElementById('btnPagar');
        btnPagar.toggleAttribute('aria-disabled', !datos.todoOk);
        btnPagar.style.pointerEvents = datos.todoOk ? '' : 'none';
        btnPagar.style.opacity = datos.todoOk ? '' : '.45';

        const resumen = datos.lineas.map(l => `- ${l.producto}: ${l.detalle}, cantidad ${l.cantidad}${l.ok ? ', ' + pesos(l.subtotal) : ''}`).join('\n');
        document.getElementById('btnAsesor').href = enlaceWhatsapp('Hola, quiero hacer este pedido:\n' + resumen + '\nTotal: ' + pesos(datos.total));
    }

    elLineas.addEventListener('click', e => {
        const b = e.target.closest('button[data-accion]');
        if (!b) return;
        const i = Number(b.dataset.i);
        const items = Carrito.leer();
        if (b.dataset.accion === 'quitar') Carrito.quitar(i);
        if (b.dataset.accion === 'menos') Carrito.cambiarCantidad(i, (Number(items[i]?.cantidad) || 1) - 1);
        if (b.dataset.accion === 'mas') Carrito.cambiarCantidad(i, (Number(items[i]?.cantidad) || 1) + 1);
        cargar();
    });

    cargar();
})();