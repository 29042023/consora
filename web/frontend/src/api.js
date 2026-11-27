// Cliente de la API de Django: misma sesión que la web y token CSRF en los POST.

/** Dirección de la web en Django, para las secciones que todavía no están en React. */
export const DJANGO = import.meta.env.DEV ? 'http://127.0.0.1:8000' : ''

function cookie(nombre) {
  const fila = document.cookie.split('; ').find(c => c.startsWith(nombre + '='))
  return fila ? decodeURIComponent(fila.split('=')[1]) : ''
}

export class ErrorApi extends Error {
  constructor(status, errores) {
    super(errores.join(' '))
    this.status = status
    this.errores = errores
  }
}

async function pedir(url, { method = 'GET', datos } = {}) {
  const opciones = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } }
  if (method !== 'GET') {
    opciones.headers['X-CSRFToken'] = cookie('csrftoken')
    if (datos instanceof FormData) {
      opciones.body = datos
    } else {
      opciones.headers['Content-Type'] = 'application/json'
      opciones.body = JSON.stringify(datos || {})
    }
  }
  const r = await fetch('/api' + url, opciones)
  const json = await r.json().catch(() => ({}))
  if (!r.ok) {
    const mensajes = (json.mensajes || []).filter(m => m.tipo === 'error').map(m => m.texto)
    throw new ErrorApi(r.status, json.errores || (mensajes.length ? mensajes : [json.detail || 'Ocurrió un error. Probá de nuevo.']))
  }
  return json
}

export const api = {
  get: url => pedir(url),
  post: (url, datos) => pedir(url, { method: 'POST', datos }),
}
