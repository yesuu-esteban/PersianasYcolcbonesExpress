(function () {
    async function compartirPdf(url, nombreArchivo, boton) {
        const textoOriginal = boton.innerHTML;
        boton.disabled = true;
        boton.innerHTML = '⏳ Preparando...';

        try {
            const token = localStorage.getItem('authToken');
            const headers = token ? { 'Authorization': 'Bearer ' + token } : {};

            const resp = await fetch(url, { headers });
            if (!resp.ok) throw new Error('HTTP ' + resp.status);

            const blob = await resp.blob();
            const archivo = new File([blob], nombreArchivo, { type: 'application/pdf' });

            if (navigator.canShare && navigator.canShare({ files: [archivo] })) {
                // Abre el panel nativo del sistema: WhatsApp, correo, Drive, Bluetooth,
                // o cualquier otra app instalada que la persona elija.
                await navigator.share({
                    title: 'Recibo de Caja',
                    text: 'Persianas y Colchones Express',
                    files: [archivo]
                });
            } else {
                // El navegador no soporta compartir archivos (típico en computador de
                // escritorio): se abre el PDF en una pestaña nueva para que la persona
                // lo descargue o lo imprima manualmente.
                window.open(url, '_blank');
            }
        } catch (e) {
            // AbortError = el usuario canceló el panel de compartir; no es un error real.
            if (e.name !== 'AbortError') {
                console.error('Error al compartir el recibo:', e);
                window.open(url, '_blank');
            }
        } finally {
            boton.disabled = false;
            boton.innerHTML = textoOriginal;
        }
    }

    // Delegación de eventos: cualquier botón con la clase .btn-compartir-pdf
    // y los atributos data-url / data-nombre queda funcional automáticamente.
    document.addEventListener('click', function (e) {
        const boton = e.target.closest('.btn-compartir-pdf');
        if (!boton) return;
        e.preventDefault();
        compartirPdf(boton.dataset.url, boton.dataset.nombre, boton);
    });
})();