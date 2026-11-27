import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api'
import { useAccion } from '../acciones'
import Avatar from '../componentes/Avatar'
import { useAvisos, useTitulo } from '../componentes/Layout'
import { fechaHora, normalizar } from '../formatos'
import { LOGO_MEET } from './Reuniones'

const CLASE_ROL = { 'Propietario/Residente': 'propietario', Proveedor: 'proveedor', Asistente: 'asistente' }
const FILTROS = [['', 'Todos'], ['propietario', 'Propietarios'], ['proveedor', 'Proveedores'], ['asistente', 'Equipo']]
const CADA = 5000 // los mensajes nuevos se consultan cada 5 segundos

/** Tarjeta de invitación a una votación de contratación (el proveedor acepta con su PDF o rechaza). */
function InvitacionVotacion({ inv, onRespondida }) {
  const avisar = useAvisos()
  const [pdf, setPdf] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const v = inv.votacion
  const responder = async respuesta => {
    if (respuesta === 'rechazar' && !window.confirm('¿Rechazar la invitación?')) return
    const datos = new FormData()
    datos.append('respuesta', respuesta)
    if (pdf) datos.append('propuesta_pdf', pdf)
    setEnviando(true)
    try {
      const r = await api.post(`/invitaciones/votacion/${inv.id}/`, datos)
      r.mensajes.forEach(m => avisar(m.texto, m.tipo))
    } catch (err) {
      err.errores.forEach(e => avisar(e, 'error'))
    } finally {
      setEnviando(false)
      onRespondida()
    }
  }
  return (
    <div className="chat-invite">
      <div className="chat-invite-head"><i className="bi bi-check2-square" /> Votación de contratación</div>
      <div className="chat-invite-meta">
        <span><i className="bi bi-building" /> {v.consorcio.nombre}</span>
        <span><i className="bi bi-clock" /> Cierra {fechaHora(v.fecha_limite)}</span>
      </div>
      {inv.aceptacion === null && v.abierta && inv.para_mi ? (
        <div className="chat-invite-form">
          <label className="small fw-semibold mb-1">Tu propuesta en PDF (opcional)</label>
          <input type="file" accept="application/pdf,.pdf" className="form-control form-control-sm mb-2" onChange={e => setPdf(e.target.files[0] || null)} />
          <div className="d-flex gap-2">
            <button type="button" className="btn btn-success btn-sm flex-fill" disabled={enviando} onClick={() => responder('aceptar')}><i className="bi bi-check-lg" /> Aceptar</button>
            <button type="button" className="btn btn-outline-danger btn-sm flex-fill" disabled={enviando} onClick={() => responder('rechazar')}><i className="bi bi-x-lg" /> Rechazar</button>
          </div>
        </div>
      ) : inv.aceptacion ? <div className="chat-invite-state ok"><i className="bi bi-check-circle-fill" /> Aceptada{inv.contratado && ' · Contratado'}</div>
        : inv.aceptacion === false ? <div className="chat-invite-state no"><i className="bi bi-x-circle-fill" /> Rechazada</div>
          : !v.abierta ? <div className="chat-invite-state">La votación cerró sin respuesta</div>
            : <div className="chat-invite-state">Esperando respuesta del proveedor</div>}
      {!inv.para_mi && <Link to={`/votaciones/${v.id}`} className="chat-invite-link">Ver votación <i className="bi bi-arrow-right" /></Link>}
    </div>
  )
}

