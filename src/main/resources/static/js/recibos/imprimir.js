/* Pantalla: templates/recibos/imprimir.html */

// ── Control de pestañas del modal ──
let tabFirmaActiva = 'dibujar';

function cambiarTabFirma(tab) {
    tabFirmaActiva = tab;
    document.getElementById('tabDibujarBtn').classList.toggle('activo', tab === 'dibujar');
    document.getElementById('tabEscribirBtn').classList.toggle('activo', tab === 'escribir');
    document.getElementById('panelDibujar').classList.toggle('activo', tab === 'dibujar');
    document.getElementById('panelEscribir').classList.toggle('activo', tab === 'escribir');
}

function abrirModalFirma() {
    document.getElementById('modalFirmaOverlay').classList.add('abierto');
}
function cerrarModalFirma() {
    document.getElementById('modalFirmaOverlay').classList.remove('abierto');
}

// ── Canvas de firma dibujada (funciona con mouse y con touch) ──
const canvas = document.getElementById('canvasFirma');
const ctx = canvas.getContext('2d');
let dibujando = false;
let hayTrazo = false;

ctx.lineWidth = 2.5;
ctx.lineCap = 'round';
ctx.strokeStyle = '#1a1a1a';

function posicionRelativa(e) {
    const rect = canvas.getBoundingClientRect();
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;
    return {
        x: (clientX - rect.left) * (canvas.width / rect.width),
        y: (clientY - rect.top) * (canvas.height / rect.height)
    };
}

function iniciarTrazo(e) {
    e.preventDefault();
    dibujando = true;
    hayTrazo = true;
    const p = posicionRelativa(e);
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
}
function continuarTrazo(e) {
    if (!dibujando) return;
    e.preventDefault();
    const p = posicionRelativa(e);
    ctx.lineTo(p.x, p.y);
    ctx.stroke();
}
function terminarTrazo() { dibujando = false; }

canvas.addEventListener('mousedown', iniciarTrazo);
canvas.addEventListener('mousemove', continuarTrazo);
window.addEventListener('mouseup', terminarTrazo);
canvas.addEventListener('touchstart', iniciarTrazo, { passive: false });
canvas.addEventListener('touchmove', continuarTrazo, { passive: false });
canvas.addEventListener('touchend', terminarTrazo);

function limpiarCanvas() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    hayTrazo = false;
}

// ── Firma escrita: vista previa en vivo con fuente cursiva ──
const inputNombre = document.getElementById('inputNombreFirma');
const previewEscrita = document.getElementById('previewFirmaEscrita');
inputNombre.addEventListener('input', () => {
    previewEscrita.textContent = inputNombre.value.trim() || 'Vista previa de tu firma';
});

// Convierte el texto escrito en una imagen PNG usando la misma fuente cursiva
function generarImagenDeTexto(texto) {
    const c = document.createElement('canvas');
    c.width = 440;
    c.height = 180;
    const cc = c.getContext('2d');
    cc.fillStyle = '#ffffff';
    cc.fillRect(0, 0, c.width, c.height);
    cc.fillStyle = '#1a1a1a';
    cc.font = "48px 'Dancing Script', cursive";
    cc.textAlign = 'center';
    cc.textBaseline = 'middle';
    cc.fillText(texto, c.width / 2, c.height / 2);
    return c.toDataURL('image/png');
}

// ── Guardar: toma la pestaña activa y envía la imagen resultante ──
function guardarFirma() {
    let dataUrl = null;

    if (tabFirmaActiva === 'dibujar') {
        if (!hayTrazo) {
            alert('Dibuja tu firma antes de guardar.');
            return;
        }
        dataUrl = canvas.toDataURL('image/png');
    } else {
        const nombre = inputNombre.value.trim();
        if (!nombre) {
            alert('Escribe tu nombre antes de guardar.');
            return;
        }
        dataUrl = generarImagenDeTexto(nombre);
    }

    document.getElementById('inputFirmaBase64').value = dataUrl;

    const token = localStorage.getItem('authToken');
    const form = document.getElementById('formFirma');
    if (token && !form.querySelector('input[name="token"]')) {
        const inputToken = document.createElement('input');
        inputToken.type = 'hidden';
        inputToken.name = 'token';
        inputToken.value = token;
        form.appendChild(inputToken);
    }

    form.submit();
}