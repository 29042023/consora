import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useTitulo } from '../componentes/Layout'
import { plural } from '../formatos'
import { useSesion } from '../sesion'

export default function Consorcios() {
  const { usuario } = useSesion()
  const navigate = useNavigate()
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  const [q, setQ] = useState('')

  useTitulo(usuario.es_propietario ? 'Mi consorcio' : 'Consorcios', 'Tus edificios, sus facturas y deudas pendientes')

  useEffect(() => {
    // Busca mientras se escribe (con una pequeña espera)
    const t = setTimeout(() => {
      api.get('/consorcios/' + (q.trim() ? '?q=' + encodeURIComponent(q.trim()) : ''))
        .then(setDatos).catch(e => setError(e.message))
    }, 250)
    return () => clearTimeout(t)
  }, [q])

  if (error) return <ErrorCarga error={error} />

  return (
    <>
      <div className="mb-4" style={{ maxWidth: 420 }}>
        <div className="input-group">
          <span className="input-group-text bg-white"><i className="bi bi-search" /></span>
          <input type="search" className="form-control" placeholder="Buscar por nombre o dirección" value={q} onChange={e => setQ(e.target.value)} />
        </div>
      </div>

      {!datos ? <Cargando /> : (
        <div className="row g-4">
          {datos.consorcios.map(c => (
            <div className="col-md-6 col-xl-4" key={c.id}>
              <div className="building-card h-100">
                <div className="building-card-img app-tarjeta-link" onClick={() => navigate(`/consorcios/${c.id}/facturacion`)}>
                  <i className="bi bi-buildings" />
                  {c.img && <img src={c.img} alt={c.nombre} />}
                </div>
                <div className="building-card-body">
                  <h5>{c.nombre}</h5>
                  <p className="building-card-address"><i className="bi bi-geo-alt" /> {c.direccion}</p>
                  {c.descripcion && <p className="building-card-desc">{c.descripcion}</p>}
                  <div className="building-card-stats">
                    <span><i className="bi bi-receipt" /> {plural(c.cant_facturas, 'factura')}</span>
                    {c.cant_pendientes > 0 && <span className="text-warning-emphasis"><i className="bi bi-hourglass-split" /> {plural(c.cant_pendientes, 'pendiente')}</span>}
                  </div>
                  <div className="d-flex flex-wrap gap-2 mt-3">
                    <Link to={`/consorcios/${c.id}/facturacion`} className="btn btn-outline-primary btn-sm flex-fill"><i className="bi bi-receipt me-1" /> Facturación</Link>
                    <Link to={`/contratos?consorcio=${c.id}`} className="btn btn-outline-primary btn-sm flex-fill"><i className="bi bi-file-earmark-text me-1" /> Contratos</Link>
                    <Link to={`/votaciones?consorcio=${c.id}`} className="btn btn-outline-primary btn-sm flex-fill"><i className="bi bi-check2-square me-1" /> Votaciones</Link>
                    <Link to={`/reuniones?consorcio=${c.id}`} className="btn btn-outline-primary btn-sm flex-fill"><i className="bi bi-camera-video me-1" /> Reuniones</Link>
                    <Link to={`/historial?consorcio=${c.id}`} className="btn btn-outline-primary btn-sm flex-fill"><i className="bi bi-clock-history me-1" /> Historial</Link>
                  </div>
                </div>
              </div>
            </div>
          ))}
          {datos.consorcios.length === 0 && (
            <div className="col-12">
              <Vacio icono="bi-buildings" titulo={q ? 'No hay resultados' : 'Todavía no tenés consorcios'}
                texto={q ? 'Probá con otro nombre o dirección.' : 'Los consorcios que administres van a aparecer acá.'} />
            </div>
          )}
        </div>
      )}
    </>
  )
}
