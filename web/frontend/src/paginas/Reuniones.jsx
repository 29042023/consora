import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, DJANGO } from '../api'
import { useAccion } from '../acciones'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import ElegirProveedores from '../componentes/ElegirProveedores'
import { useTitulo } from '../componentes/Layout'
import Modal from '../componentes/Modal'
import TabsConsorcio from '../componentes/TabsConsorcio'
import { fechaHora, hora } from '../formatos'

export const LOGO_MEET = '/static/img/google_meet.svg'

/** Etiqueta de modalidad: logo de Google Meet si es virtual. */
export function Modalidad({ r }) {
  return (
    <span className={'meet-type ' + r.modalidad}>
      {r.es_virtual ? <img src={LOGO_MEET} alt="Google Meet" className="meet-logo" /> : <i className="bi bi-geo-alt" />} {r.modalidad_texto}
    </span>
  )
}

/** Formulario de nueva reunión: virtual (Meet) o presencial, con proveedor invitado opcional. */
function NuevaReunion({ datos, consorcioInicial, abierto, onCerrar }) {
  const [enviar, ocupado] = useAccion('/reuniones/')
  const [form, setForm] = useState({})
  const [invitar, setInvitar] = useState(false)
  const [proveedores, setProveedores] = useState([])
  const [docs, setDocs] = useState([])
  const [linkPropio, setLinkPropio] = useState(false)

  useEffect(() => {
    if (!abierto) return
    setForm({ id_consorcio: consorcioInicial || datos.mis_consorcios[0]?.id || '', modalidad: 'virtual', titulo: '', descripcion: '',
      fecha_inicio: datos.inicio_sugerido, duracion: 60, meet_link: '', lugar: '' })
    setInvitar(false); setProveedores([]); setDocs([]); setLinkPropio(!datos.google_vinculada)
  }, [abierto]) // eslint-disable-line

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const virtual = form.modalidad === 'virtual'

  const crear = e => {
    e.preventDefault()
    enviar({
      ...form,
      meet_link: virtual && linkPropio ? form.meet_link : '',
      lugar: virtual ? '' : form.lugar,
      invitar_proveedor: invitar,
      proveedores: invitar ? proveedores : [],
    }, { docs })
  }

  return (
    <Modal titulo="Nueva reunión" abierto={abierto} onCerrar={onCerrar} onSubmit={crear} pie={
      <>
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={onCerrar}>Cancelar</button>
        <button type="submit" className="btn btn-primary btn-sm px-3" disabled={ocupado}>{ocupado ? 'Creando…' : 'Crear reunión'}</button>
      </>
    }>
      <label className="form-label small fw-semibold">Consorcio</label>
      <select className="form-select mb-3" value={form.id_consorcio || ''} onChange={e => set('id_consorcio', e.target.value)}>
        {datos.mis_consorcios.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
      </select>

      <label className="form-label small fw-semibold">Modalidad</label>
      <div className="vote-type-picker mb-3">
        <label>
          <input type="radio" checked={virtual} onChange={() => set('modalidad', 'virtual')} />
          <span><img src={LOGO_MEET} alt="" className="meet-logo lg" /> <strong>Virtual</strong><small>Con link de Google Meet</small></span>
        </label>
        <label>
          <input type="radio" checked={!virtual} onChange={() => set('modalidad', 'presencial')} />
          <span><i className="bi bi-geo-alt" /> <strong>Presencial</strong><small>En el edificio u otro lugar</small></span>
        </label>
      </div>

      <label className="form-label small fw-semibold">Título</label>
      <input className="form-control mb-3" maxLength={150} placeholder="Ej.: Asamblea ordinaria" value={form.titulo || ''} onChange={e => set('titulo', e.target.value)} />

      <label className="form-label small fw-semibold">Descripción u orden del día (opcional)</label>
      <textarea className="form-control mb-3" rows="2" placeholder="Temas a tratar" value={form.descripcion || ''} onChange={e => set('descripcion', e.target.value)} />

      <div className="row g-2 mb-3">
        <div className="col-7">
          <label className="form-label small fw-semibold">Fecha y hora</label>
          <input type="datetime-local" className="form-control" min={datos.ahora_min} value={form.fecha_inicio || ''} onChange={e => set('fecha_inicio', e.target.value)} />
        </div>
        <div className="col-5">
          <label className="form-label small fw-semibold">Duración</label>
          <select className="form-select" value={form.duracion || 60} onChange={e => set('duracion', e.target.value)}>
            {datos.duraciones.map(d => <option key={d.minutos} value={d.minutos}>{d.texto}</option>)}
          </select>
        </div>
      </div>

      {virtual ? (
        <div className="mb-3">
          {datos.google_vinculada && (
            <div className="meet-note ok mb-2">
              <img src={LOGO_MEET} alt="" className="meet-logo" /> Se va a crear en tu Google Calendar ({datos.google_vinculada}) con el link de Meet, y les llega la invitación a los participantes.
            </div>
          )}
          {datos.google_vinculada && (
            <button type="button" className="btn btn-link btn-sm p-0 mb-2" onClick={() => setLinkPropio(x => !x)}>
              {linkPropio ? 'Generarlo con Google' : 'Prefiero pegar un link propio'}
            </button>
          )}
          {linkPropio && (
            <>
              <input type="url" className="form-control" placeholder="https://meet.google.com/abc-defg-hij" value={form.meet_link || ''} onChange={e => set('meet_link', e.target.value)} />
              {!datos.google_vinculada && <div className="form-text">Opcional. Para generarlo solo, vinculá tu cuenta de Google desde <Link to="/cuenta">Mi cuenta</Link>.</div>}
            </>
          )}
        </div>
      ) : (
        <>
          <label className="form-label small fw-semibold">Lugar</label>
          <input className="form-control mb-3" placeholder="Ej.: SUM del edificio, planta baja" value={form.lugar || ''} onChange={e => set('lugar', e.target.value)} />
        </>
      )}

      <div className="form-check form-switch mb-2">
        <input className="form-check-input" type="checkbox" id="swProveedor" checked={invitar} onChange={e => setInvitar(e.target.checked)} />
        <label className="form-check-label fw-semibold small" htmlFor="swProveedor">Invitar proveedor</label>
      </div>
      {invitar && (
        <div className="mb-3">
          <p className="small text-secondary mb-2">Le llega la invitación por chat y participa solo si la acepta; recién ahí ve el link.</p>
          <ElegirProveedores proveedores={datos.proveedores} elegidos={proveedores} onCambiar={setProveedores} />
        </div>
      )}

      <label className="form-label small fw-semibold">Documentos (opcional)</label>
      <input type="file" className="form-control" multiple accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,image/*" onChange={e => setDocs([...e.target.files])} />
      <div className="form-text">Orden del día u otros archivos: PDF, Word, Excel o fotos de hasta 15 MB.</div>
    </Modal>
  )
}

export default function Reuniones() {
  const [params, setParams] = useSearchParams()
  const consorcio = params.get('consorcio') || ''
  const estado = params.get('estado') || ''
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  const [modal, setModal] = useState(false)

  const cargar = useCallback(() => {
    const q = new URLSearchParams()
    if (consorcio) q.set('consorcio', consorcio)
    if (estado) q.set('estado', estado)
    api.get('/reuniones/?' + q).then(setDatos).catch(e => setError(e.message))
  }, [consorcio, estado])
  useEffect(cargar, [cargar])

  useTitulo('Reuniones', 'Reuniones de los consorcios, virtuales con Google Meet o presenciales')

  const filtro = (k, v) => {
    const q = new URLSearchParams(params)
    if (v) q.set(k, v); else q.delete(k)
    setParams(q)
  }

  if (error) return <ErrorCarga error={error} />
  if (!datos) return <Cargando />
  const filtrado = datos.mis_consorcios.find(c => String(c.id) === consorcio)

  return (
    <>
      {filtrado && <TabsConsorcio id={filtrado.id} activa="reuniones" />}
      <div className="page-header-row d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2>{filtrado ? `Reuniones de ${filtrado.nombre}` : 'Reuniones'}</h2>
          <p>Reuniones virtuales con Google Meet o presenciales. Podés invitar a un proveedor: participa si acepta.</p>
        </div>
        {datos.puede_gestionar && (
          <button type="button" className="btn btn-primary btn-sm px-3" disabled={!datos.mis_consorcios.length} onClick={() => setModal(true)}>
            <img src={LOGO_MEET} alt="" className="meet-logo on-btn me-1" /> Crear Meet
          </button>
        )}
      </div>

      <div className="vote-filters">
        {datos.mis_consorcios.length > 0 && (
          <select className="form-select form-select-sm" value={consorcio} onChange={e => filtro('consorcio', e.target.value)}>
            <option value="">Todos los consorcios</option>
            {datos.mis_consorcios.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        )}
        <div className="vote-chips">
          <button type="button" className={'vote-chip' + (estado !== 'pasadas' ? ' active' : '')} onClick={() => filtro('estado', '')}>Próximas</button>
          <button type="button" className={'vote-chip' + (estado === 'pasadas' ? ' active' : '')} onClick={() => filtro('estado', 'pasadas')}>Pasadas y canceladas</button>
        </div>
      </div>

      <div className="row g-3">
        {datos.reuniones.map(r => (
          <div className="col-md-6 col-xl-4" key={r.id}>
            <Link to={`/reuniones/${r.id}`} className="vote-card">
              <div className="d-flex align-items-center justify-content-between gap-2 mb-2">
                <Modalidad r={r} />
                <span className={'meet-state ' + r.estado}>{r.estado === 'en_curso' && <i className="bi bi-circle-fill" />} {r.estado_texto}</span>
              </div>
              <p className="vote-detail fw-semibold mb-1">{r.titulo}</p>
              {r.descripcion && <p className="vote-detail small text-secondary">{r.descripcion.length > 90 ? r.descripcion.slice(0, 90) + '…' : r.descripcion}</p>}
              <div className="vote-meta">
                <span><i className="bi bi-building" /> {r.consorcio.nombre}</span>
                <span><i className="bi bi-calendar-event" /> {fechaHora(r.fecha_inicio)} a {hora(r.fecha_fin)}</span>
              </div>
              <div className="vote-foot">
                <span><i className="bi bi-people" /> {r.cant_confirmados} confirmado{r.cant_confirmados === 1 ? '' : 's'}</span>
                {r.es_virtual && r.tiene_link && <span><i className="bi bi-link-45deg" /> Meet listo</span>}
                {r.mi_respuesta === 'pendiente' && <span className="ms-auto text-warning-emphasis fw-semibold"><i className="bi bi-hourglass-split" /> Confirmá</span>}
                {r.mi_respuesta === 'acepta' && <span className="ms-auto text-success fw-semibold"><i className="bi bi-check-circle-fill" /> Vas</span>}
                {r.mi_respuesta === 'rechaza' && <span className="ms-auto text-secondary fw-semibold">No vas</span>}
              </div>
            </Link>
          </div>
        ))}
        {!datos.reuniones.length && (
          <div className="col-12">
            <Vacio icono="bi-camera-video" titulo={estado === 'pasadas' ? 'No hay reuniones pasadas' : 'No hay reuniones próximas'}
              texto={datos.puede_gestionar ? 'Creá una con "Crear Meet".' : 'Cuando se programe una reunión, la vas a ver acá.'} />
          </div>
        )}
      </div>

      {datos.puede_gestionar && <NuevaReunion datos={datos} consorcioInicial={consorcio} abierto={modal} onCerrar={() => setModal(false)} />}
    </>
  )
}
