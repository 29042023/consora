import { useEffect, useState } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { api } from '../api'
import { Cargando, ErrorCarga } from '../componentes/Cargando'
import { useTitulo } from '../componentes/Layout'
import { useSesion } from '../sesion'

/** Pestañas del Marketplace: proveedores y (para administraciones) edificios que buscan administrador. */
function Pestanas() {
  const { usuario } = useSesion()
  if (!usuario.es_admin) return null
  return (
    <div className="consorcio-tabs">
      <NavLink to="/marketplace" end><i className="bi bi-shop" /> Proveedores</NavLink>
      <NavLink to="/marketplace/consorcios"><i className="bi bi-buildings" /> Consorcios que buscan administrador</NavLink>
    </div>
  )
}

function Buscador({ valor, onCambiar, placeholder }) {
  return (
    <div className="market-search-row">
      <div className="market-search">
        <i className="bi bi-search" />
        <input type="text" placeholder={placeholder} value={valor} onChange={e => onCambiar(e.target.value)} />
      </div>
    </div>
  )
}

export function Marketplace() {
  const [q, setQ] = useState('')
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  useTitulo('Marketplace', 'Encontrá proveedores para tus consorcios')

  useEffect(() => {
    const t = setTimeout(() => {
      api.get('/marketplace/' + (q.trim() ? '?q=' + encodeURIComponent(q.trim()) : '')).then(setDatos).catch(e => setError(e.message))
    }, 250)
    return () => clearTimeout(t)
  }, [q])

  if (error) return <ErrorCarga error={error} />
  return (
    <>
      <Pestanas />
      <Buscador valor={q} onCambiar={setQ} placeholder="Buscar proveedores por nombre o servicio" />
      {!datos ? <Cargando /> : (
        <div className="market-grid">
          {datos.proveedores.map(p => (
            <Link key={p.id} to={`/marketplace/proveedor/${p.id}`} className={'market-tile tema-' + p.tema}>
              {p.foto ? <img src={p.foto} alt="" className="market-tile-img" /> : <span className="market-tile-bg-icon market-tile-initials">{p.iniciales}</span>}
              <span className="market-tile-type">{p.cant_publicaciones > 1 && <i className="bi bi-images" />}</span>
              {p.cant_publicaciones > 0 && <span className="market-tile-rubro">{p.cant_publicaciones} publicaciones</span>}
              <div className="market-tile-info">
                <p className="market-tile-text">{p.descripcion}</p>
                <div className="market-tile-provider">
                  {p.img ? <img src={p.img} alt="" className="market-tile-avatar" /> : <span className="market-tile-avatar">{p.nombre.slice(0, 1)}</span>}
                  <span>{p.nombre}</span>
                </div>
              </div>
              <div className="market-tile-hover">
                <span><i className="bi bi-star-fill" /> {p.promedio || 'Sin reseñas'}</span>
                <span><i className="bi bi-chat-fill" /> {p.cant_resenas}</span>
              </div>
            </Link>
          ))}
          {!datos.proveedores.length && <div className="market-empty">No hay proveedores con esa búsqueda.</div>}
        </div>
      )}
    </>
  )
}

export function MarketplaceConsorcios() {
  const [q, setQ] = useState('')
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  useTitulo('Marketplace de consorcios', 'Edificios que están buscando administrador')
  useEffect(() => { api.get('/marketplace/consorcios/').then(setDatos).catch(e => setError(e.message)) }, [])

  if (error) return <ErrorCarga error={error} />
  if (!datos) return <Cargando />
  const t = q.trim().toLowerCase()
  const visibles = datos.edificios.filter(e => !t || (e.nombre + ' ' + e.zona).toLowerCase().includes(t))
  return (
    <>
      <Pestanas />
      <Buscador valor={q} onCambiar={setQ} placeholder="Buscar edificios por nombre, barrio o ciudad" />
      <div className="market-grid">
        {visibles.map(e => (
          <div key={e.nombre} className={'market-tile tema-' + e.tema}>
            <i className={'bi ' + e.icono + ' market-tile-bg-icon'} />
            <span className="market-tile-rubro">{e.zona}</span>
            <div className="market-tile-info">
              <p className="market-tile-text">{e.texto}</p>
              <div className="market-tile-provider"><span className="market-tile-avatar">{e.nombre.slice(0, 1)}</span><span>{e.nombre}</span></div>
            </div>
            <div className="market-tile-hover">
              <span><i className="bi bi-door-closed-fill" /> {e.unidades} u.</span>
              <span><i className="bi bi-person-fill" /> {e.postulaciones}</span>
            </div>
          </div>
        ))}
      </div>
    </>
  )
}
