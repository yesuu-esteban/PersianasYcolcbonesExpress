/* Pantalla: templates/tienda/fragmentos.html */

/** Carrito guardado en el navegador. Solo guarda la configuración; los precios siempre los calcula el servidor. */
const Carrito = {
    clave: 'pcx_carrito',
    leer() {
        try { const v = JSON.parse(localStorage.getItem(this.clave)); return Array.isArray(v) ? v : []; }
        catch (e) { return []; }
    },
    guardar(items) {
        try { localStorage.setItem(this.clave, JSON.stringify(items)); } catch (e) {}
        this.pintarContador();
    },
    agregar(item) { const items = this.leer(); items.push(item); this.guardar(items); },
    quitar(i) { const items = this.leer(); items.splice(i, 1); this.guardar(items); },
    cambiarCantidad(i, cantidad) {
        const items = this.leer();
        if (!items[i]) return;
        items[i].cantidad = Math.max(1, Math.min(50, cantidad));
        this.guardar(items);
    },
    vaciar() { this.guardar([]); },
    pintarContador() {
        const n = this.leer().reduce((suma, it) => suma + (Number(it.cantidad) || 1), 0);
        document.querySelectorAll('[data-contador-carrito]').forEach(el => {
            el.textContent = n;
            el.hidden = n === 0;
        });
    }
};

const formatoPesos = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
function pesos(valor) { return '$' + formatoPesos.format(Number(valor) || 0); }

function enlaceWhatsapp(mensaje) {
    return 'https://wa.me/' + WHATSAPP + '?text=' + encodeURIComponent(mensaje);
}

function mostrarToast(html) {
    const t = document.getElementById('toast');
    if (!t) return;
    t.innerHTML = html;
    t.classList.add('visible');
    clearTimeout(t._temporizador);
    t._temporizador = setTimeout(() => t.classList.remove('visible'), 4500);
}

function escaparHtml(texto) {
    return String(texto ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

document.addEventListener('DOMContentLoaded', () => Carrito.pintarContador());