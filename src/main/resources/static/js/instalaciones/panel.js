/* Pantalla: templates/instalaciones/panel.html */

function tokenActual() {
    try { return localStorage.getItem('authToken'); } catch (e) { return null; }
}
function irA(url) {
    const t = tokenActual();
    window.location.href = t ? url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(t) : url;
}
function cabecerasAuth() {
    const t = tokenActual();
    return t ? { 'Authorization': 'Bearer ' + t } : {};
}

document.addEventListener('DOMContentLoaded', () => {
    // En celular la leyenda arranca cerrada para no ocupar pantalla.
    if (window.innerWidth < 768) document.getElementById('leyenda').removeAttribute('open');

    const el = document.getElementById('calendario');
    const instaladorId = el.dataset.instalador || '';
    const esMovil = window.innerWidth < 768;

    const calendario = new FullCalendar.Calendar(el, {
        locale: 'es',
        timeZone: 'local',
        firstDay: 1,
        initialView: esMovil ? 'listWeek' : (instaladorId ? 'timeGridWeek' : 'dayGridMonth'),
        headerToolbar: esMovil
            ? { left: 'prev,next today', center: 'title', right: 'listWeek,timeGridTresDias,dayGridMonth' }
            : { left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,listWeek' },
        views: { timeGridTresDias: { type: 'timeGrid', duration: { days: 3 }, buttonText: '3 días' } },
        buttonText: { today: 'Hoy', month: 'Mes', week: 'Semana', day: 'Día', list: 'Lista' },
        noEventsContent: 'No hay tareas en estas fechas',
        height: 'auto',
        nowIndicator: true,
        allDaySlot: false,
        slotMinTime: '06:00:00',
        slotMaxTime: '20:00:00',
        eventDisplay: 'block',
        // ── Formato colombiano: 12 horas con a. m. / p. m. ──
        slotLabelFormat: { hour: 'numeric', minute: '2-digit', hour12: true },
        eventTimeFormat: { hour: 'numeric', minute: '2-digit', hour12: true },
        dayHeaderFormat: { weekday: 'short', day: 'numeric', month: 'numeric', omitCommas: true },
        slotLabelInterval: '01:00',
        scrollTime: '07:00:00',

        events: (info, exito, fallo) => {
            const params = new URLSearchParams({ start: info.startStr, end: info.endStr });
            if (instaladorId) params.set('instaladorId', instaladorId);
            fetch('/instalaciones/eventos?' + params, { headers: cabecerasAuth(), credentials: 'same-origin' })
                .then(r => {
                    const tipo = r.headers.get('content-type') || '';
                    if (!r.ok || !tipo.includes('application/json')) throw new Error('Sesión vencida o error del servidor');
                    return r.json();
                })
                .then(datos => { document.getElementById('avisoCarga').style.display = 'none'; exito(datos); })
                .catch(err => { document.getElementById('avisoCarga').style.display = 'block'; fallo(err); });
        },
        eventClick: info => {
            info.jsEvent.preventDefault();
            irA(info.event.extendedProps.urlDetalle);
        },
        dateClick: info => {
            let url = '/instalaciones/nueva?fecha=' + encodeURIComponent(info.dateStr.substring(0, 16));
            if (instaladorId) url += '&instaladorId=' + instaladorId;
            irA(url);
        },
        eventDidMount: info => {
            const p = info.event.extendedProps;
            info.el.title = p.horario + ' · ' + p.tipo + ' · ' + p.estado + '\n' + p.instaladores
                + (p.direccion ? '\n' + p.direccion : '') + '\n' + p.origen;
        }
    });
    calendario.render();
});