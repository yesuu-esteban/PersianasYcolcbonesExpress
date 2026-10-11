/* Pantalla: templates/tienda-admin/producto_form.html */

(function () {
    const tipo = document.getElementById('tipoPrecio');
    function actualizarTipo() {
        document.querySelectorAll('[data-solo]').forEach(el => { el.hidden = el.dataset.solo !== tipo.value; });
    }
    tipo.addEventListener('change', actualizarTipo);
    actualizarTipo();

    /* Medidas mínimas: se escriben en METROS ("0,30" o "0.30"). El servidor las guarda en centímetros
       en el campo oculto que está al lado de cada una. (No hay medida máxima.) */
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
    /* Riel: su "Ancho mínimo" es el mismo campo del ancho mínimo de las persianas (se copian entre sí) */
    const anchoMinRiel = document.getElementById('anchoMinRielM');
    const anchoMin = document.getElementById('anchoMinM');
    anchoMinRiel.value = anchoMin.value;
    anchoMinRiel.addEventListener('input', () => {
        anchoMin.value = anchoMinRiel.value;
        anchoMin.dispatchEvent(new Event('input'));
    });
    anchoMin.addEventListener('input', () => { if (document.activeElement === anchoMin) anchoMinRiel.value = anchoMin.value; });

    const precioBaston = document.getElementById('precioRielBastonMetro');
    const precioControl = document.getElementById('precioRielControlMetro');
    const ejemploRiel = document.getElementById('ejemploRiel');
    const textoRiel = ejemploRiel.textContent.trim();
    const pesosRiel = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
    function actualizarRiel() {
        const b = parseInt(String(precioBaston.value).replace(/\D/g, ''), 10);
        const c = parseInt(String(precioControl.value).replace(/\D/g, ''), 10);
        const partes = [];
        if (b > 0) partes.push('con bastón $' + pesosRiel.format(Math.round(b * 2.4)));
        if (c > 0) partes.push('con control $' + pesosRiel.format(Math.round(c * 2.4)));
        ejemploRiel.textContent = partes.length ? textoRiel + ' Ejemplo, un riel de 2,40 m: ' + partes.join(' · ') + '.' : textoRiel;
    }
    precioBaston.addEventListener('input', actualizarRiel);
    precioControl.addEventListener('input', actualizarRiel);
    actualizarRiel();

    document.querySelector('form[action="/tienda-admin/producto/guardar"]').addEventListener('submit', e => {
        if (tipo.value === 'RIEL') {
            const b = parseInt(String(precioBaston.value).replace(/\D/g, ''), 10) > 0;
            const c = parseInt(String(precioControl.value).replace(/\D/g, ''), 10) > 0;
            if (!b && !c) {
                e.preventDefault();
                precioBaston.classList.add('is-invalid');
                precioBaston.focus();
            }
            return;
        }
        if (tipo.value !== 'M2') return;
        const cm = {};
        medidas.forEach(el => { cm[el.dataset.medidaM] = aCm(el.value); });
        const malas = medidas.filter(el => cm[el.dataset.medidaM] === null);
        document.getElementById('avisoMedidas').hidden = malas.length === 0;
        if (malas.length === 0) return;
        e.preventDefault();
        malas.forEach(el => el.classList.add('is-invalid'));
        malas[0].focus();
    });

    /* Dropi: sus datos solo se piden si el producto lo despacha Dropi. Se muestra cuánto se gana por unidad. */
    const proveedor = document.getElementById('proveedor');
    const datosDropi = document.getElementById('datosDropi');
    const precioUnidad = document.getElementById('precioUnidad');
    const costoDropi = document.getElementById('costoProveedor');
    const codigoDropi = document.getElementById('codigoProveedor');
    const gananciaDropi = document.getElementById('gananciaDropi');
    const numero = el => parseInt(String(el.value).replace(/\D/g, ''), 10);
    function actualizarDropi() {
        const esDropi = proveedor.value === 'DROPI' && tipo.value === 'UNIDAD';
        datosDropi.hidden = proveedor.value !== 'DROPI';
        codigoDropi.required = esDropi;
        costoDropi.required = esDropi;
        const venta = numero(precioUnidad), costo = numero(costoDropi);
        gananciaDropi.classList.remove('mala');
        if (!(venta > 0) || !(costo > 0)) {
            gananciaDropi.textContent = 'Escribe el precio de venta y lo que cobra Dropi para ver cuánto ganas.';
        } else if (costo >= venta) {
            gananciaDropi.classList.add('mala');
            gananciaDropi.textContent = 'Así pierdes plata: el precio de venta debe ser mayor que lo que cobra Dropi.';
        } else {
            const g = venta - costo;
            gananciaDropi.textContent = 'Ganas unos $' + formatoPesos.format(g) + ' por unidad ('
                + Math.round(g * 100 / venta) + '% del precio), antes de envío y comisiones.';
        }
    }
    [proveedor, tipo].forEach(el => el.addEventListener('change', actualizarDropi));
    [precioUnidad, costoDropi].forEach(el => el.addEventListener('input', actualizarDropi));

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
    actualizarDropi();

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