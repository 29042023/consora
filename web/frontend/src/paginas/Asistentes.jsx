import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import { useAccion } from '../acciones'
import Avatar from '../componentes/Avatar'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useTitulo } from '../componentes/Layout'
import { fecha, fechaHora } from '../formatos'

/** Un asistente del equipo: acceso general, edificios habilitados, tareas e historial de actividad. */
function TarjetaAsistente({ a, misConsorcios, accion, ocupado }) {
  const [abierto, setAbierto] = useState(false)
  const [tarea, setTarea] = useState('')
  const [asignar, setAsignar] = useState('')
  const hacer = (campos, opciones) => accion({ id_asistente: a.id, ...campos }, {}, { quedarse: true, ...opciones })
  const sinAsignar = misConsorcios.filter(c => !a.consorcios.some(x => x.id === c.id))
  const enProceso = a.tareas.filter(t => !t.completada).length

  return (
    <div className="panel-card mb-3" style={{ height: 'auto' }}>
      <div className="d-flex align-items-center gap-3 flex-wrap">
        <Avatar img={a.usuario.img} iniciales={a.usuario.iniciales} clase="sr-avatar redondo" />
        <div className="flex-grow-1 min-w-0">
          <div className="fw-bold">{a.usuario.nombre}</div>
          <div className="small text-secondary">{a.usuario.mail}{a.usuario.telefono && ` · ${a.usuario.telefono}`} · desde el {fecha(a.creado_en)}</div>
          <div className="small mt-1">
            <span className="chip-soft">{a.consorcios.filter(c => c.habilitado).length} edificio(s)</span>{' '}
            <span className="chip-soft">{enProceso} tarea(s) en proceso</span>
          </div>
        </div>
        <div className="form-check form-switch m-0" title="Acceso a CONSORA">
          <input className="form-check-input" type="checkbox" checked={a.habilitado} disabled={ocupado}
            onChange={() => hacer({ accion: 'habilitar_asistente' })} />
          <label className="form-check-label small">{a.habilitado ? 'Habilitado' : 'Deshabilitado'}</label>
        </div>
        <button type="button" className="btn btn-outline-primary btn-sm" onClick={() => setAbierto(x => !x)}>
          {abierto ? 'Cerrar' : 'Gestionar'} <i className={'bi ' + (abierto ? 'bi-chevron-up' : 'bi-chevron-down')} />
        </button>
      </div>

      {abierto && (
        <div className="row g-4 mt-1">
          <div className="col-md-6">
            <div className="small fw-semibold text-secondary mb-2">Edificios</div>
            {a.consorcios.map(c => (
              <label className="data-row" key={c.id_asignacion} style={{ cursor: 'pointer' }}>
                <span><i className="bi bi-building me-1" /> {c.nombre}</span>
                <span className="form-check form-switch m-0">
                  <input className="form-check-input" type="checkbox" checked={c.habilitado} disabled={ocupado}
                    onChange={() => hacer({ accion: 'habilitar_consorcio', id_asignacion: c.id_asignacion })} />
                </span>
              </label>
            ))}
            {!a.consorcios.length && <p className="small text-secondary">Todavía no tiene edificios asignados.</p>}
            {sinAsignar.length > 0 && (
              <div className="d-flex gap-2 mt-2">
                <select className="form-select form-select-sm" value={asignar} onChange={e => setAsignar(e.target.value)}>
                  <option value="">Asignar otro edificio…</option>
                  {sinAsignar.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
                </select>
                <button type="button" className="btn btn-outline-primary btn-sm" disabled={!asignar || ocupado}
                  onClick={async () => { await hacer({ accion: 'asignar_consorcio', id_consorcio: asignar }); setAsignar('') }}>Asignar</button>
              </div>
            )}

            <div className="small fw-semibold text-secondary mt-4 mb-2">Actividad reciente</div>
            {a.historial.map((h, i) => (
              <div className="data-row" key={i}><span className="small">{h.descripcion}</span><small className="text-secondary text-nowrap ms-2">{fechaHora(h.fecha)}</small></div>
            ))}
            {!a.historial.length && <p className="small text-secondary">Sin actividad registrada.</p>}
          </div>

          <div className="col-md-6">
            <div className="small fw-semibold text-secondary mb-2">Tareas</div>
            {a.tareas.map(t => (
              <div className="data-row" key={t.id}>
                <label className={'small ' + (t.completada ? 'text-decoration-line-through text-secondary' : '')} style={{ cursor: 'pointer' }}>
                  <input type="checkbox" className="form-check-input me-2" checked={t.completada} disabled={ocupado}
                    onChange={() => hacer({ accion: 'cambiar_tarea', id_tarea: t.id })} />
                  {t.descripcion}
                </label>
                <button type="button" className="btn-icon-soft" title="Eliminar" disabled={ocupado}
                  onClick={() => hacer({ accion: 'borrar_tarea', id_tarea: t.id }, { confirmar: '¿Eliminar la tarea?' })}><i className="bi bi-trash" /></button>
              </div>
            ))}
            {!a.tareas.length && <p className="small text-secondary">No tiene tareas.</p>}
            <div className="d-flex gap-2 mt-2">
              <input className="form-control form-control-sm" placeholder="Nueva tarea" value={tarea} onChange={e => setTarea(e.target.value)} />
              <button type="button" className="btn btn-primary btn-sm text-nowrap" disabled={!tarea.trim() || ocupado}
                onClick={async () => { await hacer({ accion: 'nueva_tarea', descripcion: tarea }); setTarea('') }}><i className="bi bi-plus-lg" /> Agregar</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default function Asistentes() {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  const cargar = useCallback(() => { api.get('/asistentes/').then(setDatos).catch(e => setError(e.message)) }, [])
  useEffect(cargar, [cargar])
  const [accion, ocupado] = useAccion('/asistentes/', cargar)
  useTitulo('Asistentes', 'Gestioná las cuentas de tu equipo y sus permisos')

  if (error) return <ErrorCarga error={error} />
  if (!datos) return <Cargando />
  const activos = datos.asistentes.filter(a => a.habilitado).length

  return (
    <>
      <div className="row g-3 mb-4">
        {[['bi-people', datos.asistentes.length, 'Asistentes'], ['bi-person-check', activos, 'Habilitados'],
          ['bi-buildings', datos.mis_consorcios.length, 'Tus consorcios']].map(([i, v, t]) => (
          <div className="col-4" key={t}>
            <div className="panel-card d-flex align-items-center gap-3" style={{ height: 'auto' }}>
              <div className="invoice-icon"><i className={'bi ' + i} /></div>
              <div><div className="fw-bold fs-5">{v}</div><div className="small text-secondary">{t}</div></div>
            </div>
          </div>
        ))}
      </div>
      {datos.asistentes.map(a => <TarjetaAsistente key={a.id} a={a} misConsorcios={datos.mis_consorcios} accion={accion} ocupado={ocupado} />)}
      {!datos.asistentes.length && <Vacio icono="bi-person-badge" titulo="Todavía no tenés asistentes" texto="Las cuentas de tu equipo van a aparecer acá." />}
    </>
  )
}
