/* Pantalla: templates/inicio/login.html */

// Si se llega al login, se borra cualquier token viejo que haya quedado en el navegador
// (por ejemplo, de antes de que el "Cerrar sesión" funcionara bien).
try { localStorage.removeItem('authToken'); } catch (e) {}
try { sessionStorage.removeItem('authToken'); } catch (e) {}

// Mostrar / ocultar contraseña (útil en el celular para revisar lo que se escribió)
const campoClave = document.getElementById('password');
const btnVer = document.getElementById('btnVer');
btnVer.addEventListener('click', () => {
    const oculta = campoClave.type === 'password';
    campoClave.type = oculta ? 'text' : 'password';
    btnVer.textContent = oculta ? 'Ocultar' : 'Ver';
    btnVer.setAttribute('aria-label', oculta ? 'Ocultar contraseña' : 'Mostrar contraseña');
    campoClave.focus();
});

// Evitar doble envío si tocan "Entrar" dos veces mientras carga
document.getElementById('formLogin').addEventListener('submit', () => {
    const btn = document.getElementById('btnEntrar');
    btn.disabled = true;
    btn.textContent = 'Entrando...';
});