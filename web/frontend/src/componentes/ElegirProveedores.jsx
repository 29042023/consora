import { useMemo, useState } from 'react'
import { normalizar } from '../formatos'
import Avatar from './Avatar'

/** Lista de proveedores con buscador, foto e info; se marcan uno o varios (para invitar). */
export default function ElegirProveedores({ proveedores, elegidos, onCambiar, multiple = true }) {
  const [q, setQ] = useState('')
  const visibles = useMemo(() => {
    const t = normalizar(q.trim())
    return proveedores.filter(p => !t || normalizar(p.nombre + ' ' + (p.info || '')).includes(t))
  }, [proveedores, q])

  const alternar = id => {
    if (!multiple) { onCambiar([id]); return }
    onCambiar(elegidos.includes(id) ? elegidos.filter(x => x !== id) : [...elegidos, id])
  }

  return (
    <div className="sr-panel mt-0">
      <input type="search" className="form-control form-control-sm sr-buscar" placeholder="Buscar proveedor o rubro"
        value={q} onChange={e => setQ(e.target.value)} />
      <div className="sr-lista">
        {visibles.map(p => (
          <button type="button" key={p.id} className={'sr-item' + (elegidos.includes(p.id) ? ' elegido' : '')} onClick={() => alternar(p.id)}>
            <span className="sr-fila">
              <input type={multiple ? 'checkbox' : 'radio'} className="form-check-input m-0 flex-shrink-0" readOnly checked={elegidos.includes(p.id)} />
              <Avatar img={p.img} iniciales={p.iniciales} />
              <span className="sr-textos">
                <span className="sr-titulo">{p.nombre}</span>
                {p.info && <span className="sr-sub">{p.info}</span>}
              </span>
              {p.estrellas && <span className="sr-extra">{p.estrellas}</span>}
            </span>
          </button>
        ))}
        {!visibles.length && <div className="sr-vacio">No hay resultados.</div>}
      </div>
      {multiple && <div className="small text-secondary mt-1">{elegidos.length} elegido{elegidos.length === 1 ? '' : 's'}</div>}
    </div>
  )
}
