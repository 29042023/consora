import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api'
import { useAccion } from '../acciones'
import Avatar from '../componentes/Avatar'
import { Cargando, ErrorCarga } from '../componentes/Cargando'
import { useAvisos, useTitulo } from '../componentes/Layout'
import Modal from '../componentes/Modal'
import { fecha } from '../formatos'

function Estrellas({ n }) {
  return <div className="review-stars">{[1, 2, 3, 4, 5].map(i => <i key={i} className={'bi bi-star-fill' + (i > n ? ' off' : '')} />)}</div>
}

/** Perfil del Marketplace (estilo Instagram): publicaciones de trabajos y reseñas. */
export default function PerfilProveedor() {
  const { id } = useParams()
  const navigate = useNavigate()
  const avisar = useAvisos()
  const [p, setP] = useState(null)
  const [error, setError] = useState('')
  const [pestana, setPestana] = useState('publicaciones')
  const [resena, setResena] = useState(null)
  const [publicar, setPublicar] = useState(null)
  const [ver, setVer] = useState(null)

  const cargar = useCallback(() => { api.get(`/marketplace/proveedor/${id}/`).then(setP).catch(e => setError(e.message)) }, [id])
  useEffect(cargar, [cargar])
  const [accion, ocupado] = useAccion(`/marketplace/proveedor/${id}/`, cargar)

  useTitulo(p?.nombre || 'Perfil', 'Perfil del Marketplace')

  const mensaje = async () => {
    try {
      const r = await api.post(`/chats/con/${id}/`, {})
      navigate(`/chat/${r.id}`)
    } catch (err) {
      avisar(err.message, 'error')
    }
  }

  if (error) return <ErrorCarga error={error} />
  if (!p) return <Cargando />

  return (
    <div className="ig-profile">
      <Link to="/marketplace" className="back-link mb-3 d-inline-block"><i className="bi bi-arrow-left" /> Marketplace</Link>
      <header className="ig-head">
        <div className="ig-avatar-wrap"><Avatar img={p.img} iniciales={p.iniciales} clase="ig-avatar" /></div>
        <div className="ig-info">
          <div className="ig-name-row">
            <h2 className="ig-name">{p.nombre}</h2>
            {p.promedio && <span className="ig-verified" title="Calificación promedio"><i className="bi bi-star-fill" /> {p.promedio}</span>}
          </div>
          <div className="ig-category">{p.rol}</div>
          <ul className="ig-stats">
            <li><strong>{p.publicaciones.length}</strong> publicacion{p.publicaciones.length === 1 ? '' : 'es'}</li>
            <li><a href="#resenas" onClick={e => { e.preventDefault(); setPestana('resenas') }}><strong>{p.resenas.length}</strong> reseña{p.resenas.length === 1 ? '' : 's'}</a></li>
            <li><strong>{p.trabajos}</strong> contrato{p.trabajos === 1 ? '' : 's'} en CONSORA</li>
          </ul>
          {p.descripcion && <p className="ig-bio">{p.descripcion}</p>}
          {p.link && <a href={p.link} target="_blank" rel="noreferrer" className="ig-link"><i className="bi bi-link-45deg" /> {p.link.replace(/^https?:\/\/(www\.)?/, '')}</a>}
          {!p.tiene_cuenta && <p className="ig-bio text-secondary">Este usuario todavía no tiene perfil en el Marketplace.</p>}
        </div>
      </header>

      {p.tiene_cuenta && (
        <>
          <div className="ig-actions">
            {p.es_propio ? (
              <>
                <button type="button" className="ig-btn" onClick={() => setPublicar({ foto: null, descripcion: '' })}><i className="bi bi-plus-square me-1" /> Nueva publicación</button>
                <button type="button" className="ig-btn" onClick={() => setPestana('resenas')}>Ver reseñas</button>
              </>
            ) : (
              <>
                <button type="button" className="ig-btn primary" onClick={mensaje}>Mensaje</button>
                <button type="button" className="ig-btn" onClick={() => setResena(p.mi_resena || { estrellas: 5, descripcion: '' })}>
                  {p.mi_resena ? 'Editar mi reseña' : 'Dejar reseña'}
                </button>
              </>
            )}
          </div>
          <nav className="ig-tabs">
            <button type="button" className={pestana === 'publicaciones' ? 'active' : ''} onClick={() => setPestana('publicaciones')}><i className="bi bi-grid-3x3" /><span>Publicaciones</span></button>
            <button type="button" className={pestana === 'resenas' ? 'active' : ''} onClick={() => setPestana('resenas')}><i className="bi bi-star" /><span>Reseñas</span></button>
          </nav>

          {pestana === 'publicaciones' ? (
            <section className="ig-panel">
              <div className="ig-grid">
                {p.es_propio && (
                  <button type="button" className="ig-cell add" onClick={() => setPublicar({ foto: null, descripcion: '' })}>
                    <i className="bi bi-plus-lg" /><span>Nueva publicación</span>
                  </button>
                )}
                {p.publicaciones.map(x => (
                  <div className="ig-cell" key={x.id} onClick={() => setVer(x)}>
                    <img src={x.img} alt="" />
                    <div className="ig-cell-hover">{x.descripcion && <span>{x.descripcion.slice(0, 70)}</span>}</div>
                  </div>
                ))}
                {!p.es_propio && !p.publicaciones.length && (
                  <div className="ig-empty"><div className="ig-empty-icon"><i className="bi bi-camera" /></div><h5>Todavía no hay publicaciones</h5></div>
                )}
              </div>
            </section>
          ) : (
            <section className="ig-panel">
              {p.resenas.map(r => (
                <div className="review-item" key={r.id}>
                  <div className="review-avatar">{r.autor.iniciales}</div>
                  <div className="flex-grow-1">
                    <div className="d-flex justify-content-between"><strong className="small">{r.autor.nombre}</strong><small className="text-secondary">{fecha(r.fecha)}</small></div>
                    <Estrellas n={r.estrellas} />
                    {r.descripcion && <p className="small mb-0">{r.descripcion}</p>}
                  </div>
                </div>
              ))}
              {!p.resenas.length && <div className="ig-empty"><div className="ig-empty-icon"><i className="bi bi-star" /></div><h5>Todavía no tiene reseñas</h5></div>}
            </section>
          )}
        </>
      )}

      {/* Reseña */}
      <Modal titulo={p.mi_resena ? 'Editar mi reseña' : 'Dejar reseña'} abierto={Boolean(resena)} onCerrar={() => setResena(null)} pie={
        <>
          <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setResena(null)}>Cancelar</button>
          <button type="button" className="btn btn-primary btn-sm px-3" disabled={ocupado}
            onClick={async () => { if (await accion({ accion: 'resena', ...resena }, {}, { quedarse: true })) setResena(null) }}>Guardar</button>
        </>
      }>
        {resena && (
          <>
            <div className="d-flex gap-1 fs-3 mb-3">
              {[1, 2, 3, 4, 5].map(i => (
                <i key={i} role="button" className={'bi ' + (i <= resena.estrellas ? 'bi-star-fill text-warning' : 'bi-star text-secondary')}
                  onClick={() => setResena(r => ({ ...r, estrellas: i }))} />
              ))}
            </div>
            <textarea className="form-control" rows="3" placeholder="Contá cómo fue el trabajo (opcional)" value={resena.descripcion || ''}
              onChange={e => setResena(r => ({ ...r, descripcion: e.target.value }))} />
          </>
        )}
      </Modal>

      {/* Nueva publicación (solo el dueño) */}
      <Modal titulo="Nueva publicación" abierto={Boolean(publicar)} onCerrar={() => setPublicar(null)} pie={
        <>
          <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setPublicar(null)}>Cancelar</button>
          <button type="button" className="btn btn-primary btn-sm px-3" disabled={!publicar?.foto || ocupado}
            onClick={async () => { if (await accion({ accion: 'publicar', descripcion: publicar.descripcion }, { foto: [publicar.foto] }, { quedarse: true })) setPublicar(null) }}>Publicar</button>
        </>
      }>
        {publicar && (
          <>
            <label className="form-label small fw-semibold">Foto del trabajo</label>
            <input type="file" className="form-control mb-3" accept="image/*" onChange={e => setPublicar(x => ({ ...x, foto: e.target.files[0] || null }))} />
            <label className="form-label small fw-semibold">Descripción (opcional)</label>
            <textarea className="form-control" rows="2" value={publicar.descripcion} onChange={e => setPublicar(x => ({ ...x, descripcion: e.target.value }))} />
          </>
        )}
      </Modal>

      {/* Ver una publicación */}
      <Modal titulo={p.nombre} abierto={Boolean(ver)} onCerrar={() => setVer(null)} pie={p.es_propio && ver && (
        <button type="button" className="btn btn-link text-danger btn-sm text-decoration-none p-0" disabled={ocupado}
          onClick={async () => { if (await accion({ accion: 'borrar_publicacion', id_publicacion: ver.id }, {}, { confirmar: '¿Eliminar esta publicación?', quedarse: true })) setVer(null) }}>
          <i className="bi bi-trash me-1" /> Eliminar publicación
        </button>
      )}>
        {ver && (
          <>
            <img src={ver.img} alt="" className="w-100 rounded-3 mb-2" />
            {ver.descripcion && <p className="small mb-0">{ver.descripcion}</p>}
          </>
        )}
      </Modal>
    </div>
  )
}
