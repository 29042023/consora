// Menú lateral en pantallas chicas: el botón ☰ lo abre y el fondo oscuro lo cierra
(function () {
    const toggle = document.getElementById('sidebarToggle');
    const sidebar = document.getElementById('adminSidebar');
    const backdrop = document.getElementById('sidebarBackdrop');
    if (!toggle || !sidebar) return;

    function abrir(abierto) {
        sidebar.classList.toggle('open', abierto);
        if (backdrop) backdrop.classList.toggle('open', abierto);
    }
    toggle.addEventListener('click', () => abrir(!sidebar.classList.contains('open')));
    if (backdrop) backdrop.addEventListener('click', () => abrir(false));
})();

// Filtros de la lista de conversaciones (por ahora solo visual: marca el
// chip activo, todavía no filtra la lista de verdad — eso necesita, o un
// campo que identifique el rol directamente en la conversación, o volver
// a pedirle la página al servidor con ese filtro por query param).
document.querySelectorAll('.chat-filter-chip').forEach(function (chip) {
    chip.addEventListener('click', function () {
        document.querySelectorAll('.chat-filter-chip').forEach(c => c.classList.remove('active'));
        this.classList.add('active');
    });
});

// Nota: la selección de conversación (chat-list-item) ya NO se maneja acá.
// Ahora cada item es un link real (?chat_id=...) que recarga la página con
// esa conversación cargada del lado del servidor.
// Adjuntar archivo: al elegirlo se muestra su nombre arriba del cuadro de texto
// y el mensaje deja de ser obligatorio (se puede mandar solo el archivo).
const adjuntoInput = document.getElementById('adjuntoInput');
if (adjuntoInput) {
    const preview = document.getElementById('adjuntoPreview');
    const mensaje = document.getElementById('mensajeInput');

    function actualizarAdjunto() {
        const archivo = adjuntoInput.files[0];
        preview.hidden = !archivo;
        mensaje.required = !archivo;
        if (archivo) document.getElementById('adjuntoNombre').textContent = archivo.name;
    }

    adjuntoInput.addEventListener('change', actualizarAdjunto);
    document.getElementById('adjuntoQuitar').addEventListener('click', function () {
        adjuntoInput.value = '';
        actualizarAdjunto();
    });
}

// Al abrir una conversación se muestra el final (los mensajes más nuevos)
const listaMensajes = document.querySelector('.chat-messages');
if (listaMensajes) listaMensajes.scrollTop = listaMensajes.scrollHeight;

// Actualización automática: cada pocos segundos se pregunta al servidor si llegó
// algún mensaje nuevo. Si cambió, se vuelve a pedir la página y se reemplazan solo
// la lista de conversaciones y los mensajes (el cuadro de texto no se toca, así
// no se pierde lo que el usuario está escribiendo).
(function () {
    const shell = document.querySelector('.chat-shell');
    const estadoUrl = shell && shell.dataset.estadoUrl;
    if (!estadoUrl) return;

    const INTERVALO_MS = 3000;
    let version = null;
    let actualizando = false;

    // No pisar un formulario de invitación que el usuario está completando
    function ocupado() {
        const mensajes = document.querySelector('.chat-messages');
        if (!mensajes) return false;
        const foco = document.activeElement;
        if (mensajes.contains(foco) && foco.matches('input, select, textarea, button')) return true;
        return Array.from(mensajes.querySelectorAll('input[type=file]')).some(i => i.files.length);
    }

    async function recargarChat() {
        const resp = await fetch(window.location.href, { credentials: 'same-origin' });
        if (!resp.ok) return false;
        const nuevo = new DOMParser().parseFromString(await resp.text(), 'text/html');

        const lista = document.querySelector('.chat-list-scroll');
        const listaNueva = nuevo.querySelector('.chat-list-scroll');
        if (lista && listaNueva) {
            const scrollLista = lista.scrollTop;
            lista.innerHTML = listaNueva.innerHTML;
            lista.scrollTop = scrollLista;
        }

        const mensajes = document.querySelector('.chat-messages');
        const mensajesNuevos = nuevo.querySelector('.chat-messages');
        if (mensajes && mensajesNuevos) {
            // Si estaba viendo el final, se queda abajo para ver lo nuevo
            const alFinal = mensajes.scrollHeight - mensajes.scrollTop - mensajes.clientHeight < 80;
            const scrollMensajes = mensajes.scrollTop;
            mensajes.innerHTML = mensajesNuevos.innerHTML;
            mensajes.scrollTop = alFinal ? mensajes.scrollHeight : scrollMensajes;
        }
        return true;
    }

    async function revisar() {
        if (actualizando || document.hidden) return;
        actualizando = true;
        try {
            const resp = await fetch(estadoUrl, { credentials: 'same-origin', cache: 'no-store' });
            if (!resp.ok) return;
            const actual = (await resp.json()).version;
            if (version === null) {
                version = actual;
            } else if (actual !== version && !ocupado()) {
                if (await recargarChat()) version = actual;
            }
        } catch (e) {
            // Sin conexión por un momento: se reintenta en la próxima vuelta
        } finally {
            actualizando = false;
        }
    }

    revisar();
    setInterval(revisar, INTERVALO_MS);
    // Al volver a la pestaña, revisar enseguida
    document.addEventListener('visibilitychange', () => { if (!document.hidden) revisar(); });
})();
