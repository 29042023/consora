import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, DJANGO } from '../api'
import { useAccion } from '../acciones'
import Avatar from '../componentes/Avatar'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import ElegirProveedores from '../componentes/ElegirProveedores'
import { useTitulo } from '../componentes/Layout'
import Modal from '../componentes/Modal'
import { diaLargo, hora, iconoArchivo } from '../formatos'
import { LOGO_MEET, Modalidad } from './Reuniones'

function Persona({ p, children }) {
  return (
    <div className="data-row meet-person">
      <span>{children}</span>
      <span className="meet-role">{p.rol_texto}{p.es_opcional && ' · opcional'}</span>
    </div>
  )
}

export default function ReunionDetalle() {
  const { id } = useParams()
  const [r, setR] = useState(null)
  const [error, setError] = useState('')
  const [link, setLink] = useState({ meet_link: '', meet_codigo: '' })
  const [docs, setDocs] = useState([])
  const [tipoDoc, setTipoDoc] = useState('orden_del_dia')
  const [presentes, setPresentes] = useState([])
  const [invitar, setInvitar] = useState(false)
  const [elegido, setElegido] = useState([])

  const cargar = useCallback(() => {
    api.get(`/reuniones/${id}/`).then(d => {
      setR(d)
      setLink({ meet_link: d.meet_link || '', meet_codigo: d.meet_codigo || '' })
      setPresentes(d.participantes.filter(p => p.asistio).map(p => p.id))
      setTipoDoc(d.activa ? 'orden_del_dia' : 'acta')
    }).catch(e => setError(e.message))
  }, [id])
  useEffect(cargar, [cargar])
  const [accion, ocupado] = useAccion(`/reuniones/${id}/`, cargar)

  useTitulo(r ? `Reunión · ${r.consorcio.nombre}` : 'Reunión', r?.modalidad_texto || '')

  if (error) return <ErrorCarga error={error} />
  if (!r) return <Cargando />

  const confirmados = r.participantes.filter(p => p.respuesta === 'acepta')
  const pendientes = r.participantes.filter(p => p.respuesta === 'pendiente')
  const rechazaron = r.participantes.filter(p => p.respuesta === 'rechaza')
  const proveedores = r.participantes.filter(p => p.rol === 'proveedor')
  const empezo = r.estado !== 'programada' && r.estado !== 'cancelada'

  return (
    <>
      <Link to={`/reuniones${r.gestiona ? '?consorcio=' + r.consorcio.id : ''}`} className="back-link mb-3 d-inline-block"><i className="bi bi-arrow-left" /> Reuniones</Link>

      <div className="row g-4">
        <div className="col-lg-7">
          {/* Cabecera */}
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="d-flex flex-wrap align-items-center gap-2 mb-2">
              <Modalidad r={r} />
              <span className={'meet-state ' + r.estado}>{r.estado === 'en_curso' && <i className="bi bi-circle-fill" />} {r.estado_texto}</span>
            </div>
            <h4 className="fw-bold mb-1">{r.titulo}</h4>
            {r.descripcion && <p className="vote-detail-full">{r.descripcion}</p>}
            <div className="meet-when">
              <span><i className="bi bi-calendar-event" /> {diaLargo(r.fecha_inicio)}</span>
              <span><i className="bi bi-clock" /> {hora(r.fecha_inicio)} a {hora(r.fecha_fin)} h</span>
              <span><i className="bi bi-building" /> {r.consorcio.nombre}</span>
              {!r.es_virtual && <span><i className="bi bi-geo-alt" /> {r.lugar}</span>}
            </div>
            <small className="text-secondary d-block mt-2">Organiza {r.organizador}</small>

            {r.es_virtual && r.activa && (r.meet_link ? (
              <>
                <div className="meet-link-box mt-3">
                  <div className="min-w-0">
                    <div className="small text-secondary">Link de la reunión</div>
                    <a href={r.meet_link} target="_blank" rel="noreferrer" className="fw-semibold text-break">{r.meet_link}</a>
                    {r.meet_codigo && <div className="small">Código: <strong>{r.meet_codigo}</strong></div>}
                  </div>
                  <a href={r.meet_link} target="_blank" rel="noreferrer" className="btn btn-success btn-sm text-nowrap">
                    <img src={LOGO_MEET} alt="Google Meet" className="meet-logo on-btn me-1" /> Unirme
                  </a>
                </div>
                {r.con_google && <small className="text-secondary"><i className="bi bi-google" /> Agendada en Google Calendar</small>}
              </>
            ) : r.hay_link
              ? <div className="vote-waiting mt-3"><i className="bi bi-lock" /><div>Vas a ver el link de la reunión cuando aceptes la invitación.</div></div>
              : <div className="vote-waiting mt-3"><i className="bi bi-link-45deg" /><div>Todavía no se cargó el link de la reunión.</div></div>)}
          </div>

          {/* Mi respuesta */}
          {r.yo && r.activa && !r.gestiona && (
            <div className="panel-card mb-4" style={{ height: 'auto' }}>
              <div className="panel-card-header"><h5 className="mb-0">¿Vas a participar?</h5></div>
              {r.yo.respuesta === 'acepta' && <p className="small text-success fw-semibold mb-2"><i className="bi bi-check-circle-fill" /> Confirmaste que vas.</p>}
              {r.yo.respuesta === 'rechaza' && <p className="small text-secondary fw-semibold mb-2">Avisaste que no vas.</p>}
              {r.yo.respuesta === 'pendiente' && <p className="small text-secondary mb-2">{r.yo.es_opcional && 'Te invitaron como proveedor. '}Confirmá tu asistencia.</p>}
              <div className="d-flex gap-2">
                {r.yo.respuesta !== 'acepta' && <button type="button" className="btn btn-success btn-sm px-3" disabled={ocupado} onClick={() => accion({ accion: 'responder', respuesta: 'acepta' })}><i className="bi bi-check-lg me-1" /> Voy</button>}
                {r.yo.respuesta !== 'rechaza' && <button type="button" className="btn btn-outline-danger btn-sm px-3" disabled={ocupado} onClick={() => accion({ accion: 'responder', respuesta: 'rechaza' })}><i className="bi bi-x-lg me-1" /> No voy</button>}
              </div>
            </div>
          )}

          {/* Gestión del link (administrador) */}
          {r.gestiona && r.es_virtual && r.activa && (
            <div className="panel-card mb-4" style={{ height: 'auto' }}>
              <div className="panel-card-header d-flex align-items-center justify-content-between">
                <h5 className="mb-0"><img src={LOGO_MEET} alt="" className="meet-logo me-1" /> Google Meet</h5>
                {!r.con_google && r.google_disponible && r.google_vinculada && (
                  <button type="button" className="btn btn-outline-primary btn-sm" disabled={ocupado} onClick={() => accion({ accion: 'crear_meet' })}>
                    <i className="bi bi-google me-1" /> Generar link con Google
                  </button>
                )}
              </div>
              {r.con_google
                ? <p className="small text-secondary mb-0">El link lo generó Google Calendar. Si cancelás la reunión, se borra también de tu calendario.</p>
                : (
                  <>
                    <div className="row g-2">
                      <div className="col-sm-7"><input type="url" className="form-control form-control-sm" placeholder="https://meet.google.com/abc-defg-hij" value={link.meet_link} onChange={e => setLink(l => ({ ...l, meet_link: e.target.value }))} /></div>
                      <div className="col-sm-3"><input className="form-control form-control-sm" placeholder="Código" value={link.meet_codigo} onChange={e => setLink(l => ({ ...l, meet_codigo: e.target.value }))} /></div>
                      <div className="col-sm-2 d-grid"><button type="button" className="btn btn-outline-primary btn-sm" disabled={ocupado} onClick={() => accion({ accion: 'guardar_link', ...link })}>Guardar</button></div>
                    </div>
                    {!r.google_vinculada && <div className="form-text">Para generarlo automáticamente, vinculá tu cuenta de Google desde <Link to="/cuenta">Mi cuenta</Link>.</div>}
                  </>
                )}
            </div>
          )}

          {/* Proveedores invitados */}
          {r.gestiona && (
            <div className="panel-card mb-4" style={{ height: 'auto' }}>
              <div className="panel-card-header d-flex align-items-center justify-content-between">
                <h5 className="mb-0">Proveedores invitados</h5>
                {r.activa && r.proveedores.length > 0 && (
                  <button type="button" className="btn btn-outline-primary btn-sm" onClick={() => { setElegido([]); setInvitar(true) }}><i className="bi bi-send me-1" /> Invitar proveedor</button>
                )}
              </div>
              {proveedores.map(p => (
                <div className={'proposal-row' + (p.respuesta === 'acepta' ? ' accepted' : '')} key={p.id}>
                  <div className="proposal-avatar"><Avatar img={p.usuario.img} iniciales={p.usuario.iniciales} clase="" /></div>
                  <div className="flex-grow-1 min-w-0">
                    <div className="fw-bold"><Link to={`/marketplace/proveedor/${p.usuario.id}`} className="text-reset text-decoration-none">{p.usuario.nombre}</Link></div>
                    <div className="small">
                      {p.respuesta === 'acepta' && <span className="text-success fw-semibold"><i className="bi bi-check-circle-fill" /> Aceptó · se sumó a la reunión</span>}
                      {p.respuesta === 'rechaza' && <span className="text-danger fw-semibold"><i className="bi bi-x-circle-fill" /> Rechazó</span>}
                      {p.respuesta === 'pendiente' && <span className="text-warning-emphasis fw-semibold"><i className="bi bi-hourglass-split" /> Esperando respuesta</span>}
                      {p.chat_id && <> · <Link to={`/chat/${p.chat_id}`}><i className="bi bi-chat-dots" /> Chat</Link></>}
                    </div>
                  </div>
                  {r.activa && p.respuesta === 'pendiente' && (
                    <button type="button" className="btn-icon-soft" title="Cancelar invitación"
                      onClick={() => accion({ accion: 'cancelar_invitacion', id_participante: p.id }, {}, { confirmar: '¿Cancelar la invitación?' })}>
                      <i className="bi bi-x-lg" />
                    </button>
                  )}
                </div>
              ))}
              {!proveedores.length && <Vacio chico icono="bi-person-plus" texto="Podés invitar a un proveedor (por ejemplo, para que explique un presupuesto). Participa si acepta." />}
            </div>
          )}

          {/* Documentos */}
          <div className="panel-card" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0">Documentos</h5></div>
            {r.documentos.map(d => (
              <div className="invoice-row" key={d.id}>
                <div className="invoice-icon"><i className={'bi ' + iconoArchivo(d.extension)} /></div>
                <div className="flex-grow-1 min-w-0">
                  <a href={d.url} target="_blank" rel="noreferrer" className="invoice-title text-decoration-none">{d.nombre}</a>
                  <div className="invoice-meta">{d.tipo_texto}</div>
                </div>
                {r.gestiona && (
                  <button type="button" className="btn-icon-soft" title="Eliminar"
                    onClick={() => accion({ accion: 'borrar_doc', id_doc: d.id }, {}, { confirmar: '¿Eliminar este documento?' })}>
                    <i className="bi bi-trash" />
                  </button>
                )}
              </div>
            ))}
            {!r.documentos.length && <p className="text-secondary small">Sin documentos{r.gestiona ? '. Podés subir el orden del día y, después de la reunión, el acta' : ''}.</p>}
            {r.gestiona && (
              <div className="d-flex flex-wrap gap-2 mt-2">
                <select className="form-select form-select-sm" style={{ maxWidth: 160 }} value={tipoDoc} onChange={e => setTipoDoc(e.target.value)}>
                  {r.tipos_doc.map(t => <option key={t.valor} value={t.valor}>{t.texto}</option>)}
                </select>
                <input type="file" className="form-control form-control-sm flex-grow-1" style={{ minWidth: 180 }} multiple accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,image/*"
                  onChange={e => setDocs([...e.target.files])} />
                <button type="button" className="btn btn-outline-primary btn-sm text-nowrap" disabled={!docs.length || ocupado}
                  onClick={async () => { await accion({ accion: 'subir_doc', tipo: tipoDoc }, { doc: docs }); setDocs([]) }}>
                  <i className="bi bi-upload me-1" /> Subir
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Participantes */}
        <div className="col-lg-5">
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="panel-card-header d-flex align-items-center justify-content-between">
              <h5 className="mb-0">Participantes</h5>
              <small className="text-secondary">{confirmados.length} confirmado{confirmados.length === 1 ? '' : 's'}</small>
            </div>
            {r.gestiona && empezo && confirmados.length > 0 ? (
              <>
                <p className="small text-secondary mb-1">Marcá quiénes asistieron:</p>
                {confirmados.map(p => (
                  <label className="data-row meet-person" key={p.id}>
                    <span>
                      <input type="checkbox" className="form-check-input me-2" checked={presentes.includes(p.id)}
                        onChange={e => setPresentes(x => e.target.checked ? [...x, p.id] : x.filter(y => y !== p.id))} />
                      {p.usuario.nombre}
                    </span>
                    <span className="meet-role">{p.rol_texto}</span>
                  </label>
                ))}
                <button type="button" className="btn btn-outline-primary btn-sm mt-2" disabled={ocupado} onClick={() => accion({ accion: 'asistencia', presentes })}>Guardar asistencia</button>
              </>
            ) : (
              <>
                {confirmados.map(p => (
                  <Persona p={p} key={p.id}><i className="bi bi-check-circle-fill text-success me-1" /> {p.usuario.nombre}{p.asistio && <small className="text-success"> · asistió</small>}</Persona>
                ))}
                {!confirmados.length && <p className="text-secondary small mb-0">Todavía nadie confirmó.</p>}
              </>
            )}
            {pendientes.length > 0 && <div className="small fw-semibold text-secondary mt-3 mb-1">Sin responder</div>}
            {pendientes.map(p => <Persona p={p} key={p.id}><i className="bi bi-hourglass-split text-warning-emphasis me-1" /> {p.usuario.nombre}</Persona>)}
            {rechazaron.length > 0 && <div className="small fw-semibold text-secondary mt-3 mb-1">No van</div>}
            {rechazaron.map(p => <Persona p={p} key={p.id}><span className="text-secondary"><i className="bi bi-x-circle me-1" /> {p.usuario.nombre}</span></Persona>)}
          </div>

          {r.gestiona && (
            <div className="d-flex gap-2">
              {r.activa && (
                <button type="button" className="btn btn-outline-secondary btn-sm flex-grow-1" disabled={ocupado}
                  onClick={() => accion({ accion: 'cancelar' }, {}, { confirmar: '¿Cancelar la reunión?' + (r.con_google ? ' También se borra de Google Calendar y se les avisa a los invitados.' : '') })}>
                  <i className="bi bi-calendar-x me-1" /> Cancelar reunión
                </button>
              )}
              <button type="button" className="btn btn-outline-danger btn-sm flex-grow-1" disabled={ocupado}
                onClick={() => accion({ accion: 'eliminar' }, {}, { confirmar: '¿Eliminar la reunión con sus participantes y documentos?' })}>
                <i className="bi bi-trash me-1" /> Eliminar
              </button>
            </div>
          )}
        </div>
      </div>

      <Modal titulo="Invitar proveedor" abierto={invitar} onCerrar={() => setInvitar(false)} pie={
        <>
          <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setInvitar(false)}>Cancelar</button>
          <button type="button" className="btn btn-primary btn-sm px-3" disabled={!elegido.length || ocupado}
            onClick={async () => { await accion({ accion: 'invitar', id_proveedor: elegido[0] }); setInvitar(false) }}>
            <i className="bi bi-send me-1" /> Enviar invitación
          </button>
        </>
      }>
        <p className="small text-secondary">Le llega la invitación por chat y participa solo si la acepta.</p>
        <ElegirProveedores proveedores={r.proveedores} elegidos={elegido} onCambiar={setElegido} multiple={false} />
      </Modal>
    </>
  )
}
