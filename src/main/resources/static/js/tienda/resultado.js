/* Pantalla: templates/tienda/resultado.html */

(function () {
    // Pago aprobado: se vacía el carrito
    if (estado === 'APROBADA') Carrito.vaciar();

    // Pendiente: se vuelve a consultar cada 6 segundos (máximo 10 veces)
    if (estado === 'PENDIENTE' && hayTransaccion) {
        const clave = 'pcx_reintentos_' + location.pathname;
        const n = Number(sessionStorage.getItem(clave) || 0);
        if (n < 10) {
            sessionStorage.setItem(clave, n + 1);
            setTimeout(() => location.reload(), 6000);
        }
    }
})();