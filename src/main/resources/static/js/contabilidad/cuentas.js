/* Pantalla: templates/contabilidad/cuentas.html
   Pesos con puntos de mil al salir del campo (1500000 → 1.500.000).
   Respeta el menos adelante para cuentas sobregiradas (-200000 → -200.000). */

(function () {
    const formato = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
    document.querySelectorAll('.campo-pesos').forEach(campo => {
        campo.addEventListener('blur', () => {
            const negativo = campo.value.trim().startsWith('-');
            const digitos = campo.value.replace(/\D/g, '');
            campo.value = digitos ? (negativo ? '-' : '') + formato.format(Number(digitos)) : '';
        });
    });
})();

/* "Cuadrar con lo que hay hoy": suma en vivo lo que se va escribiendo, para compararlo con el total del banco. */
(function () {
    const total = document.getElementById('totalCuadre');
    if (!total) return;
    const formato = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
    const campos = Array.from(document.querySelectorAll('.campo-real'));
    function sumar() {
        let suma = 0;
        campos.forEach(c => {
            const digitos = c.value.replace(/\D/g, '');
            if (!digitos) return;
            suma += (c.value.trim().startsWith('-') ? -1 : 1) * Number(digitos);
        });
        total.textContent = (suma < 0 ? '-$' : '$') + formato.format(Math.abs(suma));
    }
    campos.forEach(c => { c.addEventListener('input', sumar); c.addEventListener('blur', sumar); });
    sumar();
})();