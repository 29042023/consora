import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, DJANGO } from '../api'
import { useAccion } from '../acciones'
import Avatar from '../componentes/Avatar'
import { Cargando, ErrorCarga } from '../componentes/Cargando'
import { useTitulo } from '../componentes/Layout'
import Modal from '../componentes/Modal'
import { fecha, moneda } from '../formatos'
import { useSesion } from '../sesion'
import { LOGO_MEET } from './Reuniones'

function Dato({ etiqueta, valor }) {
  return <div className="data-row"><span className="text-secondary">{etiqueta}</span><strong>{valor || '—'}</strong></div>
}

export default function Cuenta() {
  const { salir } = useSesion()
  const navigate = useNavigate()
  const [c, setC] = useState(null)
  const [error, setError] = useState('')
  const [modal, setModal] = useState(null) // 'perfil' | 'password' | 'marketplace' | 'baja'
  const [form, setForm] = useState({})

  const cargar = useCallback(() => { api.get('/cuenta/').then(setC).catch(e => setError(e.message)) }, [])
  useEffect(cargar, [cargar])
  const [accion, ocupado] = useAccion('/cuenta/', cargar)
  useTitulo('Cuenta', 'Tus datos y la facturación de CONSORA')

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const abrir = (cual, inicial = {}) => { setForm(inicial); setModal(cual) }
  const guardar = async (campos, archivos) => {
    if (await accion(campos, archivos, { quedarse: true })) setModal(null)
  }
  const darDeBaja = async () => {
    if (await accion({ accion: 'baja', confirmar_password: form.confirmar_password }, {}, { quedarse: true })) {
      await salir()
      navigate('/login')
    }
  }

  if (error) return <ErrorCarga error={error} />
  if (!c) return <Cargando />
  const s = c.suscripcion

  return (
    <div className="row g-4">
      <div className="col-lg-6">
        {/* Datos personales */}
        <div className="panel-card mb-4" style={{ height: 'auto' }}>
          <div className="d-flex align-items-center gap-3 mb-3">
            <Avatar img={c.img} iniciales={c.iniciales} clase="sr-avatar redondo" />
            <div className="flex-grow-1 min-w-0">
              <h5 className="fw-bold mb-0">{c.nombre}</h5>
              <span className="chip-soft">{c.rol}</span>
            </div>
            <button type="button" className="btn btn-outline-primary btn-sm" onClick={() => abrir('perfil', {
              name_surname: c.nombre, mail: c.mail, dni: c.dni || '', phone: c.telefono || '', date_nacimiento: c.nacimiento || '',
              ...(c.administrador ? { matricula: c.administrador.matricula, fecha_alta_matricula: c.administrador.fecha_alta_matricula || '', ente_otorgante: c.administrador.ente_otorgante || '' } : {}),
            })}><i className="bi bi-pencil" /> Editar</button>
          </div>
          <div className="d-flex gap-2 mb-3 flex-wrap">
            <label className="btn btn-outline-secondary btn-sm mb-0">
              <i className="bi bi-camera" /> Cambiar foto
              <input type="file" accept="image/*" hidden onChange={e => e.target.files[0] && accion({ accion: 'foto' }, { foto: [e.target.files[0]] }, { quedarse: true })} />
            </label>
            {c.img && <button type="button" className="btn btn-link btn-sm text-secondary" onClick={() => accion({ accion: 'quitar_foto' }, {}, { quedarse: true })}>Quitar foto</button>}
          </div>
          <Dato etiqueta="Email" valor={c.mail} />
          <Dato etiqueta="Teléfono" valor={c.telefono} />
          <Dato etiqueta="DNI" valor={c.dni} />
          <Dato etiqueta="Nacimiento" valor={fecha(c.nacimiento)} />
          {c.administrador && (
            <>
              <Dato etiqueta="Matrícula" valor={c.administrador.matricula} />
              <Dato etiqueta="Ente otorgante" valor={c.administrador.ente_otorgante} />
            </>
          )}
          <Dato etiqueta="Estado" valor={c.estado} />
          <Dato etiqueta="Miembro desde" valor={fecha(c.miembro_desde)} />
          <div className="d-grid gap-2 mt-3">
            <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => abrir('password', { actual: '', nueva: '', repetida: '' })}><i className="bi bi-key" /> Cambiar contraseña</button>
            <button type="button" className="btn btn-link btn-sm text-danger" onClick={() => abrir('baja', { confirmar_password: '' })}>Dar de baja mi cuenta</button>
          </div>
        </div>

        {/* Títulos habilitantes (administración) */}
        {c.administrador && (
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0">Título habilitante</h5></div>
            {c.titulos.map(t => (
              <div className="invoice-row" key={t.id}>
                <div className="invoice-icon"><i className="bi bi-file-earmark-pdf text-danger" /></div>
                <a href={t.url} target="_blank" rel="noreferrer" className="flex-grow-1 invoice-title text-decoration-none">{t.nombre}</a>
                <button type="button" className="btn-icon-soft" title="Eliminar" onClick={() => accion({ accion: 'borrar_titulo', id_titulo: t.id }, {}, { confirmar: '¿Eliminar este título?', quedarse: true })}><i className="bi bi-trash" /></button>
              </div>
            ))}
            {!c.titulos.length && <p className="small text-secondary">Subí tu título o matrícula en PDF (Ley 941).</p>}
            <label className="btn btn-outline-primary btn-sm mt-2 mb-0">
              <i className="bi bi-upload" /> Subir PDF
              <input type="file" accept="application/pdf,.pdf" hidden onChange={e => e.target.files[0] && accion({ accion: 'subir_titulo' }, { titulo: [e.target.files[0]] }, { quedarse: true })} />
            </label>
          </div>
        )}

        {/* Perfil del Marketplace */}
        <div className="panel-card mb-4" style={{ height: 'auto' }}>
          <div className="panel-card-header d-flex align-items-center justify-content-between">
            <h5 className="mb-0">Perfil del Marketplace</h5>
            <button type="button" className="btn btn-outline-primary btn-sm" onClick={() => abrir('marketplace', { descripcion: c.marketplace?.descripcion || '', link: c.marketplace?.link || '' })}><i className="bi bi-pencil" /> Editar</button>
          </div>
          {c.marketplace?.descripcion ? <p className="mb-1">{c.marketplace.descripcion}</p> : <p className="small text-secondary">Todavía no completaste tu perfil.</p>}
          {c.marketplace?.link && <a href={c.marketplace.link} target="_blank" rel="noreferrer" className="small">{c.marketplace.link}</a>}
          <div className="mt-2"><Link to={`/marketplace/proveedor/${c.id}`} className="small fw-semibold">Ver mi perfil <i className="bi bi-arrow-right" /></Link></div>
          {c.es_admin && <p className="small text-secondary mt-2 mb-0"><i className="bi bi-eye" /> Tu perfil lo ven solo los propietarios de los consorcios; no aparece entre los proveedores.</p>}
        </div>
      </div>

      <div className="col-lg-6">
        {/* Suscripción */}
        {s && (
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="panel-card-header d-flex align-items-center justify-content-between">
              <h5 className="mb-0">Facturación · {s.periodo}</h5>
              <span className="chip-soft">Plan {s.plan}</span>
            </div>
            {s.lineas.map(l => <div className="data-row" key={l.concepto}><span>{l.concepto} <small className="text-secondary">{l.detalle}</small></span><span>{moneda(l.importe)}</span></div>)}
            <div className="data-row fw-bold fs-6"><span>Total</span><span>{moneda(s.total)}</span></div>
            <div className="d-flex align-items-center justify-content-between mt-3 flex-wrap gap-2">
              {s.pagada ? <span className="text-success fw-semibold"><i className="bi bi-check-circle-fill" /> Pagada</span>
                : <span className="text-warning-emphasis fw-semibold"><i className="bi bi-clock" /> Pendiente · vence {fecha(s.vencimiento)}</span>}
              {!s.pagada && (
                <div className="d-flex gap-2">
                  {['Mercado Pago', 'MODO'].map(m => (
                    <button key={m} type="button" className="btn btn-primary btn-sm" disabled={ocupado} onClick={() => accion({ medio: m }, {}, { quedarse: true })}>Pagar con {m}</button>
                  ))}
                </div>
              )}
            </div>
            <p className="small text-secondary mt-2 mb-0">Pago simulado para la demostración.</p>
          </div>
        )}

        {/* Google Meet (administración) */}
        {c.es_admin && (
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0"><img src={LOGO_MEET} alt="" className="meet-logo me-1" /> Google Meet</h5></div>
            {c.google.email ? (
              <>
                <p className="small mb-2"><i className="bi bi-check-circle-fill text-success" /> Cuenta vinculada: <strong>{c.google.email}</strong>. Las reuniones virtuales se crean en tu Google Calendar con su link de Meet.</p>
                <button type="button" className="btn btn-outline-danger btn-sm" disabled={ocupado}
                  onClick={() => accion({ accion: 'desvincular_google' }, {}, { confirmar: '¿Desvincular tu cuenta de Google?', quedarse: true })}>Desvincular</button>
              </>
            ) : (
              <>
                <p className="small text-secondary">Vinculá tu cuenta de Google para que las reuniones virtuales se creen solas en Google Calendar, con su link de Meet e invitaciones por correo.</p>
                {c.google.disponible
                  ? <a href={`${DJANGO}/google/conectar/?volver=app`} className="btn btn-primary btn-sm"><i className="bi bi-google me-1" /> Vincular cuenta de Google</a>
                  : <button type="button" className="btn btn-outline-secondary btn-sm" disabled>Vincular cuenta de Google</button>}
              </>
            )}
          </div>
        )}

        {/* Consorcios a cargo */}
        {c.consorcios_a_cargo.length > 0 && (
          <div className="panel-card" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0">Consorcios a cargo</h5></div>
            {c.consorcios_a_cargo.map(x => (
              <Link to={`/consorcios/${x.id}/facturacion`} className="invoice-row text-decoration-none text-reset" key={x.id}>
                <div className="invoice-icon"><i className="bi bi-building" /></div>
                <div className="flex-grow-1 min-w-0"><div className="invoice-title">{x.nombre}</div><div className="invoice-meta">{x.direccion}</div></div>
              </Link>
            ))}
          </div>
        )}
      </div>

      {/* Editar datos */}
      <Modal titulo="Editar mis datos" abierto={modal === 'perfil'} onCerrar={() => setModal(null)} pie={
        <button type="button" className="btn btn-primary btn-sm px-3" disabled={ocupado} onClick={() => guardar({ accion: 'perfil', ...form })}>Guardar</button>
      }>
        {[['name_surname', 'Nombre y apellido'], ['mail', 'Email', 'email'], ['phone', 'Teléfono'], ['dni', 'DNI'], ['date_nacimiento', 'Fecha de nacimiento', 'date']]
          .map(([k, t, tipo]) => (
            <div className="mb-2" key={k}>
              <label className="form-label small fw-semibold">{t}</label>
              <input type={tipo || 'text'} className="form-control" value={form[k] || ''} onChange={e => set(k, e.target.value)} />
            </div>
          ))}
        {c.administrador && [['matricula', 'Matrícula'], ['fecha_alta_matricula', 'Fecha de alta de la matrícula', 'date'], ['ente_otorgante', 'Ente otorgante']]
          .map(([k, t, tipo]) => (
            <div className="mb-2" key={k}>
              <label className="form-label small fw-semibold">{t}</label>
              <input type={tipo || 'text'} className="form-control" value={form[k] || ''} onChange={e => set(k, e.target.value)} />
            </div>
          ))}
      </Modal>

      <Modal titulo="Cambiar contraseña" abierto={modal === 'password'} onCerrar={() => setModal(null)} pie={
        <button type="button" className="btn btn-primary btn-sm px-3" disabled={ocupado} onClick={() => guardar({ accion: 'password', ...form })}>Cambiar</button>
      }>
        {[['actual', 'Contraseña actual'], ['nueva', 'Contraseña nueva (mínimo 6 caracteres)'], ['repetida', 'Repetí la contraseña nueva']].map(([k, t]) => (
          <div className="mb-2" key={k}>
            <label className="form-label small fw-semibold">{t}</label>
            <input type="password" className="form-control" value={form[k] || ''} onChange={e => set(k, e.target.value)} />
          </div>
        ))}
      </Modal>

      <Modal titulo="Perfil del Marketplace" abierto={modal === 'marketplace'} onCerrar={() => setModal(null)} pie={
        <button type="button" className="btn btn-primary btn-sm px-3" disabled={ocupado} onClick={() => guardar({ accion: 'marketplace', ...form })}>Guardar</button>
      }>
        <label className="form-label small fw-semibold">Descripción</label>
        <textarea className="form-control mb-3" rows="3" value={form.descripcion || ''} onChange={e => set('descripcion', e.target.value)} />
        <label className="form-label small fw-semibold">Web (opcional)</label>
        <input className="form-control" placeholder="https://..." value={form.link || ''} onChange={e => set('link', e.target.value)} />
      </Modal>

      <Modal titulo="Dar de baja mi cuenta" abierto={modal === 'baja'} onCerrar={() => setModal(null)} pie={
        <button type="button" className="btn btn-danger btn-sm px-3" disabled={ocupado || !form.confirmar_password} onClick={darDeBaja}>Dar de baja</button>
      }>
        <p className="small">La cuenta pasa a estado "Baja" y no vas a poder volver a ingresar. Si administrás consorcios, primero tenés que desasociarte.</p>
        <label className="form-label small fw-semibold">Confirmá con tu contraseña</label>
        <input type="password" className="form-control" value={form.confirmar_password || ''} onChange={e => set('confirmar_password', e.target.value)} />
      </Modal>
    </div>
  )
}