/** Tarjeta de invitación a una reunión (el proveedor acepta o rechaza; recién al aceptar ve el link). */
function InvitacionReunion({ inv, onRespondida }) {
  const avisar = useAvisos()
  const [enviando, setEnviando] = useState(false)
  const r = inv.reunion
  const responder = async respuesta => {
    if (respuesta === 'rechazar' && !window.confirm('¿Rechazar la invitación?')) return
    const datos = new FormData()
    datos.append('respuesta', respuesta)
    setEnviando(true)
    try {
      const x = await api.post(`/invitaciones/reunion/${inv.id}/`, datos)
      x.mensajes.forEach(m => avisar(m.texto, m.tipo))
    } catch (err) {
      err.errores.forEach(e => avisar(e, 'error'))
    } finally {
      setEnviando(false)
      onRespondida()
    }
  }
  return (
    <div className="chat-invite meet">
      <div className="chat-invite-head"><i className="bi bi-camera-video" /> Invitación a una reunión</div>
      <div className="fw-semibold small mb-1">{r.titulo}</div>
      <div className="chat-invite-meta">
        <span><i className="bi bi-building" /> {r.consorcio.nombre}</span>
        <span><i className="bi bi-clock" /> {fechaHora(r.fecha_inicio)}</span>
        <span>{r.es_virtual ? <><img src={LOGO_MEET} alt="" className="meet-logo" /> Google Meet</> : <><i className="bi bi-geo-alt" /> {r.lugar}</>}</span>
      </div>
      {inv.respuesta === 'pendiente' && r.activa && inv.para_mi ? (
        <div className="chat-invite-form d-flex gap-2">
          <button type="button" className="btn btn-success btn-sm flex-fill" disabled={enviando} onClick={() => responder('aceptar')}><i className="bi bi-check-lg" /> Aceptar</button>
          <button type="button" className="btn btn-outline-danger btn-sm flex-fill" disabled={enviando} onClick={() => responder('rechazar')}><i className="bi bi-x-lg" /> Rechazar</button>
        </div>
      ) : inv.respuesta === 'acepta' ? <div className="chat-invite-state ok"><i className="bi bi-check-circle-fill" /> Aceptada · participa de la reunión</div>
        : inv.respuesta === 'rechaza' ? <div className="chat-invite-state no"><i className="bi bi-x-circle-fill" /> Rechazada</div>
          : r.estado === 'cancelada' ? <div className="chat-invite-state">La reunión se canceló</div>
            : !r.activa ? <div className="chat-invite-state">La reunión terminó sin respuesta</div>
              : <div className="chat-invite-state">Esperando respuesta del proveedor</div>}
      {inv.respuesta !== 'rechaza' && <Link to={`/reuniones/${r.id}`} className="chat-invite-link">Ver reunión <i className="bi bi-arrow-right" /></Link>}
    </div>
  )
}

function Burbuja({ m, recargar }) {
  return (
    <div className={'chat-bubble-row' + (m.mio ? ' mine' : '')}>
      <div className={'chat-bubble' + (m.archivo && m.es_imagen ? ' has-image' : '')}>
        {m.archivo && (m.es_imagen
          ? <a href={m.archivo} target="_blank" rel="noreferrer" className="chat-image"><img src={m.archivo} alt={m.nombre_archivo} /></a>
          : (
            <a href={m.archivo} target="_blank" rel="noreferrer" download={m.nombre_archivo} className={'chat-file ' + (m.tipo_archivo || '')}>
              <span className="chat-file-icon"><i className={'bi ' + ({ pdf: 'bi-file-earmark-pdf-fill', word: 'bi-file-earmark-word-fill', excel: 'bi-file-earmark-excel-fill' }[m.tipo_archivo] || 'bi-file-earmark-fill')} /></span>
              <span className="chat-file-name">{m.nombre_archivo}</span>
              <i className="bi bi-download chat-file-download" />
            </a>
          ))}
        {m.texto && <div className="chat-bubble-text">{m.texto}</div>}
        {m.invitacion && <InvitacionVotacion inv={m.invitacion} onRespondida={recargar} />}
        {m.invitacion_reunion && <InvitacionReunion inv={m.invitacion_reunion} onRespondida={recargar} />}
        <span className="chat-bubble-time">{fechaHora(m.enviado_en)}</span>
      </div>
    </div>
  )
}

