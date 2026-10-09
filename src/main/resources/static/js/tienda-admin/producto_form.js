/* Pantalla: templates/tienda-admin/producto_form.html */

(function () {
    const tipo = document.getElementById('tipoPrecio');
    function actualizarTipo() {
        document.querySelectorAll('[data-solo]').forEach(el => { el.hidden = el.dataset.solo !== tipo.value; });
    }
    tipo.addEventListener('change', actualizarTipo);
    actualizarTipo();

    /* Límites de medida: se escriben en METROS ("0,30" o "0.30"). El servidor los guarda en centímetros
       en el campo oculto que está al lado de cada uno. */
    const medidas = Array.from(document.querySelectorAll('[data-medida-m]'));
    const ocultoDe = el => document.querySelector('input[type="hidden"][name="' + el.dataset.medidaM + '"]');
    const aCm = texto => {
        const v = parseFloat(String(texto).trim().replace(',', '.'));
        return isNaN(v) || v <= 0 ? null : Math.round(v * 100);
    };
    medidas.forEach(el => {
        const oculto = ocultoDe(el);
        const cm = parseInt(oculto.value, 10);
        if (!isNaN(cm) && cm > 0) el.value = (cm / 100).toFixed(2).replace('.', ',');
        el.addEventListener('input', () => {
            const nuevo = aCm(el.value);
            oculto.value = nuevo === null ? '' : nuevo;
            el.classList.remove('is-invalid');
        });
    });
    document.querySelector('form[action="/tienda-admin/producto/guardar"]').addEventListener('submit', e => {
        if (tipo.value !== 'M2') return;
        const cm = {};
        medidas.forEach(el => { cm[el.dataset.medidaM] = aCm(el.value); });
        const malas = medidas.filter(el => cm[el.dataset.medidaM] === null);
        if (malas.length === 0 && cm.anchoMinCm >= cm.anchoMaxCm) malas.push(document.getElementById('anchoMaxM'));
        if (malas.length === 0 && cm.altoMinCm >= cm.altoMaxCm) malas.push(document.getElementById('altoMaxM'));
        document.getElementById('avisoMedidas').hidden = malas.length === 0;
        if (malas.length === 0) return;
        e.preventDefault();
        malas.forEach(el => el.classList.add('is-invalid'));
        malas[0].focus();
    });

    /* Cabezal: el valor por metro solo se pide si el producto ofrece cabezal */
    const ofreceCabezal = document.getElementById('ofreceCabezal');
    const precioCabezal = document.getElementById('precioCabezalMetro');
    const ejemploCabezal = document.getElementById('ejemploCabezal');
    const formatoPesos = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
    function actualizarCabezal() {
        document.getElementById('filaPrecioCabezal').hidden = !ofreceCabezal.checked;
        precioCabezal.required = ofreceCabezal.checked && tipo.value === 'M2';
        const valor = parseInt(String(precioCabezal.value).replace(/\D/g, ''), 10);
        ejemploCabezal.textContent = isNaN(valor) || valor <= 0
            ? 'Se suma al precio de la persiana: ancho × este valor.'
            : 'Ejemplo: una persiana de 1,50 m de ancho paga $' + formatoPesos.format(Math.round(valor * 1.5)) + ' más por el cabezal.';
    }
    ofreceCabezal.addEventListener('change', actualizarCabezal);
    precioCabezal.addEventListener('input', actualizarCabezal);
    tipo.addEventListener('change', actualizarCabezal);
    actualizarCabezal();

    const contenedor = document.getElementById('telas');
    document.getElementById('agregarTela').addEventListener('click', () => {
        contenedor.appendChild(document.getElementById('plantillaTela').content.cloneNode(true));
        contenedor.lastElementChild.querySelector('input[name="telaNombre"]').focus();
    });
    contenedor.addEventListener('click', e => {
        const b = e.target.closest('[data-quitar-tela]');
        if (!b) return;
        b.closest('.tela-fila').remove();
    });

    // Vista previa de la foto elegida
    document.getElementById('imagen').addEventListener('change', e => {
        const archivo = e.target.files[0];
        const vista = document.getElementById('vistaFoto');
        if (!archivo) return;
        vista.innerHTML = '<img alt="">';
        vista.querySelector('img').src = URL.createObjectURL(archivo);
    });
})();