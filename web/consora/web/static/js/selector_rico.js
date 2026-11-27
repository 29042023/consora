/*
 * Selector con buscador, foto e información para los <select class="selector-rico">.
 * El <select> original queda oculto y sigue siendo el que se envía en el formulario.
 * Cada <option> puede traer: data-img (URL de la foto), data-iniciales (si no hay foto),
 * data-sub (línea secundaria) y data-extra (dato a la derecha, ej. "⭐ 4,5").
 */
(function () {
    const normalizar = t => (t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

    function avatar(opcion, redondo) {
        const caja = document.createElement('span');
        caja.className = 'sr-avatar' + (redondo ? ' redondo' : '');
        if (opcion.dataset.img) {
            const img = document.createElement('img');
            img.src = opcion.dataset.img;
            img.alt = '';
            caja.appendChild(img);
        } else {
            caja.textContent = opcion.dataset.iniciales || opcion.textContent.trim().slice(0, 2).toUpperCase();
        }
        return caja;
    }

    function contenido(opcion, redondo) {
        const fila = document.createElement('span');
        fila.className = 'sr-fila';
        fila.appendChild(avatar(opcion, redondo));
        const textos = document.createElement('span');
        textos.className = 'sr-textos';
        const titulo = document.createElement('span');
        titulo.className = 'sr-titulo';
        titulo.textContent = opcion.textContent.trim();
        textos.appendChild(titulo);
        if (opcion.dataset.sub) {
            const sub = document.createElement('span');
            sub.className = 'sr-sub';
            sub.textContent = opcion.dataset.sub;
            textos.appendChild(sub);
        }
        fila.appendChild(textos);
        if (opcion.dataset.extra) {
            const extra = document.createElement('span');
            extra.className = 'sr-extra';
            extra.textContent = opcion.dataset.extra;
            fila.appendChild(extra);
        }
        return fila;
    }

    function armar(select) {
        const redondo = select.dataset.forma === 'redonda';
        const opciones = Array.from(select.options).filter(o => o.value);
        select.classList.add('d-none');

        const caja = document.createElement('div');
        caja.className = 'selector-rico-caja';
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'sr-boton form-select';
        const panel = document.createElement('div');
        panel.className = 'sr-panel d-none';
        const buscador = document.createElement('input');
        buscador.type = 'search';
        buscador.className = 'form-control form-control-sm sr-buscar';
        buscador.placeholder = select.dataset.buscar || 'Escribí para buscar…';
        const lista = document.createElement('div');
        lista.className = 'sr-lista';
        const vacio = document.createElement('div');
        vacio.className = 'sr-vacio d-none';
        vacio.textContent = 'No hay resultados.';
        panel.append(buscador, lista, vacio);
        caja.append(boton, panel);
        select.after(caja);

        const items = opciones.map(o => {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'sr-item';
            item.appendChild(contenido(o, redondo));
            item.dataset.valor = o.value;
            item.dataset.busqueda = normalizar(o.textContent + ' ' + (o.dataset.sub || ''));
            item.addEventListener('click', () => elegir(o.value));
            lista.appendChild(item);
            return item;
        });
        let activo = -1;

        function pintar() {
            boton.innerHTML = '';
            const elegida = opciones.find(o => o.value === select.value);
            if (elegida) boton.appendChild(contenido(elegida, redondo));
            else {
                const ph = document.createElement('span');
                ph.className = 'sr-placeholder';
                ph.textContent = select.options[0] && !select.options[0].value ? select.options[0].textContent : 'Elegí una opción';
                boton.appendChild(ph);
            }
            items.forEach(i => i.classList.toggle('elegido', i.dataset.valor === select.value));
        }
        function visibles() { return items.filter(i => !i.classList.contains('d-none')); }
        function marcar(n) {
            const v = visibles();
            v.forEach(i => i.classList.remove('activo'));
            activo = Math.max(0, Math.min(n, v.length - 1));
            if (v[activo]) { v[activo].classList.add('activo'); v[activo].scrollIntoView({ block: 'nearest' }); }
        }
        function filtrar() {
            const q = normalizar(buscador.value.trim());
            items.forEach(i => i.classList.toggle('d-none', q && !i.dataset.busqueda.includes(q)));
            vacio.classList.toggle('d-none', visibles().length > 0);
            marcar(0);
        }
        function abrir() {
            panel.classList.remove('d-none');
            caja.classList.add('abierto');
            buscador.value = '';
            filtrar();
            buscador.focus();
        }
        function cerrar() { panel.classList.add('d-none'); caja.classList.remove('abierto'); }
        function elegir(valor) {
            select.value = valor;
            select.dispatchEvent(new Event('change', { bubbles: true }));
            pintar();
            cerrar();
            boton.focus();
        }

        boton.addEventListener('click', () => panel.classList.contains('d-none') ? abrir() : cerrar());
        buscador.addEventListener('input', filtrar);
        buscador.addEventListener('keydown', e => {
            if (e.key === 'ArrowDown') { e.preventDefault(); marcar(activo + 1); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); marcar(activo - 1); }
            else if (e.key === 'Enter') { e.preventDefault(); const v = visibles()[activo]; if (v) elegir(v.dataset.valor); }
            else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); cerrar(); boton.focus(); }
        });
        document.addEventListener('click', e => { if (!caja.contains(e.target)) cerrar(); });
        // Si el formulario exige elegir, avisa como un select normal
        select.addEventListener('invalid', () => { boton.classList.add('is-invalid'); abrir(); });
        select.addEventListener('change', () => boton.classList.remove('is-invalid'));
        pintar();
    }

    document.querySelectorAll('select.selector-rico').forEach(armar);
})();