export default function Chat() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [chats, setChats] = useState(null)
  const [activo, setActivo] = useState(null)
  const [buscar, setBuscar] = useState('')
  const [filtro, setFiltro] = useState('')
  const [texto, setTexto] = useState('')
  const [archivo, setArchivo] = useState(null)
  const fin = useRef(null)
  const inputArchivo = useRef(null)
  const cantMensajes = useRef(0)

  useTitulo('Chat', 'Comunicate con propietarios, proveedores y tu equipo')

  const cargarLista = useCallback(() => api.get('/chats/').then(d => setChats(d.chats)).catch(() => {}), [])
  const idActivo = id || chats?.[0]?.id
  const cargarActivo = useCallback(() => {
    if (!idActivo) return
    api.get(`/chats/${idActivo}/`).then(setActivo).catch(() => setActivo(null))
  }, [idActivo])

  useEffect(() => { cargarLista() }, [cargarLista])
  useEffect(() => { cargarActivo() }, [cargarActivo])
  // Mensajes nuevos: se vuelve a consultar cada pocos segundos
  useEffect(() => {
    const t = setInterval(() => { cargarLista(); cargarActivo() }, CADA)
    return () => clearInterval(t)
  }, [cargarLista, cargarActivo])
  // Baja al último mensaje cuando llegan mensajes nuevos
  useEffect(() => {
    const n = activo?.mensajes.length || 0
    if (n !== cantMensajes.current) { cantMensajes.current = n; fin.current?.scrollIntoView({ block: 'end' }) }
  }, [activo])

  const recargar = useCallback(() => { cargarLista(); cargarActivo() }, [cargarLista, cargarActivo])
  const [enviar, enviando] = useAccion(`/chats/${idActivo}/`, recargar)

  const visibles = useMemo(() => (chats || []).filter(c => {
    const rol = CLASE_ROL[c.otro?.rol] || 'admin'
    return (!filtro || rol === filtro) && (!buscar || normalizar(c.otro?.nombre).includes(normalizar(buscar)))
  }), [chats, filtro, buscar])

  const mandar = async e => {
    e.preventDefault()
    if (!texto.trim() && !archivo) return
    const ok = await enviar({ chat_id: idActivo, mensaje: texto }, archivo ? { archivo: [archivo] } : {}, { quedarse: true })
    if (ok) { setTexto(''); setArchivo(null); if (inputArchivo.current) inputArchivo.current.value = '' }
  }

  const otro = activo?.otro

  return (
    <div className="chat-shell app-chat">
      <div className="chat-list-panel">
        <div className="chat-list-search">
          <div className="input-wrap">
            <i className="bi bi-search" />
            <input type="text" placeholder="Buscar conversación..." value={buscar} onChange={e => setBuscar(e.target.value)} />
          </div>
        </div>
        <div className="chat-filters">
          {FILTROS.map(([v, t]) => (
            <span key={v} className={'chat-filter-chip' + (filtro === v ? ' active' : '')} onClick={() => setFiltro(v)}>{t}</span>
          ))}
        </div>
        <div className="chat-list-scroll">
          {visibles.map(c => {
            const rol = CLASE_ROL[c.otro?.rol]
            return (
              <Link key={c.id} to={`/chat/${c.id}`} className={'chat-list-item' + (String(c.id) === String(idActivo) ? ' selected' : '')}>
                <div className={'chat-list-avatar bg-' + (rol || 'admin')}>
                  {c.otro && <Avatar img={c.otro.img} iniciales={c.otro.iniciales} clase="" />}
                </div>
                <div className="chat-list-body">
                  <div className="chat-list-top">
                    <span className="chat-list-name">{c.otro?.nombre || 'Conversación'}</span>
                    {c.ultimo && <span className="chat-list-time">{fechaHora(c.ultimo.enviado_en)}</span>}
                  </div>
                  {rol && <span className={'chat-list-tag ' + rol}>{rol === 'asistente' ? 'Asistente' : rol === 'proveedor' ? 'Proveedor' : 'Propietario'}</span>}
                  <div className="chat-list-preview">{c.ultimo ? (c.ultimo.mio ? 'Vos: ' : '') + c.ultimo.texto : 'Sin mensajes todavía'}</div>
                </div>
              </Link>
            )
          })}
          {chats && !visibles.length && <div className="text-secondary small p-4 text-center">No hay conversaciones.</div>}
        </div>
      </div>

      <div className="chat-active-panel">
        {activo ? (
          <>
            <div className="chat-active-header">
              <div className={'chat-active-avatar bg-' + (CLASE_ROL[otro?.rol] || 'admin')}>
                {otro && <Avatar img={otro.img} iniciales={otro.iniciales} clase="" />}
              </div>
              <div>
                <p className="chat-active-name">
                  {otro?.rol === 'Proveedor' ? <Link to={`/marketplace/proveedor/${otro.id}`} className="text-reset">{otro.nombre}</Link> : otro?.nombre}
                </p>
                <span className="chat-active-meta">{otro?.rol}</span>
              </div>
            </div>
            <div className="chat-messages">
              {activo.mensajes.map(m => <Burbuja key={m.id} m={m} recargar={recargar} />)}
              {!activo.mensajes.length && <div className="text-secondary small text-center py-4">Todavía no hay mensajes en esta conversación.</div>}
              <div ref={fin} />
            </div>
            {archivo && (
              <div className="chat-attach-preview">
                <i className="bi bi-paperclip" /> <span>{archivo.name}</span>
                <button type="button" className="btn-close btn-sm ms-auto" title="Quitar archivo" onClick={() => { setArchivo(null); inputArchivo.current.value = '' }} />
              </div>
            )}
            <form className="chat-input-row" onSubmit={mandar}>
              <input type="file" ref={inputArchivo} hidden accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,image/jpeg,image/png,image/gif,image/webp"
                onChange={e => setArchivo(e.target.files[0] || null)} />
              <button type="button" className="btn-icon" title="Adjuntar archivo" onClick={() => inputArchivo.current.click()}><i className="bi bi-paperclip" /></button>
              <input type="text" placeholder="Escribí un mensaje..." value={texto} onChange={e => setTexto(e.target.value)} />
              <button type="submit" className="chat-send-btn" disabled={enviando}><i className="bi bi-send-fill" /></button>
            </form>
          </>
        ) : (
          <div className="text-secondary small text-center p-5">
            {chats && !chats.length ? 'Todavía no tenés conversaciones.' : 'Elegí una conversación.'}
            {chats && !chats.length && <div><button type="button" className="btn btn-link btn-sm" onClick={() => navigate('/marketplace')}>Buscar proveedores en el Marketplace</button></div>}
          </div>
        )}
      </div>
    </div>
  )
}
