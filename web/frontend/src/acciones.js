import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from './api'
import { useAvisos } from './componentes/Layout'

/** Traduce a dónde redirigió la vista de Django a la ruta equivalente de React (si existe). */
export function rutaReact(destino) {
  if (!destino) return null
  const url = new URL(destino, window.location.origin)
  const reglas = [
    [/^\/panel\/votaciones\/(\d+)\/$/, m => `/votaciones/${m[1]}`],
    [/^\/panel\/reuniones\/(\d+)\/$/, m => `/reuniones/${m[1]}`],
    [/^\/panel\/votaciones\/$/, () => '/votaciones'],
    [/^\/panel\/reuniones\/$/, () => '/reuniones'],
    [/^\/panel\/contratos\/$/, () => '/contratos'],
    [/^\/panel\/agenda\/$/, () => '/agenda'],
    [/^\/panel\/historial\/$/, () => '/historial'],
  ]
  for (const [regla, ruta] of reglas) {
    const m = url.pathname.match(regla)
    if (m) return ruta(m) + url.search
  }
  return null
}

/**
 * Manda una acción a la API como formulario (las procesa la vista de Django) y muestra sus mensajes.
 * campos: { accion: 'votar', voto: 'si', proveedores: [1, 2] }  ·  archivos: { doc: [File, ...] }
 * Devuelve true si salió bien. Si la vista redirige a otra pantalla (crear, eliminar), navega ahí.
 */
export function useAccion(url, alTerminar) {
  const avisar = useAvisos()
  const navigate = useNavigate()
  const [ocupado, setOcupado] = useState(false)

  const enviar = useCallback(async (campos, archivos = {}, { confirmar, quedarse } = {}) => {
    if (confirmar && !window.confirm(confirmar)) return false
    const datos = new FormData()
    Object.entries(campos).forEach(([k, v]) => {
      if (Array.isArray(v)) v.forEach(x => datos.append(k, x))
      else if (v !== undefined && v !== null && v !== false) datos.append(k, v === true ? '1' : v)
    })
    Object.entries(archivos).forEach(([k, lista]) => [...(lista || [])].forEach(f => datos.append(k, f)))
    setOcupado(true)
    try {
      const r = await api.post(url, datos)
      r.mensajes.forEach(m => avisar(m.texto, m.tipo))
      const ruta = rutaReact(r.destino)
      if (!quedarse && ruta && ruta.split('?')[0] !== window.location.pathname.replace(/^\/app/, '')) navigate(ruta)
      else if (alTerminar) alTerminar()
      return true
    } catch (err) {
      err.errores.forEach(e => avisar(e, 'error'))
      if (alTerminar) alTerminar()
      return false
    } finally {
      setOcupado(false)
    }
  }, [url, avisar, navigate, alTerminar])

  return [enviar, ocupado]
}
