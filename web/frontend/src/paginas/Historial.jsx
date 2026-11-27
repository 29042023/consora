import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { useAccion } from '../acciones'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useTitulo } from '../componentes/Layout'
import Modal from '../componentes/Modal'
import TabsConsorcio from '../componentes/TabsConsorcio'
import { fecha } from '../formatos'

const ICONOS = {
  contrato: 'bi-file-earmark-text', pago: 'bi-cash-coin', votacion: 'bi-check2-square',
  reunion: 'bi-camera-video', obra: 'bi-tools', otro: 'bi-info-circle',
}

export default function Historial() {
  const [params, setParams] = useSearchParams()
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  const [modal, setModal] = useState(false)
  const [form, setForm] = useState({})
  const consorcio = params.get('consorcio') || ''

  const cargar = useCallback(() => {
    api.get('/historial/?' + params).then(setDatos).catch(e => setError(e.message))
  }, [params])
  useEffect(cargar, [cargar])
  const [accion, ocupado] = useAccion('/historial/', cargar)

  const filtrado = datos?.mis_consorcios.find(c => String(c.id) === consorcio)
  useTitulo('Historial', 'Lo que pasó en cada edificio: contratos, pagos, votaciones, reuniones y obras')

  const filtro = (k, v) => {
    const q = new URLSearchParams(params)
    if (v) q.set(k, v); else q.delete(k)
    setParams(q)
  }
  const abrir = () => {
    setForm({ id_consorcio: consorcio || datos.gestionables[0]?.id, categoria: datos.manuales[0]?.valor, fecha: datos.hoy, descripcion: '' })
    setModal(true)
  }
  const guardar = async e => {
    e.preventDefault()
    if (await accion({ accion: 'agregar', ...form })) setModal(false)
  }

  if (error) return <ErrorCarga error={error} />
  if (!datos) return <Cargando />
  const hayFiltros = params.get('categoria') || params.get('desde') || params.get('hasta')

  return (
    <>
      {filtrado && <TabsConsorcio id={filtrado.id} activa="historial" />}
      <div className="page-header-row d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2>{filtrado ? `Historial de ${filtrado.nombre}` : 'Historial de los edificios'}</h2>
          <p>Contratos, pagos, votaciones, reuniones y obras, en orden de fecha.</p>
        </div>
        {datos.gestionables.length > 0 && (
          <button type="button" className="btn btn-primary btn-sm px-3" onClick={abrir}><i className="bi bi-plus-lg me-1" /> Agregar entrada</button>
        )}
      </div>

      <div className="vote-filters">
        <select className="form-select form-select-sm" value={consorcio} onChange={e => filtro('consorcio', e.target.value)}>
          <option value="">Todos los edificios</option>
          {datos.mis_consorcios.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
        </select>
        <select className="form-select form-select-sm" value={params.get('categoria') || ''} onChange={e => filtro('categoria', e.target.value)}>
          <option value="">Todo</option>
          {datos.categorias.map(c => <option key={c.valor} value={c.valor}>{c.texto}</option>)}
        </select>
        <div className="d-flex align-items-center gap-2 small">
          <span className="text-secondary">Desde</span>
          <input type="date" className="form-control form-control-sm" value={params.get('desde') || ''} onChange={e => filtro('desde', e.target.value)} />
          <span className="text-secondary">hasta</span>
          <input type="date" className="form-control form-control-sm" value={params.get('hasta') || ''} onChange={e => filtro('hasta', e.target.value)} />
        </div>
        {hayFiltros && <button type="button" className="btn btn-link btn-sm p-0" onClick={() => setParams(consorcio ? { consorcio } : {})}>Limpiar filtros</button>}
      </div>

      {datos.meses.map(m => (
        <div key={m.clave}>
          <div className="history-month">{m.titulo}</div>
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            {m.eventos.map(h => (
              <div className="history-row" key={h.id}>
                <div className={'history-icon ' + h.categoria}><i className={'bi ' + ICONOS[h.categoria]} /></div>
                <div className="flex-grow-1 min-w-0">
                  <div className="history-text">{h.descripcion}</div>
                  <div className="invoice-meta">
                    <span className="chip-soft">{h.categoria_texto}</span>{' '}
                    {!filtrado && `${h.consorcio} · `}{fecha(h.fecha)}{!h.manual && h.hora && ` ${h.hora}`}{h.usuario && ` · ${h.usuario}`}
                  </div>
                </div>
                {h.puede_borrar && (
                  <button type="button" className="btn btn-link btn-sm text-danger p-0" title="Eliminar" disabled={ocupado}
                    onClick={() => accion({ accion: 'borrar', id_historial: h.id }, {}, { confirmar: '¿Eliminar esta entrada del historial?' })}>
                    <i className="bi bi-trash" />
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      ))}
      {!datos.meses.length && <Vacio icono="bi-clock-history" titulo="No hay movimientos" texto={hayFiltros ? 'Probá con otros filtros.' : 'Lo que pase en el edificio se va a ir registrando acá.'} />}

      <Modal titulo="Agregar al historial" abierto={modal} onCerrar={() => setModal(false)} onSubmit={guardar} pie={
        <>
          <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setModal(false)}>Cancelar</button>
          <button type="submit" className="btn btn-primary btn-sm px-3" disabled={ocupado}>Guardar</button>
        </>
      }>
        <label className="form-label small fw-semibold">Edificio</label>
        <select className="form-select mb-3" value={form.id_consorcio || ''} onChange={e => setForm(f => ({ ...f, id_consorcio: e.target.value }))}>
          {datos.gestionables.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
        </select>
        <div className="row g-2 mb-3">
          <div className="col-7">
            <label className="form-label small fw-semibold">Tipo</label>
            <select className="form-select" value={form.categoria || ''} onChange={e => setForm(f => ({ ...f, categoria: e.target.value }))}>
              {datos.manuales.map(c => <option key={c.valor} value={c.valor}>{c.texto}</option>)}
            </select>
          </div>
          <div className="col-5">
            <label className="form-label small fw-semibold">Fecha</label>
            <input type="date" className="form-control" max={datos.hoy} value={form.fecha || ''} onChange={e => setForm(f => ({ ...f, fecha: e.target.value }))} />
          </div>
        </div>
        <label className="form-label small fw-semibold">Qué pasó</label>
        <textarea className="form-control" rows="2" maxLength={255} placeholder="Ej.: Se pintó el hall de entrada"
          value={form.descripcion || ''} onChange={e => setForm(f => ({ ...f, descripcion: e.target.value }))} />
      </Modal>
    </>
  )
}
