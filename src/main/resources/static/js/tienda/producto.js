/* Pantalla: templates/tienda/producto.html */

(function () {
    const form = document.getElementById('configurador');
    const d = form.dataset;
    const porMetro = d.porMetro === 'true';
    const conMando = d.conMando === 'true';
    const esRiel = d.riel === 'true';

    const elAncho = document.getElementById('ancho');
    const elAlto = document.getElementById('alto');
    const elCantidad = document.getElementById('cantidad');
    const elColores = document.getElementById('colores');
    const elPrecio = document.getElementById('precioCaja');
    const btnAgregar = document.getElementById('btnAgregar');
    const btnAsesor = document.getElementById('btnAsesor');
    const persiana = document.getElementById('persiana');

    /* Colores conocidos → muestra visual. Los que no estén aquí se ven en gris. */
    const TONOS = {
        'blanco': '#FFFFFF', 'gris': '#9C9A97', 'fawn': '#C9B49A', 'vainilla': '#F1E3C1', 'beige': '#DCCBAE',
        'arena': '#D6C4A2', 'negro': '#222222', 'café': '#6B4A30', 'cafe': '#6B4A30', 'plata': '#C3C6C9',
        'madera natural': '#B9875B', 'madera': '#B9875B', 'crema': '#F3EAD7', 'azul': '#2F4E7A', 'verde': '#4E6B4A',
        'gris oscuro': '#5A5856', 'gris claro': '#CFCDCA', 'marfil': '#F5EFDF', 'chocolate': '#4A3123'
    };
    const tono = nombre => TONOS[(nombre || '').trim().toLowerCase()] || '#BDBAB6';

    let ultimaCotizacion = null;
    let temporizador = null;

    const telaSel = () => form.querySelector('input[name="tela"]:checked');
    const colorSel = () => form.querySelector('input[name="color"]:checked');
    const ladoSel = () => form.querySelector('input[name="lado"]:checked');
    /* Opciones de enrollable: solo existen si el producto las ofrece */
    const conCabezal = () => { const el = form.querySelector('input[name="cabezal"]:checked'); return !!el && el.value === 'si'; };
    const alContrario = () => { const el = form.querySelector('input[name="enrollado"]:checked'); return !!el && el.value === 'contrario'; };
    /* Riel de onda serena: bastón o control, y hacia dónde abre */
    const sistemaSel = () => form.querySelector('input[name="sistema"]:checked');
    const aperturaSel = () => form.querySelector('input[name="apertura"]:checked');
    const textoApertura = a => !a ? '' : (a.toLowerCase().startsWith('hacia') ? 'abre ' + a.toLowerCase() : 'abre hacia la ' + a.toLowerCase());
    const entero = el => { const v = parseInt(el && el.value, 10); return isNaN(v) ? null : v; };

    /* Las medidas se escriben en METROS ("1.20" o "1,20"). Al servidor se le envían en centímetros. */
    const medidaCm = el => {
        if (!el) return null;
        const v = parseFloat(String(el.value).trim().replace(',', '.'));
        return isNaN(v) || v <= 0 ? null : Math.round(v * 100);
    };
    const metros = cm => (cm / 100).toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    /* No hay medida máxima. Solo se avisa si es menor al mínimo, o si es tan grande (más de 20 m)
       que seguro se escribió en centímetros. Devuelve el aviso; si está bien, null. */
    const MEDIDA_IMPOSIBLE_CM = 2000;
    function fueraDeRango(anchoCm, altoCm) {
        const anchoMin = Number(d.anchoMin), altoMin = Number(d.altoMin);
        if (anchoCm > MEDIDA_IMPOSIBLE_CM || altoCm > MEDIDA_IMPOSIBLE_CM) {
            return 'Revisa la medida: escríbela en metros. Por ejemplo, 1 metro con 20 centímetros es 1,20.';
        }
        if (anchoCm < anchoMin) return `El ancho mínimo es ${metros(anchoMin)} m.`;
        if (altoCm !== null && altoCm < altoMin) return `El alto mínimo es ${metros(altoMin)} m.`;
        return null;
    }

    function pintarColores() {
        if (!porMetro) return;
        const tela = telaSel();
        const colores = tela ? tela.dataset.colores.split(',').map(c => c.trim()).filter(Boolean) : [];
        const anterior = colorSel() ? colorSel().value : null;
        document.getElementById('pasoColor').hidden = colores.length === 0;
        elColores.innerHTML = colores.map((c, i) => `
            <label class="color">
                <input type="radio" name="color" value="${escaparHtml(c)}" ${(anterior ? c === anterior : i === 0) ? 'checked' : ''}>
                <span class="muestra" style="background:${tono(c)}"></span>
                <span class="etiqueta">${escaparHtml(c)}</span>
            </label>`).join('');
        if (!colorSel() && elColores.querySelector('input')) elColores.querySelector('input').checked = true;
    }

    /* Dibuja la persiana a escala dentro de un lienzo de 150 px, con las cotas en metros */
    function pintarPersiana() {
        if (!persiana) return;
        const ancho = medidaCm(elAncho), alto = medidaCm(elAlto);
        const hayMedidas = ancho > 0 && alto > 0;
        const w = hayMedidas ? ancho : 120, h = hayMedidas ? alto : 150;
        const escala = 150 / Math.max(w, h);
        persiana.style.width = Math.max(24, Math.round(w * escala)) + 'px';
        persiana.style.height = Math.max(24, Math.round(h * escala)) + 'px';
        persiana.classList.toggle('fantasma', !hayMedidas);
        document.getElementById('cotaAncho').textContent = hayMedidas ? metros(ancho) + ' m' : 'ancho';
        document.getElementById('cotaAlto').textContent = hayMedidas ? metros(alto) + ' m' : 'alto';
        const color = colorSel();
        persiana.style.setProperty('--color-tela', color ? tono(color.value) : '#EDEBE8');
        const mando = document.getElementById('mando');
        if (mando) {
            const lado = ladoSel() ? ladoSel().value : 'Derecha';
            mando.className = 'mando ' + (lado === 'Izquierda' ? 'izquierda' : 'derecha');
        }
        persiana.classList.toggle('con-cabezal', conCabezal());
    }

    function resumenTexto() {
        const partes = [d.nombre];
        if (porMetro) {
            const tela = telaSel(), color = colorSel(), lado = ladoSel();
            if (tela) partes.push('tela ' + tela.dataset.nombre);
            if (color) partes.push('color ' + color.value);
            if (medidaCm(elAncho) && medidaCm(elAlto)) partes.push(metros(medidaCm(elAncho)) + ' × ' + metros(medidaCm(elAlto)) + ' m');
            if (conMando && lado) partes.push('mando a la ' + lado.value.toLowerCase());
            if (conCabezal()) partes.push('con cabezal');
            if (alContrario()) partes.push('enrollado al contrario (tela por delante)');
        }
        if (esRiel) {
            const sistema = sistemaSel(), apertura = aperturaSel();
            if (medidaCm(elAncho)) partes.push(metros(medidaCm(elAncho)) + ' m de ancho');
            if (sistema) partes.push(sistema.value === 'CONTROL' ? 'con control' : 'con bastón');
            if (apertura) partes.push(textoApertura(apertura.value));
        }
        partes.push('cantidad ' + (entero(elCantidad) || 1));
        return partes.join(', ');
    }

    function actualizarAsesor() {
        btnAsesor.href = enlaceWhatsapp('Hola, quiero cotizar: ' + resumenTexto() + '.');
    }

    function mostrarPrecio(c) {
        ultimaCotizacion = c && c.ok ? c : null;
        btnAgregar.disabled = !ultimaCotizacion;
        if (!c) {
            elPrecio.innerHTML = porMetro ? '<p class="indicacion">Escribe el ancho y el alto para ver el precio.</p>'
                : esRiel ? '<p class="indicacion">Escribe el ancho para ver el precio.</p>' : '';
            return;
        }
        if (!c.ok) {
            elPrecio.innerHTML = `<p class="problema">${escaparHtml(c.mensaje)} <a href="${enlaceWhatsapp('Hola, quiero cotizar: ' + resumenTexto() + '.')}" target="_blank" rel="noopener">Escribir por WhatsApp</a></p>`;
            return;
        }
        const cant = entero(elCantidad) || 1;
        let desglose = '';
        if (porMetro) {
            desglose = `${metros(medidaCm(elAncho))} × ${metros(medidaCm(elAlto))} m = ${Number(c.m2Reales).toLocaleString('es-CO')} m²`;
            if (c.m2 > c.m2Reales) desglose += `. Se cobra el mínimo de ${Number(c.m2).toLocaleString('es-CO')} m²`;
            desglose += '.';
            if (Number(c.precioCabezal) > 0) desglose += ` Incluye el cabezal: ${pesos(c.precioCabezal)}.`;
        }
        if (esRiel && sistemaSel()) {
            desglose = `${metros(medidaCm(elAncho))} m × ${pesos(sistemaSel().dataset.precio)} el metro `
                + (sistemaSel().value === 'CONTROL' ? '(con control).' : '(con bastón).');
        }
        if (cant > 1) desglose += ` ${cant} unidades de ${pesos(c.precioUnitario)}.`;
        elPrecio.innerHTML = `<div class="precio-grande">${pesos(c.subtotal)}</div><p class="desglose">${desglose}</p>`;
    }

    function cotizar() {
        actualizarAsesor();
        pintarPersiana();
        clearTimeout(temporizador);
        const anchoCm = medidaCm(elAncho), altoCm = medidaCm(elAlto);
        if (porMetro && !(anchoCm && altoCm)) { mostrarPrecio(null); return; }
        if (esRiel && !anchoCm) { mostrarPrecio(null); return; }
        if (porMetro || esRiel) {
            const aviso = fueraDeRango(anchoCm, porMetro ? altoCm : null);
            if (aviso) { mostrarPrecio({ ok: false, mensaje: aviso }); return; }
        }
        temporizador = setTimeout(async () => {
            const p = new URLSearchParams({ productoId: d.productoId, cantidad: entero(elCantidad) || 1 });
            if (porMetro) {
                if (telaSel()) p.set('telaId', telaSel().value);
                p.set('ancho', anchoCm);
                p.set('alto', altoCm);
                if (conCabezal()) p.set('cabezal', 'true');
            }
            if (esRiel) {
                p.set('ancho', anchoCm);
                if (sistemaSel()) p.set('sistema', sistemaSel().value);
            }
            try {
                const r = await fetch('/tienda/api/cotizar?' + p.toString());
                mostrarPrecio(await r.json());
            } catch (e) {
                mostrarPrecio({ ok: false, mensaje: 'No pudimos calcular el precio. Revisa tu conexión e inténtalo de nuevo.' });
            }
        }, 280);
    }

    function cambiarCantidad(delta) {
        elCantidad.value = Math.max(1, Math.min(50, (entero(elCantidad) || 1) + delta));
        cotizar();
    }

    form.addEventListener('change', e => {
        if (e.target.name === 'tela') pintarColores();
        cotizar();
    });
    form.addEventListener('input', e => { if (e.target.matches('#ancho, #alto, #cantidad')) cotizar(); });
    document.getElementById('menos').addEventListener('click', () => cambiarCantidad(-1));
    document.getElementById('mas').addEventListener('click', () => cambiarCantidad(1));

    form.addEventListener('submit', e => {
        e.preventDefault();
        if (!ultimaCotizacion) return;
        Carrito.agregar({
            productoId: Number(d.productoId),
            telaId: porMetro && telaSel() ? Number(telaSel().value) : null,
            color: porMetro && colorSel() ? colorSel().value : '',
            anchoCm: porMetro || esRiel ? medidaCm(elAncho) : null,
            altoCm: porMetro ? medidaCm(elAlto) : null,
            lado: porMetro && conMando && ladoSel() ? ladoSel().value : '',
            cantidad: entero(elCantidad) || 1,
            cabezal: porMetro && conCabezal(),
            contrario: porMetro && alContrario(),
            sistema: esRiel && sistemaSel() ? sistemaSel().value : null,
            apertura: esRiel && aperturaSel() ? aperturaSel().value : null
        });
        mostrarToast(`Agregado al carrito: ${escaparHtml(d.nombre)} <a href="/tienda/carrito">Ver carrito</a>`);
    });

    /* Texto de ayuda con las medidas mínimas de este producto, en metros */
    const ayuda = document.getElementById('ayudaMedidas');
    if (ayuda && esRiel && d.anchoMin) {
        ayuda.textContent = `Escribe el ancho en metros, desde ${metros(Number(d.anchoMin))} m.`;
    } else if (ayuda && d.anchoMin && d.altoMin) {
        ayuda.textContent = `Escribe las medidas en metros. Ancho desde ${metros(Number(d.anchoMin))} m y alto desde ${metros(Number(d.altoMin))} m.`;
    }

    pintarColores();
    cotizar();
})();