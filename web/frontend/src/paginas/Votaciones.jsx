import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { useAccion } from '../acciones'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import ElegirProveedores from '../componentes/ElegirProveedores'
import { useTitulo } from '../componentes/Layout'
import Modal from '../componentes/Modal'
import TabsConsorcio from '../componentes/TabsConsorcio'
import { fechaHora } from '../formatos'

/** Formulario de nueva votación: común (Sí / No / Abstención) o de contratación (con proveedores invitados). */
function NuevaVotacion({ datos, consorcioInicial, abierto, onCerrar }) {
  const [enviar, ocupado] = useAccion('/votaciones/')
  const [form, setForm] = useState({})
  const [proveedores, setProveedores] = useState([])
  const [docs, setDocs] = useState([])

  useEffect(() => {
    if (!abierto) return
    setForm({ id_consorcio: consorcioInicial || datos.mis_consorcios[0]?.id || '', tipo: 'comun', detalle: '', fecha_limite: datos.limite_sugerido })
    setProveedores([])
    setDocs([])
  }, [abierto]) // eslint-disable-line

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const crear = e => {
    e.preventDefault()
    enviar({ ...form, proveedores: form.tipo === 'contratacion' ? proveedores : [] }, { docs })
  }

  return (
    <Modal titulo="Nueva votación" abierto={abierto} onCerrar={onCerrar} onSubmit={crear} pie={
      <>
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={onCerrar}>Cancelar</button>
        <button type="submit" className="btn btn-primary btn-sm px-3" disabled={ocupado}>{ocupado ? 'Creando…' : 'Crear votación'}</button>
      </>
    }>
      <label className="form-label small fw-semibold">Consorcio</label>
      <select className="form-select mb-3" value={form.id_consorcio || ''} onChange={e => set('id_consorcio', e.target.value)}>
        {datos.mis_consorcios.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
      </select>

      <label className="form-label small fw-semibold">Tipo</label>
      <div className="vote-type-picker mb-3">
        <label>
          <input type="radio" checked={form.tipo === 'comun'} onChange={() => set('tipo', 'comun')} />
          <span><i className="bi bi-check2-square" /> <strong>Común</strong><small>Sí, No o Abstención</small></span>
        </label>
        <label>
          <input type="radio" checked={form.tipo === 'contratacion'} onChange={() => set('tipo', 'contratacion')} />
          <span><i className="bi bi-briefcase" /> <strong>Contratación</strong><small>Se vota entre propuestas de proveedores</small></span>
        </label>
      </div>

      <label className="form-label small fw-semibold">Detalle</label>
      <textarea className="form-control mb-3" rows="3" placeholder="Qué se vota" value={form.detalle || ''} onChange={e => set('detalle', e.target.value)} />

      <label className="form-label small fw-semibold">Fecha límite</label>
      <input type="datetime-local" className="form-control mb-3" min={datos.ahora_min} value={form.fecha_limite || ''} onChange={e => set('fecha_limite', e.target.value)} />

      {form.tipo === 'contratacion' && (
        <div className="mb-3">
          <label className="form-label small fw-semibold">Proveedores a invitar</label>
          <p className="small text-secondary mb-2">Les llega una invitación por chat. La votación se lanza cuando alguno acepta.</p>
          <ElegirProveedores proveedores={datos.proveedores} elegidos={proveedores} onCambiar={setProveedores} />
        </div>
      )}

      <label className="form-label small fw-semibold">Documentos (opcional)</label>
      <input type="file" className="form-control" multiple accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,image/*" onChange={e => setDocs([...e.target.files])} />
      <div className="form-text">Presupuestos, planos o fotos: PDF, Word, Excel o imágenes de hasta 15 MB.</div>
    </Modal>
  )
}

export default function Votaciones() {
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
    api.get('/votaciones/?' + q).then(setDatos).catch(e => setError(e.message))
  }, [consorcio, estado])
  useEffect(cargar, [cargar])

  useTitulo('Votaciones', 'Decisiones de los consorcios: votaciones comunes y contrataciones')

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
      {filtrado && <TabsConsorcio id={filtrado.id} activa="votaciones" />}
      <div className="page-header-row d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2>{filtrado ? `Votaciones de ${filtrado.nombre}` : 'Votaciones'}</h2>
          <p>Los propietarios votan; el administrador crea, cierra y registra la contratación.</p>
        </div>
        {datos.puede_gestionar && (
          <button type="button" className="btn btn-primary btn-sm px-3" disabled={!datos.mis_consorcios.length} onClick={() => setModal(true)}>
            <i className="bi bi-plus-lg me-1" /> Nueva votación
          </button>
        )}
      </div>

      <div className="vote-filters">
        <select className="form-select form-select-sm" value={consorcio} onChange={e => filtro('consorcio', e.target.value)}>
          <option value="">Todos los consorcios</option>
          {datos.mis_consorcios.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
        </select>
        <div className="vote-chips">
          {[['', 'Todas'], ['abiertas', 'Abiertas'], ['cerradas', 'Cerradas']].map(([v, t]) => (
            <button key={v} type="button" className={'vote-chip' + (estado === v ? ' active' : '')} onClick={() => filtro('estado', v)}>{t}</button>
          ))}
        </div>
      </div>

      <div className="row g-3">
        {datos.votaciones.map(v => (
          <div className="col-md-6 col-xl-4" key={v.id}>
            <Link to={`/votaciones/${v.id}`} className="vote-card">
              <div className="d-flex align-items-center justify-content-between gap-2 mb-2">
                <span className={'vote-type ' + v.tipo}>
                  <i className={'bi ' + (v.tipo === 'comun' ? 'bi-check2-square' : 'bi-briefcase')} /> {v.tipo_texto}
                </span>
                {v.abierta
                  ? <span className="vote-state open">{v.lanzada ? <><i className="bi bi-circle-fill" /> Abierta</> : 'Esperando proveedores'}</span>
                  : <span className="vote-state closed">Cerrada</span>}
              </div>
              <p className="vote-detail">{v.detalle.length > 120 ? v.detalle.slice(0, 120) + '…' : v.detalle}</p>
              <div className="vote-meta">
                <span><i className="bi bi-building" /> {v.consorcio.nombre}</span>
                <span><i className="bi bi-calendar-event" /> {v.abierta ? 'Cierra' : 'Cerró'} {fechaHora(v.fecha_limite)}</span>
              </div>
              <div className="vote-foot">
                <span><i className="bi bi-people" /> {v.cant_votos} voto{v.cant_votos === 1 ? '' : 's'}</span>
                {v.tipo === 'contratacion' && <span><i className="bi bi-briefcase" /> {v.cant_propuestas} proveedor{v.cant_propuestas === 1 ? '' : 'es'}</span>}
                {v.ya_vote && <span className="ms-auto text-success fw-semibold"><i className="bi bi-check-circle-fill" /> Votaste</span>}
              </div>
            </Link>
          </div>
        ))}
        {!datos.votaciones.length && (
          <div className="col-12">
            <Vacio icono="bi-check2-square" titulo="No hay votaciones" texto={datos.puede_gestionar ? 'Creá una con "Nueva votación".' : 'Cuando haya una votación, la vas a ver acá.'} />
          </div>
        )}
      </div>

      {datos.puede_gestionar && (
        <NuevaVotacion datos={datos} consorcioInicial={consorcio} abierto={modal} onCerrar={() => setModal(false)} />
      )}
    </>
  )
}
