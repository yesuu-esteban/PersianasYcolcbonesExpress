/* Pantalla: templates/tienda/checkout.html */

(function () {
    const items = Carrito.leer();
    if (items.length === 0) { window.location.replace('/tienda/carrito'); return; }

    // Puede haber uno o dos botones de pago: Wompi y Addi
    const botonesPagar = document.querySelectorAll('.btn-pagar');
    let todoOk = false;

    fetch('/tienda/api/carrito', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(items) })
        .then(r => r.json())
        .then(d => {
            todoOk = d.todoOk;
            document.getElementById('items').innerHTML = d.lineas.map(l => `
                <div class="item">
                    <span><strong>${escaparHtml(l.producto)} × ${l.cantidad}</strong><small>${escaparHtml(l.detalle)}</small>
                    ${l.ok ? '' : `<small style="color:#8F1013;display:block">${escaparHtml(l.mensaje)}</small>`}</span>
                    <span>${l.ok ? pesos(l.subtotal) : ''}</span>
                </div>`).join('');
            document.getElementById('total').textContent = pesos(d.total);
            if (!todoOk) botonesPagar.forEach(b => { b.disabled = true; });
            const resumen = d.lineas.map(l => `- ${l.producto}: ${l.detalle}, cantidad ${l.cantidad}${l.ok ? ', ' + pesos(l.subtotal) : ''}`).join('\n');
            document.getElementById('btnAsesor').href = enlaceWhatsapp('Hola, quiero hacer este pedido:\n' + resumen + '\nTotal: ' + pesos(d.total));
        })
        .catch(() => { document.getElementById('items').innerHTML = '<p class="aviso aviso-error">No pudimos cargar tu pedido. Recarga la página.</p>'; });

    document.getElementById('formPago').addEventListener('submit', e => {
        const form = e.target;
        if (!pagosActivos || !todoOk) { e.preventDefault(); return; }
        if (!form.checkValidity()) {
            e.preventDefault();
            form.reportValidity();
            return;
        }
        document.getElementById('campoCarrito').value = JSON.stringify(Carrito.leer());
        // El botón que se oprimió dice con qué se paga: "wompi" o "addi"
        const oprimido = e.submitter && e.submitter.classList.contains('btn-pagar') ? e.submitter : botonesPagar[0];
        document.getElementById('campoMedio').value = oprimido && oprimido.value === 'addi' ? 'addi' : 'wompi';
        botonesPagar.forEach(b => { b.disabled = true; });
        if (oprimido) oprimido.textContent = oprimido.value === 'addi' ? 'Abriendo Addi…' : 'Abriendo el pago…';
    });
})();