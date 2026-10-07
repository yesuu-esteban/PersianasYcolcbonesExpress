/* Pantalla: templates/instalaciones/mi_calendario.html */

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
    const esMovil = window.innerWidth < 768;
    if (esMovil) document.getElementById('leyenda').removeAttribute('open');
    const calendario = new FullCalendar.Calendar(document.getElementById('calendario'), {
        locale: 'es',
        timeZone: 'local',
        firstDay: 1,
        initialView: esMovil ? 'listWeek' : 'timeGridWeek',
        headerToolbar: esMovil
            ? { left: 'prev,next today', center: 'title', right: 'listWeek,timeGridTresDias,dayGridMonth' }
            : { left: 'prev,next today', center: 'title', right: 'timeGridWeek,dayGridMonth,listWeek' },
        views: { timeGridTresDias: { type: 'timeGrid', duration: { days: 3 }, buttonText: '3 días' } },
        buttonText: { today: 'Hoy', month: 'Mes', week: 'Semana', day: 'Día', list: 'Lista' },
        noEventsContent: 'No tienes tareas en estas fechas',
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
            fetch('/mi-calendario/eventos?' + params, { headers: cabecerasAuth(), credentials: 'same-origin' })
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
            irA('/mi-calendario/nueva?fecha=' + encodeURIComponent(info.dateStr.substring(0, 16)));
        },
        eventDidMount: info => {
            const p = info.event.extendedProps;
            info.el.title = p.horario + ' · ' + p.tipo + ' · ' + p.estado + '\n' + p.instaladores
                + (p.direccion ? '\n' + p.direccion : '') + '\n' + p.origen;
        }
    });
    calendario.render();
});