/* Pantalla: templates/fabrica/nuevo_insumo.html */

let filaIndex = 1;

function agregarFila() {
    const contenedor = document.getElementById('contenedor-insumos');
    const idx = filaIndex;

    const fila = `
        <div class="row mb-2 align-items-center fila-insumo" data-indice="${idx}">
            <div class="col-md-3">
                <input type="text" name="nombres" class="form-control" placeholder="Ej: Tubo R24" required>
            </div>
            <div class="col-md-2 col-medida">
                <div class="form-check">
                    <input class="form-check-input campo-tiene-medida" type="checkbox" name="tieneMedida[${idx}]" value="true" id="medida${idx}">
                    <label class="form-check-label small" for="medida${idx}">Por longitud</label>
                </div>
            </div>
            <div class="col-md-2">
                <input type="number" step="0.001" name="largoInicial[${idx}]" class="form-control campo-largo campo-extra" placeholder="Largo en m">
                <input type="number" name="unidadesIniciales[${idx}]" class="form-control campo-unidades" placeholder="Unidades" value="0">
            </div>
            <div class="col-md-1">
                <input type="number" name="cantidadPiezas[${idx}]" class="form-control campo-piezas campo-extra" placeholder="Piezas" value="1" min="1">
            </div>
            <div class="col-md-3">
                <input type="text" name="descripciones" class="form-control" placeholder="Notas (opcional)">
            </div>
            <div class="col-md-1">
                <button type="button" class="btn btn-danger btn-sm"
                        onclick="this.closest('.fila-insumo').remove()">✕</button>
            </div>
        </div>`;

    contenedor.insertAdjacentHTML('beforeend', fila);
    filaIndex++;
    engancharEventosFila(contenedor.lastElementChild);
}

function engancharEventosFila(fila) {
    const checkbox = fila.querySelector('.campo-tiene-medida');
    const campoLargo = fila.querySelector('.campo-largo');
    const campoPiezas = fila.querySelector('.campo-piezas');
    const campoUnidades = fila.querySelector('.campo-unidades');

    const actualizar = () => {
        if (checkbox.checked) {
            campoLargo.classList.add('activo');
            campoPiezas.classList.add('activo');
            campoUnidades.style.display = 'none';
        } else {
            campoLargo.classList.remove('activo');
            campoPiezas.classList.remove('activo');
            campoUnidades.style.display = 'block';
        }
    };

    checkbox.addEventListener('change', actualizar);
    actualizar();
}

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.fila-insumo').forEach(engancharEventosFila);
});