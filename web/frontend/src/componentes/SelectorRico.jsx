import { useEffect, useMemo, useRef, useState } from 'react'
import { normalizar } from '../formatos'
import Avatar from './Avatar'

function Fila({ opcion, redondo }) {
  return (
    <span className="sr-fila">
      <Avatar img={opcion.img} iniciales={opcion.iniciales} clase={'sr-avatar' + (redondo ? ' redondo' : '')} />
      <span className="sr-textos">
        <span className="sr-titulo">{opcion.titulo}</span>
        {opcion.sub && <span className="sr-sub">{opcion.sub}</span>}
      </span>
      {opcion.extra && <span className="sr-extra">{opcion.extra}</span>}
    </span>
  )
}

/**
 * Selector con buscador, foto e información (consorcios y proveedores).
 * opciones: [{ id, titulo, sub, img, iniciales, extra }]
 */
export default function SelectorRico({ opciones, valor, onCambiar, placeholder, buscar, redondo, invalido }) {
  const [abierto, setAbierto] = useState(false)
  const [q, setQ] = useState('')
  const [activo, setActivo] = useState(0)
  const caja = useRef(null)
  const elegida = opciones.find(o => String(o.id) === String(valor))

  const visibles = useMemo(() => {
    const t = normalizar(q.trim())
    return opciones.filter(o => !t || normalizar(o.titulo + ' ' + (o.sub || '')).includes(t))
  }, [opciones, q])

  useEffect(() => {
    if (!abierto) return undefined
    const fuera = e => caja.current && !caja.current.contains(e.target) && setAbierto(false)
    document.addEventListener('mousedown', fuera)
    return () => document.removeEventListener('mousedown', fuera)
  }, [abierto])

  const elegir = o => { onCambiar(o.id); setAbierto(false); setQ('') }

  const teclas = e => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActivo(a => Math.min(a + 1, visibles.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActivo(a => Math.max(a - 1, 0)) }
    else if (e.key === 'Enter') { e.preventDefault(); if (visibles[activo]) elegir(visibles[activo]) }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); setAbierto(false) }
  }

  return (
    <div className={'selector-rico-caja' + (abierto ? ' abierto' : '')} ref={caja}>
      <button type="button" className={'sr-boton form-select' + (invalido ? ' is-invalid' : '')} onClick={() => setAbierto(a => !a)}>
        {elegida ? <Fila opcion={elegida} redondo={redondo} /> : <span className="sr-placeholder">{placeholder}</span>}
      </button>
      {abierto && (
        <div className="sr-panel">
          <input
            type="search" className="form-control form-control-sm sr-buscar" placeholder={buscar} autoFocus
            value={q} onChange={e => { setQ(e.target.value); setActivo(0) }} onKeyDown={teclas}
          />
          <div className="sr-lista">
            {visibles.map((o, i) => (
              <button type="button" key={o.id} onClick={() => elegir(o)}
                className={'sr-item' + (i === activo ? ' activo' : '') + (String(o.id) === String(valor) ? ' elegido' : '')}>
                <Fila opcion={o} redondo={redondo} />
              </button>
            ))}
          </div>
          {visibles.length === 0 && <div className="sr-vacio">No hay resultados.</div>}
        </div>
      )}
    </div>
  )
}
