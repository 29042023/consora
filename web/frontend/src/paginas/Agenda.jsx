import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import { useAccion } from '../acciones'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useTitulo } from '../componentes/Layout'
import Modal from '../componentes/Modal'
import { diaLargo } from '../formatos'

export default function Agenda() {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  const [modal, setModal] = useState(false)
  const [form, setForm] = useState({})

  const cargar = useCallback(() => { api.get('/agenda/').then(setDatos).catch(e => setError(e.message)) }, [])
  useEffect(cargar, [cargar])
  const [accion, ocupado] = useAccion('/agenda/', cargar)

  useTitulo('Agenda', 'Tus próximos eventos y vencimientos')

  const abrir = () => { setForm({ titulo: '', descripcion: '', fecha: datos.manana, hora: '', id_consorcio: '' }); setModal(true) }
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const guardar = async e => {
    e.preventDefault()
    if (await accion(form)) setModal(false)
  }

  if (error) return <ErrorCarga error={error} />
  if (!datos) return <Cargando />

  const titulo = f => (f === datos.hoy ? 'Hoy' : f === datos.manana ? 'Mañana' : diaLargo(f))

  return (
    <>
      <div className="page-header-row d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2>Agenda</h2>
          <p>Reuniones, visitas de proveedores y vencimientos de tus consorcios.</p>
        </div>
        {datos.puede_agendar && <button type="button" className="btn btn-primary btn-sm px-3" onClick={abrir}><i className="bi bi-plus-lg me-1" /> Agendar evento</button>}
      </div>

      {datos.dias.map(d => (
        <div key={d.fecha} className="mb-4">
          <div className="history-month">{titulo(d.fecha)}</div>
          <div className="panel-card" style={{ height: 'auto' }}>
            {d.eventos.map(e => (
              <div className="invoice-row" key={e.id}>
                <div className="invoice-icon"><i className="bi bi-calendar-event" /></div>
                <div className="flex-grow-1 min-w-0">
                  <div className="invoice-title">{e.titulo}</div>
                  <div className="invoice-meta">
                    {e.hora ? <span className="fw-semibold">{e.hora} h</span> : 'Todo el día'}
                    {e.consorcio && <> · <span className="chip-soft"><i className="bi bi-building" /> {e.consorcio}</span></>}
                    {e.descripcion && <> · {e.descripcion}</>}
                  </div>
                </div>
                {e.propio && datos.puede_agendar && (
                  <button type="button" className="btn-icon-soft" title="Eliminar" disabled={ocupado}
                    onClick={() => accion({ accion: 'eliminar', id_evento: e.id }, {}, { confirmar: '¿Eliminar el evento?' })}>
                    <i className="bi bi-trash" />
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      ))}
      {!datos.dias.length && <Vacio icono="bi-calendar-event" titulo="No hay eventos próximos" texto={datos.puede_agendar ? 'Agendá uno con "Agendar evento".' : 'Cuando haya eventos en tu edificio, los vas a ver acá.'} />}

      <Modal titulo="Agendar evento" abierto={modal} onCerrar={() => setModal(false)} onSubmit={guardar} pie={
        <>
          <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setModal(false)}>Cancelar</button>
          <button type="submit" className="btn btn-primary btn-sm px-3" disabled={ocupado}>Guardar</button>
        </>
      }>
        <label className="form-label small fw-semibold">Título</label>
        <input className="form-control mb-3" maxLength={150} placeholder="Ej.: Visita técnica del ascensor" value={form.titulo || ''} onChange={e => set('titulo', e.target.value)} />
        <label className="form-label small fw-semibold">Descripción (opcional)</label>
        <textarea className="form-control mb-3" rows="2" value={form.descripcion || ''} onChange={e => set('descripcion', e.target.value)} />
        <div className="row g-3">
          <div className="col-sm-6">
            <label className="form-label small fw-semibold">Fecha</label>
            <input type="date" className="form-control" min={datos.hoy} value={form.fecha || ''} onChange={e => set('fecha', e.target.value)} />
          </div>
          <div className="col-sm-6">
            <label className="form-label small fw-semibold">Hora (opcional)</label>
            <input type="time" className="form-control" value={form.hora || ''} onChange={e => set('hora', e.target.value)} />
          </div>
          <div className="col-12">
            <label className="form-label small fw-semibold">Consorcio (opcional)</label>
            <select className="form-select" value={form.id_consorcio || ''} onChange={e => set('id_consorcio', e.target.value)}>
              <option value="">Sin consorcio</option>
              {datos.mis_consorcios.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
            </select>
          </div>
        </div>
      </Modal>
    </>
  )
}
